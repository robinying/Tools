package com.robin.tools.core.event

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap

object SharedFlowBus {

    private val events = ConcurrentHashMap<Any, MutableSharedFlow<Any>>()
    private val stickyEvents = ConcurrentHashMap<Any, MutableSharedFlow<Any>>()

    fun <T> with(objectKey: Class<T>): MutableSharedFlow<T> {
        if (!events.containsKey(objectKey)) {
            events[objectKey] = MutableSharedFlow(0, Int.MAX_VALUE, BufferOverflow.SUSPEND)
        }
        @Suppress("UNCHECKED_CAST")
        return events[objectKey] as MutableSharedFlow<T>
    }

    fun <T> withSticky(objectKey: Class<T>): MutableSharedFlow<T> {
        if (!stickyEvents.containsKey(objectKey)) {
            stickyEvents[objectKey] = MutableSharedFlow(1, Int.MAX_VALUE, BufferOverflow.SUSPEND)
        }
        @Suppress("UNCHECKED_CAST")
        return stickyEvents[objectKey] as MutableSharedFlow<T>
    }

    fun <T> on(objectKey: Class<T>): LiveData<T> {
        return with(objectKey).asLiveData()
    }

    fun <T> onSticky(objectKey: Class<T>): LiveData<T> {
        return withSticky(objectKey).asLiveData()
    }

    /**
     * 注销指定事件 key 对应的 SharedFlow，释放其在总线中的引用。
     *
     * 注意：仅移除总线内部持有的 [MutableSharedFlow] 引用，不会中断已通过 [with]/[withSticky]
     * 拿到该 Flow 并正在 collect 的订阅者；且 [withSticky] 的 sticky 缓存值会随之丢失。
     * 应在确认该事件已无人订阅时调用，避免长期运行中无界增长导致的内存泄漏。
     */
    fun <T> unregister(objectKey: Class<T>) {
        events.remove(objectKey)
        stickyEvents.remove(objectKey)
    }

}