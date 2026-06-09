package com.robin.tools.core.widget.dialog

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.robin.tools.core.ext.util.logd

class DialogChain private constructor(
    // 弹窗的时候可能需要Activity/Fragment环境。
    val activity: FragmentActivity? = null,
    val fragment: Fragment? = null,
    private var interceptors: MutableList<DialogInterceptor>?
) {
    companion object {
        var isOpenLog = false
        @JvmStatic
        fun create(initialCapacity: Int = 0): Builder {
            return Builder(initialCapacity)
        }

        @JvmStatic
        fun openLog(isOpen: Boolean) {
            isOpenLog = isOpen
        }
    }

    private var index: Int = 0

    // 执行拦截器。
    fun process() {
        // 先捕获当前拦截器列表和索引的本地快照，防止递归调用导致的竞态
        val currentInterceptors = interceptors ?: return
        val currentIndex = index

        when {
            currentIndex in currentInterceptors.indices -> {
                index++
                currentInterceptors[currentIndex].intercept(this)
            }
            // 最后一个弹窗关闭的时候，我们希望释放所有弹窗引用。
            currentIndex >= currentInterceptors.size -> {
                "===> clearAllInterceptors".logd("DialogChain")
                clearAllInterceptors()
            }
        }
    }

    private fun clearAllInterceptors() {
        interceptors?.clear()
        interceptors = null
    }

    // 构建者模式。
    open class Builder(private val initialCapacity: Int = 0) {
        private val interceptors by lazy(LazyThreadSafetyMode.NONE) {
            ArrayList<DialogInterceptor>(
                initialCapacity
            )
        }
        private var activity: FragmentActivity? = null
        private var fragment: Fragment? = null

        // 添加一个拦截器。
        fun addInterceptor(interceptor: DialogInterceptor): Builder {
            if (!interceptors.contains(interceptor)) {
                interceptors.add(interceptor)
            }
            return this
        }

        // 关联Fragment。
        fun attach(fragment: Fragment): Builder {
            this.fragment = fragment
            return this
        }

        // 关联Activity。
        fun attach(activity: FragmentActivity): Builder {
            this.activity = activity
            return this
        }


        fun build(): DialogChain {
            return DialogChain(activity, fragment, interceptors)
        }
    }
}