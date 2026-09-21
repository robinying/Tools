package com.robin.tools.feature.storage.data

import java.io.IOException

/** 测试目标目录不可用或无法创建。 */
class TargetUnavailableException(message: String) : IOException(message)

/**
 * SQLite 阶段失败。
 *
 * 单独分类是为了让界面能给出与普通 IO 失败不同的提示：
 * 存储写不进去和数据库打不开，对用户来说要采取的行动不一样。
 */
class BenchmarkDatabaseException(message: String, cause: Throwable) : IOException(message, cause)

/**
 * 存储基准测试引擎。
 *
 * 实现会执行阻塞式文件 IO，**必须在非主线程调用**。
 * 接口与实现分离，使 ViewModel 的状态机可以在 JVM 单元测试中用替身完整覆盖。
 */
interface BenchmarkEngine {

    /**
     * 执行一次完整测试。
     *
     * @param spec 显式载荷描述，由档位映射得到；实际执行的阶段见 [BenchmarkSpec.phasePlan]
     * @param onProgress 进度回调，在每个分块或每次操作完成后触发；回调本身必须足够轻量
     * @return 测试结果
     * @throws InsufficientStorageException 可用空间不足
     * @throws TargetUnavailableException 目标目录不可用
     * @throws BenchmarkDatabaseException SQLite 阶段失败
     * @throws IOException 读写失败
     */
    suspend fun run(
        spec: BenchmarkSpec,
        onProgress: (EngineProgress) -> Unit
    ): BenchmarkResult
}
