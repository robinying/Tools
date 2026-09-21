package com.robin.tools.feature.storage.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * 存储基准测试引擎的 Android 实现。
 *
 * 阶段顺序（`CACHE_FLUSH` 与 `SQLITE_INSERT` 按载荷条件启用）：
 *
 * 1. **顺序写入**：先用 `posix_fallocate` 预分配，让测量反映稳态写入而不是块分配开销；
 *    主口径包含 `force(true)` 落盘同步——不加同步的写入测到的是页缓存（内存）速度。
 * 2. **缓存驱逐（可选）**：写入丢弃数据制造脏页回写压力，尽力驱逐页缓存。
 *    公共 API 没有 `posix_fadvise`，这是不 root 前提下唯一能做的近似手段，**不保证生效**。
 * 3. **顺序读取**：连续读两轮，首轮与次轮的差值用于提示缓存参与程度。
 * 4. **随机读取 / 随机写入**：区域内 4 KB 对齐随机偏移，位置读写，逐次记录延迟。
 * 5. **小文件**：逐文件「创建→写→`fdatasync`→关闭」，反映相册批量存图这类真实场景。
 * 6. **SQLite 插入（可选）**：真实 SQLite 数据库，单事务批量插入。
 *
 * 所有测试产物集中在工作目录内，退出路径（正常、失败、取消）都会清理。
 */
class StorageBenchmarkEngine(
    private val targetResolver: StorageTargetResolver,
    private val deviceSnapshotProvider: DeviceSnapshotProvider
) : BenchmarkEngine {

    override suspend fun run(
        spec: BenchmarkSpec,
        onProgress: (EngineProgress) -> Unit
    ): BenchmarkResult {
        val workspace = targetResolver.requireWorkspace(spec.target)
        val startedAtEpochMillis = System.currentTimeMillis()
        val startedNanos = System.nanoTime()

        try {
            // 上一次运行可能因进程被杀而留下残留文件，先清干净再开始。
            StorageSafetyGuard.purgeTestArtifacts(workspace)

            val availableBytes = targetResolver.allocatableBytes(workspace)
            StorageSafetyGuard.verifyCapacity(StorageSafetyGuard.peakPayloadBytes(spec), availableBytes)

            val device = deviceSnapshotProvider.snapshot(
                dir = workspace,
                volumeTotalBytes = targetResolver.totalBytes(workspace),
                freeBytesAtStart = availableBytes
            )
            val plan = PhasePlan(spec.phasePlan)

            val sequentialWrite = measureSequentialWrite(workspace, spec.sequentialBytes, plan, startedNanos, onProgress)
            val coldReadAttempted = spec.coldRead && spec.cacheFlushBytes > 0L
            if (coldReadAttempted) {
                measureCacheFlush(workspace, spec.cacheFlushBytes, plan, startedNanos, onProgress)
            }
            val (firstPass, repeatPass) = measureSequentialRead(workspace, spec.sequentialBytes, plan, startedNanos, onProgress)
            val randomRead = measureRandomIo(workspace, spec, isWrite = false, plan = plan, runStartedNanos = startedNanos, onProgress = onProgress)
            val randomWrite = measureRandomIo(workspace, spec, isWrite = true, plan = plan, runStartedNanos = startedNanos, onProgress = onProgress)
            val smallFile = measureSmallFiles(workspace, spec, plan, startedNanos, onProgress)
            val sqliteInsert = if (spec.sqliteRows > 0) {
                measureSqliteInsert(workspace, spec.sqliteRows, plan, startedNanos, onProgress)
            } else {
                null
            }

            val totalElapsedMillis = (System.nanoTime() - startedNanos) / NANOS_PER_MILLI
            Log.i(
                TAG,
                "benchmark finished: target=${spec.target} phases=${plan.count} " +
                    "write=${sequentialWrite.averageBytesPerSecond.toLong()}B/s " +
                    "read=${firstPass.averageBytesPerSecond.toLong()}B/s " +
                    "randomRead=${randomRead.operationsPerSecond.toLong()}iops " +
                    "randomWrite=${randomWrite.operationsPerSecond.toLong()}iops " +
                    "smallFiles=${smallFile.operationsPerSecond.toLong()}/s " +
                    "elapsed=${totalElapsedMillis}ms"
            )

            return BenchmarkResult(
                target = spec.target,
                preset = spec.preset,
                sequentialWrite = sequentialWrite,
                sequentialReadFirstPass = firstPass,
                sequentialReadRepeat = repeatPass,
                randomRead = randomRead,
                randomWrite = randomWrite,
                smallFile = smallFile,
                sqliteInsert = sqliteInsert,
                coldReadAttempted = coldReadAttempted,
                device = device,
                startedAtEpochMillis = startedAtEpochMillis,
                totalElapsedMillis = totalElapsedMillis
            )
        } finally {
            // 取消也必须清理：用 NonCancellable 保证清理代码不随协程一起被取消。
            withContext(NonCancellable) {
                StorageSafetyGuard.purgeTestArtifacts(workspace)
            }
        }
    }

    /**
     * 顺序写入测量。
     *
     * 主口径的耗时包含末尾的 `force(true)` 落盘同步，[ThroughputMetric.bufferedBytesPerSecond]
     * 则保留不含同步的对照值。
     */
    private suspend fun measureSequentialWrite(
        workspace: File,
        totalBytes: Long,
        plan: PhasePlan,
        runStartedNanos: Long,
        onProgress: (EngineProgress) -> Unit
    ): ThroughputMetric {
        val phase = BenchmarkPhase.SEQUENTIAL_WRITE
        val file = File(workspace, SEQUENTIAL_FILE_NAME)
        val randomAccessFile = RandomAccessFile(file, "rw")
        try {
            preallocate(randomAccessFile, totalBytes)

            val channel = randomAccessFile.channel
            val buffer = BenchmarkBuffers.directBuffer(SEQUENTIAL_CHUNK_BYTES)

            // 预热：把开头一段先写一遍，排除 JIT 与页缓存建立的开销。
            // 计时阶段会从位置 0 重写整段，因此预热不改变最终文件内容。
            warmUp(channel, buffer, minOf(totalBytes, WARMUP_BYTES))
            channel.position(0L)

            val recorder = ThroughputTimelineRecorder()
            val phaseStartedNanos = System.nanoTime()
            var written = 0L
            var bytesSinceHeadroomCheck = 0L

            while (written < totalBytes) {
                currentCoroutineContext().ensureActive()
                val chunk = minOf(SEQUENTIAL_CHUNK_BYTES.toLong(), totalBytes - written)
                buffer.clear()
                buffer.limit(chunk.toInt())
                written += writeFully(channel, buffer)

                val nowNanos = System.nanoTime()
                recorder.record(written, nowNanos)
                report(
                    plan = plan,
                    phase = phase,
                    phaseFraction = written.toFloat() / totalBytes,
                    liveRatePerSecond = recorder.currentBytesPerSecond(written, nowNanos),
                    runStartedNanos = runStartedNanos,
                    onProgress = onProgress
                )

                bytesSinceHeadroomCheck += chunk
                if (bytesSinceHeadroomCheck >= StorageSafetyGuard.HEADROOM_CHECK_INTERVAL_BYTES) {
                    StorageSafetyGuard.verifyRuntimeHeadroom(targetResolver.availableBytes(workspace))
                    bytesSinceHeadroomCheck = 0L
                }
            }

            val bufferedNanos = System.nanoTime() - phaseStartedNanos
            // 关键：把脏页真正刷到介质。缺少这一步，测到的是内存带宽。
            channel.force(true)
            val elapsedNanos = System.nanoTime() - phaseStartedNanos

            return toMetric(
                phase = phase,
                raw = RawMeasurement(written, bufferedNanos, elapsedNanos, recorder.snapshot())
            )
        } finally {
            runCatching { randomAccessFile.close() }
        }
    }

    /**
     * 尽力而为的缓存驱逐。
     *
     * 向丢弃文件写入伪随机数据：大量脏页回写会在内核侧触发对干净页缓存的回收，
     * 从而部分驱逐顺序文件在页缓存中的副本。这是启发式手段，**不保证生效**，
     * 因此结果中只记录"是否尝试过"，不声称读取数字已经变冷。
     */
    private suspend fun measureCacheFlush(
        workspace: File,
        bytes: Long,
        plan: PhasePlan,
        runStartedNanos: Long,
        onProgress: (EngineProgress) -> Unit
    ) {
        val phase = BenchmarkPhase.CACHE_FLUSH
        val file = File(workspace, CACHE_FLUSH_FILE_NAME)
        val randomAccessFile = RandomAccessFile(file, "rw")
        try {
            val channel = randomAccessFile.channel
            val buffer = BenchmarkBuffers.directBuffer(SEQUENTIAL_CHUNK_BYTES)
            BenchmarkBuffers.fillWithPseudoRandom(buffer)

            var written = 0L
            val phaseStartedNanos = System.nanoTime()
            while (written < bytes) {
                currentCoroutineContext().ensureActive()
                val chunk = minOf(SEQUENTIAL_CHUNK_BYTES.toLong(), bytes - written)
                buffer.clear()
                buffer.limit(chunk.toInt())
                written += writeFully(channel, buffer)

                val nowNanos = System.nanoTime()
                report(
                    plan = plan,
                    phase = phase,
                    phaseFraction = written.toFloat() / bytes,
                    liveRatePerSecond = BenchmarkMath.bytesPerSecond(written, nowNanos - phaseStartedNanos),
                    runStartedNanos = runStartedNanos,
                    onProgress = onProgress
                )
            }
            channel.force(true)
        } finally {
            runCatching { randomAccessFile.close() }
            // 丢弃数据用完即删，不占用后续阶段的空间。
            runCatching { file.delete() }
        }
    }

    /** 顺序读取：连读两轮，返回（首轮，次轮）。 */
    private suspend fun measureSequentialRead(
        workspace: File,
        totalBytes: Long,
        plan: PhasePlan,
        runStartedNanos: Long,
        onProgress: (EngineProgress) -> Unit
    ): Pair<ThroughputMetric, ThroughputMetric> {
        val phase = BenchmarkPhase.SEQUENTIAL_READ
        val file = File(workspace, SEQUENTIAL_FILE_NAME)
        val totalReadBytes = totalBytes * READ_PASS_COUNT
        var completedBytes = 0L

        fun reportPass(done: Long, rate: Double) = report(
            plan = plan,
            phase = phase,
            phaseFraction = (completedBytes + done).toFloat() / totalReadBytes,
            liveRatePerSecond = rate,
            runStartedNanos = runStartedNanos,
            onProgress = onProgress
        )

        val firstPass = readPass(file, totalBytes) { done, rate -> reportPass(done, rate) }
        completedBytes += totalBytes
        val repeatPass = readPass(file, totalBytes) { done, rate -> reportPass(done, rate) }

        return toMetric(phase, firstPass) to toMetric(phase, repeatPass)
    }

    /**
     * 单轮顺序读取。
     *
     * 这里不需要额外制造"消费者"来防止读被优化掉：`FileChannel.read` 是带可观测
     * 副作用的本地调用，编译器无法消除它。
     */
    private suspend fun readPass(
        file: File,
        totalBytes: Long,
        onChunk: (transferredBytes: Long, bytesPerSecond: Double) -> Unit
    ): RawMeasurement {
        val stream = FileInputStream(file)
        try {
            val channel = stream.channel
            val buffer = BenchmarkBuffers.directBuffer(SEQUENTIAL_CHUNK_BYTES)
            val recorder = ThroughputTimelineRecorder()
            val phaseStartedNanos = System.nanoTime()

            var readBytes = 0L
            while (readBytes < totalBytes) {
                currentCoroutineContext().ensureActive()
                buffer.clear()
                buffer.limit(minOf(SEQUENTIAL_CHUNK_BYTES.toLong(), totalBytes - readBytes).toInt())
                val readNow = channel.read(buffer)
                if (readNow <= 0) {
                    break
                }
                readBytes += readNow

                val nowNanos = System.nanoTime()
                recorder.record(readBytes, nowNanos)
                onChunk(readBytes, recorder.currentBytesPerSecond(readBytes, nowNanos))
            }

            val elapsedNanos = System.nanoTime() - phaseStartedNanos
            if (readBytes < totalBytes) {
                Log.w(TAG, "short read on ${file.name}: $readBytes of $totalBytes bytes")
            }

            return RawMeasurement(readBytes, null, elapsedNanos, recorder.snapshot())
        } finally {
            runCatching { stream.close() }
        }
    }

    /**
     * 随机读/写测量。
     *
     * 几点必须注意的实现约束：
     *
     * - **偏移一次性预生成**，不放在计时区间内，否则随机数生成的开销会混进延迟统计。
     * - **同步放在计时区间之外**：若把 `force(false)` 算进某一次操作的延迟，那个样本会被
     *   整体抬高，百分位就不再代表单次 IO。同步仍然计入总耗时，所以 IOPS 是带同步的真实值。
     * - 读取阶段会先把区域填满伪随机数据：只靠 `posix_fallocate` 预留的块虽然已分配，
     *   但在降级为 `setLength`（稀疏文件）的设备上读到的只是零页，会让随机读虚高。
     */
    private suspend fun measureRandomIo(
        workspace: File,
        spec: BenchmarkSpec,
        isWrite: Boolean,
        plan: PhasePlan,
        runStartedNanos: Long,
        onProgress: (EngineProgress) -> Unit
    ): RandomIoMetric {
        val phase = if (isWrite) BenchmarkPhase.RANDOM_WRITE else BenchmarkPhase.RANDOM_READ
        val file = File(workspace, RANDOM_FILE_NAME)
        val randomAccessFile = RandomAccessFile(file, "rw")
        try {
            preallocate(randomAccessFile, spec.randomRegionBytes)
            val channel = randomAccessFile.channel

            if (!isWrite) {
                fillRegion(channel, spec.randomRegionBytes, plan, phase, runStartedNanos, onProgress)
            }

            val buffer = BenchmarkBuffers.directBuffer(spec.randomBlockBytes)
            if (isWrite) {
                // 只在计时开始前填充一次：计时区间内保持缓冲区内容不变，避免把
                // 伪随机生成的开销算进写入延迟。
                BenchmarkBuffers.fillWithPseudoRandom(buffer)
            }

            val offsets = randomOffsets(spec.randomRegionBytes, spec.randomBlockBytes, spec.randomOps)
            val syncEveryOps = if (isWrite) RANDOM_WRITE_SYNC_EVERY_OPS else NO_SYNC
            val recorder = LatencyRecorder(spec.randomOps)

            val phaseStartedNanos = System.nanoTime()
            for (index in 0 until spec.randomOps) {
                currentCoroutineContext().ensureActive()
                buffer.clear()
                val operationStartedNanos = System.nanoTime()
                if (isWrite) {
                    writeFullyAt(channel, buffer, offsets[index])
                } else {
                    readFullyAt(channel, buffer, offsets[index])
                }
                recorder.record(System.nanoTime() - operationStartedNanos)

                if (syncEveryOps > NO_SYNC && (index + 1) % syncEveryOps == 0) {
                    channel.force(false)
                }

                val elapsedNanos = System.nanoTime() - phaseStartedNanos
                report(
                    plan = plan,
                    phase = phase,
                    phaseFraction = (index + 1).toFloat() / spec.randomOps,
                    liveRatePerSecond = operationsPerSecond(index + 1, elapsedNanos),
                    runStartedNanos = runStartedNanos,
                    onProgress = onProgress
                )
            }
            if (isWrite) {
                channel.force(true)
            }
            val elapsedNanos = System.nanoTime() - phaseStartedNanos

            return buildRandomIoMetric(
                phase = phase,
                spec = spec,
                recorder = recorder,
                elapsedNanos = elapsedNanos,
                syncEveryOps = syncEveryOps
            )
        } finally {
            runCatching { randomAccessFile.close() }
            if (isWrite) {
                // 随机区域用完即删，避免与小文件阶段叠加占用空间。
                runCatching { file.delete() }
            }
        }
    }

    /**
     * 小文件阶段：逐文件「创建→写→`fdatasync`→关闭」。
     *
     * 删除放在**计时之外**，且在整段测量结束后统一执行：把删除放进循环会让总耗时包含
     * 未计入单次延迟的操作，`operationsPerSecond` 与百分位就来自不同的时间口径。
     */
    private suspend fun measureSmallFiles(
        workspace: File,
        spec: BenchmarkSpec,
        plan: PhasePlan,
        runStartedNanos: Long,
        onProgress: (EngineProgress) -> Unit
    ): RandomIoMetric {
        val phase = BenchmarkPhase.SMALL_FILE
        val directory = File(workspace, SMALL_FILE_DIR_NAME)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("cannot create small-file directory ${directory.absolutePath}")
        }

        val payload = BenchmarkBuffers.pseudoRandomBytes(spec.smallFileBytes)
        val recorder = LatencyRecorder(spec.smallFileCount)
        val phaseStartedNanos = System.nanoTime()

        try {
            for (index in 0 until spec.smallFileCount) {
                currentCoroutineContext().ensureActive()
                val operationStartedNanos = System.nanoTime()
                FileOutputStream(File(directory, "f$index.dat")).use { stream ->
                    stream.write(payload)
                    // 逐文件落盘：测的正是"批量存图"这类场景的数据 + 元数据双落盘成本。
                    Os.fdatasync(stream.fd)
                }
                recorder.record(System.nanoTime() - operationStartedNanos)

                val elapsedNanos = System.nanoTime() - phaseStartedNanos
                report(
                    plan = plan,
                    phase = phase,
                    phaseFraction = (index + 1).toFloat() / spec.smallFileCount,
                    liveRatePerSecond = operationsPerSecond(index + 1, elapsedNanos),
                    runStartedNanos = runStartedNanos,
                    onProgress = onProgress
                )
            }
            val elapsedNanos = System.nanoTime() - phaseStartedNanos

            return buildRandomIoMetric(
                phase = phase,
                spec = spec,
                recorder = recorder,
                elapsedNanos = elapsedNanos,
                syncEveryOps = EVERY_OPERATION_SYNCED,
                blockBytes = spec.smallFileBytes
            )
        } finally {
            runCatching { directory.deleteRecursively() }
        }
    }

    /**
     * SQLite 阶段：真实数据库、单事务批量插入。
     *
     * 用平台自带的 [SQLiteDatabase] 而不是 Room：这里被测量的是 SQLite 的写入路径本身，
     * 直接开库、建表、预编译语句就足够，不必为此提前把 Room 拉进本模块
     * （Room 只在 M3 的历史持久化里才需要）。
     */
    private suspend fun measureSqliteInsert(
        workspace: File,
        rows: Int,
        plan: PhasePlan,
        runStartedNanos: Long,
        onProgress: (EngineProgress) -> Unit
    ): OperationRateMetric {
        val phase = BenchmarkPhase.SQLITE_INSERT
        val file = File(workspace, SQLITE_FILE_NAME)
        val database = try {
            SQLiteDatabase.openOrCreateDatabase(file, null)
        } catch (e: SQLiteException) {
            throw BenchmarkDatabaseException("cannot open benchmark database", e)
        }

        try {
            database.enableWriteAheadLogging()
            val payload = "x".repeat(SQLITE_PAYLOAD_BYTES)

            val phaseStartedNanos = System.nanoTime()
            try {
                database.execSQL("CREATE TABLE bench (id INTEGER PRIMARY KEY AUTOINCREMENT, payload TEXT NOT NULL)")
                val statement = database.compileStatement("INSERT INTO bench (payload) VALUES (?)")
                // KTX 的 transaction 块负责 begin/setSuccessful/end 的配对，异常时也能保证 endTransaction。
                database.transaction {
                    for (index in 0 until rows) {
                        currentCoroutineContext().ensureActive()
                        statement.clearBindings()
                        statement.bindString(1, payload)
                        statement.executeInsert()

                        if ((index + 1) % SQLITE_PROGRESS_INTERVAL_ROWS == 0 || index + 1 == rows) {
                            val elapsedNanos = System.nanoTime() - phaseStartedNanos
                            report(
                                plan = plan,
                                phase = phase,
                                phaseFraction = (index + 1).toFloat() / rows,
                                liveRatePerSecond = operationsPerSecond(index + 1, elapsedNanos),
                                runStartedNanos = runStartedNanos,
                                onProgress = onProgress
                            )
                        }
                    }
                }
            } catch (e: SQLiteException) {
                throw BenchmarkDatabaseException("sqlite insert failed", e)
            }
            val elapsedNanos = System.nanoTime() - phaseStartedNanos

            return OperationRateMetric(
                phase = phase,
                operationCount = rows,
                elapsedNanos = elapsedNanos,
                operationsPerSecond = operationsPerSecond(rows, elapsedNanos)
            )
        } finally {
            runCatching { database.close() }
        }
    }

    /** 用 1 MB 分块把随机区域填满伪随机数据，供随机读阶段使用。 */
    private suspend fun fillRegion(
        channel: FileChannel,
        regionBytes: Long,
        plan: PhasePlan,
        phase: BenchmarkPhase,
        runStartedNanos: Long,
        onProgress: (EngineProgress) -> Unit
    ) {
        val buffer = BenchmarkBuffers.directBuffer(SEQUENTIAL_CHUNK_BYTES)
        BenchmarkBuffers.fillWithPseudoRandom(buffer)

        var written = 0L
        val startedNanos = System.nanoTime()
        while (written < regionBytes) {
            currentCoroutineContext().ensureActive()
            val chunk = minOf(SEQUENTIAL_CHUNK_BYTES.toLong(), regionBytes - written)
            buffer.clear()
            buffer.limit(chunk.toInt())
            written += writeFully(channel, buffer)

            // 准备阶段没有独立进度区间，映射到阶段进度的前 [REGION_PREPARE_SHARE]，
            // 保证进度条单调递增而不是停在 0。
            report(
                plan = plan,
                phase = phase,
                phaseFraction = REGION_PREPARE_SHARE * written / regionBytes,
                liveRatePerSecond = BenchmarkMath.bytesPerSecond(written, System.nanoTime() - startedNanos),
                runStartedNanos = runStartedNanos,
                onProgress = onProgress
            )
        }
        channel.force(true)
    }

    /** 生成 [operationCount] 个 4 KB 对齐的随机偏移，范围 `[0, regionBytes - blockBytes]`。 */
    private fun randomOffsets(regionBytes: Long, blockBytes: Int, operationCount: Int): LongArray {
        val spansCount = (regionBytes / blockBytes).coerceAtLeast(1L)
        val random = java.util.SplittableRandom(RANDOM_SEED)
        return LongArray(operationCount) { random.nextLong(spansCount) * blockBytes }
    }

    private fun buildRandomIoMetric(
        phase: BenchmarkPhase,
        spec: BenchmarkSpec,
        recorder: LatencyRecorder,
        elapsedNanos: Long,
        syncEveryOps: Int,
        blockBytes: Int = spec.randomBlockBytes
    ): RandomIoMetric {
        val sorted = recorder.sortedSamples()
        val operationsPerSecond = operationsPerSecond(recorder.sampleCount(), elapsedNanos)
        return RandomIoMetric(
            phase = phase,
            operationCount = recorder.sampleCount(),
            blockBytes = blockBytes,
            elapsedNanos = elapsedNanos,
            operationsPerSecond = operationsPerSecond,
            bytesPerSecond = operationsPerSecond * blockBytes,
            latencyP50Micros = BenchmarkMath.nanosToMicros(BenchmarkMath.percentileNanos(sorted, P50)),
            latencyP95Micros = BenchmarkMath.nanosToMicros(BenchmarkMath.percentileNanos(sorted, P95)),
            latencyP99Micros = BenchmarkMath.nanosToMicros(BenchmarkMath.percentileNanos(sorted, P99)),
            syncEveryOps = syncEveryOps
        )
    }

    private fun operationsPerSecond(operations: Int, elapsedNanos: Long): Double {
        if (operations <= 0 || elapsedNanos <= 0L) {
            return 0.0
        }
        return operations.toDouble() * NANOS_PER_SECOND / elapsedNanos
    }

    /**
     * 预分配目标文件。
     *
     * 先尝试 `posix_fallocate`，文件系统不支持时降级为 `setLength`。
     * 两者都失败不影响正确性，只是块分配开销会计入写入耗时，因此只记录不抛出。
     */
    private fun preallocate(file: RandomAccessFile, sizeBytes: Long) {
        if (file.length() >= sizeBytes) {
            return
        }
        try {
            Os.posix_fallocate(file.fd, 0L, sizeBytes)
            return
        } catch (e: ErrnoException) {
            Log.w(TAG, "posix_fallocate unavailable, falling back to setLength", e)
        }
        try {
            file.setLength(sizeBytes)
        } catch (e: IOException) {
            Log.w(TAG, "preallocation failed, allocation cost will be counted in the measurement", e)
        }
    }

    /** 预热写：只写开头一段，不计入成绩。 */
    private fun warmUp(channel: FileChannel, buffer: ByteBuffer, bytes: Long) {
        BenchmarkBuffers.fillWithPseudoRandom(buffer)
        var remaining = bytes
        while (remaining > 0L) {
            val chunk = minOf(remaining, buffer.capacity().toLong()).toInt()
            buffer.clear()
            buffer.limit(chunk)
            writeFully(channel, buffer)
            remaining -= chunk
        }
    }

    /**
     * 把缓冲区中剩余的全部字节写入通道。
     *
     * `FileChannel.write` 不保证一次写完，必须循环；返回非正数说明通道状态异常，
     * 直接抛错而不是空转。
     *
     * @return 实际写入的字节数
     */
    private fun writeFully(channel: FileChannel, buffer: ByteBuffer): Int {
        val total = buffer.remaining()
        while (buffer.hasRemaining()) {
            val writtenNow = channel.write(buffer)
            if (writtenNow <= 0) {
                throw IOException("FileChannel.write returned $writtenNow with ${buffer.remaining()} bytes pending")
            }
        }
        return total
    }

    /** 从指定位置写入，循环处理部分写。 */
    private fun writeFullyAt(channel: FileChannel, buffer: ByteBuffer, position: Long) {
        var offset = position
        while (buffer.hasRemaining()) {
            val writtenNow = channel.write(buffer, offset)
            if (writtenNow <= 0) {
                throw IOException("FileChannel.write at $offset returned $writtenNow")
            }
            offset += writtenNow
        }
    }

    /** 从指定位置读取，循环处理部分读。到达文件末尾时提前结束。 */
    private fun readFullyAt(channel: FileChannel, buffer: ByteBuffer, position: Long) {
        var offset = position
        while (buffer.hasRemaining()) {
            val readNow = channel.read(buffer, offset)
            if (readNow <= 0) {
                return
            }
            offset += readNow
        }
    }

    private fun toMetric(phase: BenchmarkPhase, raw: RawMeasurement): ThroughputMetric = ThroughputMetric(
        phase = phase,
        bytes = raw.bytes,
        elapsedNanos = raw.elapsedNanos,
        averageBytesPerSecond = BenchmarkMath.bytesPerSecond(raw.bytes, raw.elapsedNanos),
        steadyStateBytesPerSecond = BenchmarkMath.steadyStateBytesPerSecond(raw.timeline),
        bufferedBytesPerSecond = raw.bufferedNanos
            ?.takeIf { it > 0L }
            ?.let { BenchmarkMath.bytesPerSecond(raw.bytes, it) },
        timeline = raw.timeline
    )

    private fun report(
        plan: PhasePlan,
        phase: BenchmarkPhase,
        phaseFraction: Float,
        liveRatePerSecond: Double,
        runStartedNanos: Long,
        onProgress: (EngineProgress) -> Unit
    ) {
        val safeFraction = phaseFraction.coerceIn(0f, 1f)
        val phaseIndex = plan.indexOf(phase)
        onProgress(
            EngineProgress(
                phase = phase,
                phaseIndex = phaseIndex,
                phaseCount = plan.count,
                phaseFraction = safeFraction,
                overallFraction = ((phaseIndex + safeFraction) / plan.count).coerceIn(0f, 1f),
                liveRatePerSecond = liveRatePerSecond,
                elapsedMillis = (System.nanoTime() - runStartedNanos) / NANOS_PER_MILLI
            )
        )
    }

    /** 本次运行的阶段计划，用于把阶段内进度换算成整体进度。 */
    private class PhasePlan(val phases: List<BenchmarkPhase>) {

        val count: Int = phases.size

        private val indices: Map<BenchmarkPhase, Int> =
            phases.withIndex().associate { (index, phase) -> phase to index }

        /** 不在计划中的阶段返回 0，避免除零或越界。 */
        fun indexOf(phase: BenchmarkPhase): Int = indices[phase] ?: 0
    }

    /** 单次测量的原始输出，尚未换算为指标。 */
    private class RawMeasurement(
        val bytes: Long,
        val bufferedNanos: Long?,
        val elapsedNanos: Long,
        val timeline: List<ThroughputSample>
    )

    private companion object {
        const val TAG = "StorageBenchmarkEngine"

        /** 顺序读写分块大小：与常见的页缓存回写粒度匹配。 */
        const val SEQUENTIAL_CHUNK_BYTES = 1024 * 1024

        /** 预热字节数。 */
        const val WARMUP_BYTES = 4L * MB

        /** 顺序读取轮数：首轮用于出数，次轮用于提示页缓存参与程度。 */
        const val READ_PASS_COUNT = 2

        /** 随机写的同步策略：每 64 次操作 fdatasync 一次。逐次同步会让 IOPS 退化为延迟的倒数。 */
        const val RANDOM_WRITE_SYNC_EVERY_OPS = 64

        /** 表示"本阶段不做批量同步"：随机读不需要，小文件则每个文件单独同步。 */
        const val NO_SYNC = 0

        /** 小文件阶段每个文件都单独 fdatasync。 */
        const val EVERY_OPERATION_SYNCED = 1

        /** 随机区域填充占阶段进度的比例，其余留给计时后的随机读。 */
        const val REGION_PREPARE_SHARE = 0.2f

        /** SQLite 行载荷长度。 */
        const val SQLITE_PAYLOAD_BYTES = 128

        /** SQLite 阶段的进度上报间隔，避免每插一行都发一次进度。 */
        const val SQLITE_PROGRESS_INTERVAL_ROWS = 25

        const val SEQUENTIAL_FILE_NAME = "seq.dat"
        const val RANDOM_FILE_NAME = "random.dat"
        const val CACHE_FLUSH_FILE_NAME = "flush.dat"
        const val SMALL_FILE_DIR_NAME = "small"
        const val SQLITE_FILE_NAME = "bench.sqlite"

        const val RANDOM_SEED = 0x5EED_1234_ABCD_EF01L
        const val NANOS_PER_MILLI = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0

        const val P50 = 0.50
        const val P95 = 0.95
        const val P99 = 0.99
    }
}
