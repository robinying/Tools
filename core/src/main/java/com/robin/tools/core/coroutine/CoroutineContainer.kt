package com.robin.tools.core.coroutine

/**
 * 自定义协程批量管理接口。与 [Coroutine] 同属早期自定义协程体系。
 *
 * 新代码请直接使用 kotlinx.coroutines 的结构化并发：以 [kotlinx.coroutines.CoroutineScope]
 * （如 viewModelScope / lifecycleScope）管理生命周期，用 [kotlinx.coroutines.Job] 集合或
 * [kotlinx.coroutines.SupervisorJob] 替代本接口的批量管理能力，更可读且可调试。
 */
@Deprecated(
    "自定义协程体系已废弃，请使用 kotlinx.coroutines 的结构化并发（CoroutineScope + Job）",
    level = DeprecationLevel.WARNING
)
internal interface CoroutineContainer {

    fun add(coroutine: Coroutine<*>): Boolean

    fun addAll(vararg coroutines: Coroutine<*>): Boolean

    fun remove(coroutine: Coroutine<*>): Boolean

    fun delete(coroutine: Coroutine<*>): Boolean

    fun clear()

}
