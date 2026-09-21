package com.robin.tools.feature.storage.data

import android.app.Application
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * 存储速度测试的界面状态机。
 *
 * 职责：持有选择项、把引擎的原始进度映射为界面状态、处理取消与失败归因。
 * 实际的阻塞 IO 由 [BenchmarkEngine] 实现执行，本类只负责调度，
 * 因此可以在 JVM 单元测试中用替身引擎完整覆盖。
 */
class StorageBenchViewModel(
    private val engine: BenchmarkEngine,
    private val targetResolver: StorageTargetResolver,
    private val repository: BenchmarkRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _selectedTarget = MutableStateFlow(BenchmarkTarget.INTERNAL_FILES)
    val selectedTarget: StateFlow<BenchmarkTarget> = _selectedTarget.asStateFlow()

    private val _selectedPreset = MutableStateFlow(BenchmarkPreset.STANDARD)
    val selectedPreset: StateFlow<BenchmarkPreset> = _selectedPreset.asStateFlow()

    /** 当前目标的容量概况；目标不可用时为 null。 */
    private val _targetInfo = MutableStateFlow<StorageTargetInfo?>(null)
    val targetInfo: StateFlow<StorageTargetInfo?> = _targetInfo.asStateFlow()

    /** 是否在读取前尽力驱逐页缓存。默认关闭：它只是启发式手段，且会显著拉长测试时间。 */
    private val _coldRead = MutableStateFlow(false)
    val coldRead: StateFlow<Boolean> = _coldRead.asStateFlow()

    private val _progress = MutableStateFlow<BenchmarkProgress>(BenchmarkProgress.Idle)
    val progress: StateFlow<BenchmarkProgress> = _progress.asStateFlow()

    /**
     * 历史记录，按时间倒序。
     *
     * 用 Eagerly 而不是 WhileSubscribed：查询很小，而且保存新结果后即使用户此刻没在
     * 看列表（比如刚跑完测试还在结果区），状态也已经是最新的。
     */
    val history: StateFlow<List<BenchmarkHistoryEntry>> = repository
        .observeRecent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 最后一次引擎回调的阶段，**不受节流影响**。
     *
     * 失败归因需要精确的阶段，不能依赖可能滞后最多一个节流周期的界面状态。
     */
    private val latestPhase = AtomicReference<BenchmarkPhase?>(null)

    private var runJob: Job? = null
    private var lastEmitNanos = 0L

    init {
        refreshTargetInfo()
    }

    /** 当前是否有测试在跑。 */
    fun isRunning(): Boolean = runJob?.isActive == true

    /** 切换测试目标。测试进行中忽略，避免结果与所选目标不一致。 */
    fun selectTarget(target: BenchmarkTarget) {
        if (isRunning() || _selectedTarget.value == target) {
            return
        }
        _selectedTarget.value = target
        _progress.value = BenchmarkProgress.Idle
        refreshTargetInfo()
    }

    /** 切换测试档位。测试进行中忽略。 */
    fun selectPreset(preset: BenchmarkPreset) {
        if (isRunning() || _selectedPreset.value == preset) {
            return
        }
        _selectedPreset.value = preset
        _progress.value = BenchmarkProgress.Idle
    }

    /** 切换冷读开关。测试进行中忽略。 */
    fun setColdRead(enabled: Boolean) {
        if (isRunning() || _coldRead.value == enabled) {
            return
        }
        _coldRead.value = enabled
        _progress.value = BenchmarkProgress.Idle
    }

    /** 重新探测当前目标的容量概况。 */
    fun refreshTargetInfo() {
        val target = _selectedTarget.value
        viewModelScope.launch {
            val info = withContext(ioDispatcher) { probeTargetInfo(target) }
            // 探测期间用户可能已切走，只在目标仍一致时写回。
            if (_selectedTarget.value == target) {
                _targetInfo.value = info
            }
        }
    }

    /**
     * 开始测试。已有测试在跑时直接返回，重复点击不会启动第二次。
     */
    fun start() {
        if (isRunning()) {
            return
        }
        val spec = _selectedPreset.value.toSpec(_selectedTarget.value, _coldRead.value)
        latestPhase.set(null)
        lastEmitNanos = 0L

        runJob = viewModelScope.launch {
            _progress.value = BenchmarkProgress.Running(
                phase = BenchmarkPhase.SEQUENTIAL_WRITE,
                phaseIndex = 0,
                phaseCount = spec.phasePlan.size,
                phaseFraction = 0f,
                overallFraction = 0f,
                liveRatePerSecond = 0.0,
                elapsedMillis = 0L
            )
            try {
                val result = withContext(ioDispatcher) {
                    engine.run(spec) { engineProgress -> publish(engineProgress) }
                }
                persistResult(result)
                _progress.value = BenchmarkProgress.Finished(result)
            } catch (e: CancellationException) {
                _progress.value = BenchmarkProgress.Cancelled
                throw e
            } catch (e: InsufficientStorageException) {
                Log.w(TAG, "benchmark rejected: not enough space", e)
                _progress.value = BenchmarkProgress.Failed(
                    phase = latestPhase.get(),
                    reason = BenchmarkFailure.NOT_ENOUGH_SPACE,
                    requiredBytes = e.requiredAvailableBytes,
                    availableBytes = e.availableBytes,
                    cause = e
                )
            } catch (e: TargetUnavailableException) {
                Log.w(TAG, "benchmark rejected: target unavailable", e)
                _progress.value = BenchmarkProgress.Failed(
                    phase = latestPhase.get(),
                    reason = BenchmarkFailure.TARGET_UNAVAILABLE,
                    cause = e
                )
            } catch (e: BenchmarkDatabaseException) {
                Log.e(TAG, "benchmark failed in the sqlite phase", e)
                _progress.value = BenchmarkProgress.Failed(
                    phase = latestPhase.get(),
                    reason = BenchmarkFailure.DATABASE_ERROR,
                    cause = e
                )
            } catch (e: IOException) {
                Log.e(TAG, "benchmark failed with IO error", e)
                _progress.value = BenchmarkProgress.Failed(
                    phase = latestPhase.get(),
                    reason = BenchmarkFailure.IO_ERROR,
                    cause = e
                )
            } catch (e: Exception) {
                Log.e(TAG, "benchmark failed", e)
                _progress.value = BenchmarkProgress.Failed(
                    phase = latestPhase.get(),
                    reason = BenchmarkFailure.UNKNOWN,
                    cause = e
                )
            } finally {
                refreshTargetInfo()
            }
        }
    }

    /** 取消正在运行的测试。测试产物由引擎在 NonCancellable 段中清理。 */
    fun cancel() {
        runJob?.cancel()
    }

    /** 清空全部历史记录。 */
    fun clearHistory() {
        viewModelScope.launch {
            runCatching { repository.deleteAll() }
                .onFailure { error -> Log.e(TAG, "failed to clear the benchmark history", error) }
        }
    }

    /**
     * 保存结果到历史。
     *
     * 保存失败**不**把整轮测试判为失败：数据已经测出来并展示给用户了，
     * 只是没能落库。这里记录日志后继续，避免用户因为一次数据库写入失败而白跑一趟。
     *
     * 只捕获 [SQLiteException]，**不能**写成 `RuntimeException`：`CancellationException`
     * 也是它的子类，那样会把取消一并吞掉。
     */
    private suspend fun persistResult(result: BenchmarkResult) {
        try {
            repository.save(result)
        } catch (e: SQLiteException) {
            Log.e(TAG, "failed to persist the benchmark result", e)
        }
    }

    /**
     * 把引擎进度写入界面状态，按时间与进度增量双重条件节流。
     *
     * 引擎的回调粒度是每个分块（最快约 1 MB 一次），直接转发会造成重组风暴，
     * 反过来影响正在测量的 IO。
     */
    private fun publish(engineProgress: EngineProgress) {
        latestPhase.set(engineProgress.phase)

        val nowNanos = System.nanoTime()
        val previous = _progress.value
        val fractionJumped = previous !is BenchmarkProgress.Running ||
            engineProgress.overallFraction - previous.overallFraction >= MIN_EMIT_FRACTION
        val intervalElapsed = nowNanos - lastEmitNanos >= MIN_EMIT_INTERVAL_NANOS
        if (!fractionJumped && !intervalElapsed) {
            return
        }
        lastEmitNanos = nowNanos

        _progress.value = BenchmarkProgress.Running(
            phase = engineProgress.phase,
            phaseIndex = engineProgress.phaseIndex,
            phaseCount = engineProgress.phaseCount,
            phaseFraction = engineProgress.phaseFraction,
            overallFraction = engineProgress.overallFraction,
            liveRatePerSecond = engineProgress.liveRatePerSecond,
            elapsedMillis = engineProgress.elapsedMillis
        )
    }

    private fun probeTargetInfo(target: BenchmarkTarget): StorageTargetInfo? {
        val root = targetResolver.resolveRootDirectory(target) ?: return null
        return StorageTargetInfo(
            target = target,
            availableBytes = targetResolver.availableBytes(root),
            totalBytes = targetResolver.totalBytes(root)
        )
    }

    /** 构造默认依赖的工厂。 */
    class Factory(private val application: Application) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val resolver = StorageTargetResolver(application)
            val engine = StorageBenchmarkEngine(resolver, DeviceSnapshotProvider(application))
            val repository = BenchmarkRepository(
                StorageBenchDatabase.getInstance(application).benchmarkResultDao()
            )
            return StorageBenchViewModel(engine, resolver, repository) as T
        }
    }

    private companion object {
        const val TAG = "StorageBenchViewModel"

        /** 进度节流的最小时间间隔。 */
        const val MIN_EMIT_INTERVAL_NANOS = 100_000_000L

        /** 进度节流的最小增量，保证慢速阶段也有可见推进。 */
        const val MIN_EMIT_FRACTION = 0.01f
    }
}
