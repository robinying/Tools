package com.robin.tools.core.coroutine

/**
 * 自定义协程批量管理实现。与 [Coroutine] 同属早期自定义协程体系，当前无任何调用方。
 *
 * 新代码请使用 kotlinx.coroutines 的结构化并发（[kotlinx.coroutines.CoroutineScope] +
 * [kotlinx.coroutines.Job] / [kotlinx.coroutines.SupervisorJob]）替代。
 */
@Deprecated(
    "自定义协程体系已废弃，请使用 kotlinx.coroutines 的结构化并发（CoroutineScope + Job）",
    level = DeprecationLevel.WARNING
)
@Suppress("unused")
class CompositeCoroutine : CoroutineContainer {

    private var resources: HashSet<Coroutine<*>>? = null

    val size: Int
        get() = resources?.size ?: 0

    val isEmpty: Boolean
        get() = size == 0

    constructor()

    constructor(vararg coroutines: Coroutine<*>) {
        this.resources = hashSetOf(*coroutines)
    }

    constructor(coroutines: Iterable<Coroutine<*>>) {
        this.resources = hashSetOf()
        for (d in coroutines) {
            this.resources?.add(d)
        }
    }

    override fun add(coroutine: Coroutine<*>): Boolean {
        synchronized(this) {
            var set: HashSet<Coroutine<*>>? = resources
            if (resources == null) {
                set = hashSetOf()
                resources = set
            }
            return set!!.add(coroutine)
        }
    }

    override fun addAll(vararg coroutines: Coroutine<*>): Boolean {
        synchronized(this) {
            var set: HashSet<Coroutine<*>>? = resources
            if (resources == null) {
                set = hashSetOf()
                resources = set
            }
            for (coroutine in coroutines) {
                val add = set!!.add(coroutine)
                if (!add) {
                    return false
                }
            }
        }
        return true
    }

    override fun remove(coroutine: Coroutine<*>): Boolean {
        if (delete(coroutine)) {
            coroutine.cancel()
            return true
        }
        return false
    }

    override fun delete(coroutine: Coroutine<*>): Boolean {
        synchronized(this) {
            val set = resources
            if (set == null || !set.remove(coroutine)) {
                return false
            }
        }
        return true
    }

    override fun clear() {
        val set: HashSet<Coroutine<*>>?
        synchronized(this) {
            set = resources
            resources = null
        }

        set?.forEach { coroutine ->
            coroutine.cancel()
        }
    }
}