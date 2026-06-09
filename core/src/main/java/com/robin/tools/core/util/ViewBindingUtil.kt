package com.robin.tools.core.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.concurrent.ConcurrentHashMap


@JvmName("inflateWithGeneric")
fun <VB : ViewBinding> Any.inflateBindingWithGeneric(layoutInflater: LayoutInflater): VB =
    withGenericBindingClass<VB>(this) { clazz ->
        getInflateMethod(clazz).invoke(null, layoutInflater) as VB
    }.also { binding ->
        if (this is ComponentActivity && binding is ViewDataBinding) {
            binding.lifecycleOwner = this
        }
    }

@JvmName("inflateWithGeneric")
fun <VB : ViewBinding> Any.inflateBindingWithGeneric(layoutInflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean): VB =
    withGenericBindingClass<VB>(this) { clazz ->
        getInflateMethodWithParent(clazz).invoke(null, layoutInflater, parent, attachToParent) as VB
    }.also { binding ->
        if (this is Fragment && binding is ViewDataBinding) {
            binding.lifecycleOwner = viewLifecycleOwner
        }
    }

@JvmName("inflateWithGeneric")
fun <VB : ViewBinding> Any.inflateBindingWithGeneric(parent: ViewGroup): VB =
    inflateBindingWithGeneric(LayoutInflater.from(parent.context), parent, false)

fun <VB : ViewBinding> Any.bindViewWithGeneric(view: View): VB =
    withGenericBindingClass<VB>(this) { clazz ->
        getBindMethod(clazz).invoke(null, view) as VB
    }.also { binding ->
        if (this is Fragment && binding is ViewDataBinding) {
            binding.lifecycleOwner = viewLifecycleOwner
        }
    }

private val inflateMethodCache = ConcurrentHashMap<Class<*>, Method>()
private val inflateMethodWithParentCache = ConcurrentHashMap<Class<*>, Method>()
private val bindMethodCache = ConcurrentHashMap<Class<*>, Method>()
private val parameterizedTypeCache = ConcurrentHashMap<Class<*>, List<ParameterizedType>>()

private fun getInflateMethod(clazz: Class<*>): Method =
    inflateMethodCache.getOrPut(clazz) {
        clazz.getMethod("inflate", LayoutInflater::class.java)
    }

private fun getInflateMethodWithParent(clazz: Class<*>): Method =
    inflateMethodWithParentCache.getOrPut(clazz) {
        clazz.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.java)
    }

private fun getBindMethod(clazz: Class<*>): Method =
    bindMethodCache.getOrPut(clazz) {
        clazz.getMethod("bind", View::class.java)
    }

private fun <VB : ViewBinding> withGenericBindingClass(any: Any, block: (Class<VB>) -> VB): VB {
    any.allParameterizedType.forEach { parameterizedType ->
        parameterizedType.actualTypeArguments.forEach {
            try {
                @Suppress("UNCHECKED_CAST")
                return block.invoke(it as Class<VB>)
            } catch (_: ClassCastException) {
            }
        }
    }
    throw IllegalArgumentException("There is no generic of ViewBinding.")
}

private val Any.allParameterizedType: List<ParameterizedType>
    get() = parameterizedTypeCache.getOrPut(javaClass) {
        val genericParameterizedType = mutableListOf<ParameterizedType>()
        var genericSuperclass = javaClass.genericSuperclass
        var superclass = javaClass.superclass
        while (superclass != null) {
            if (genericSuperclass is ParameterizedType) {
                genericParameterizedType.add(genericSuperclass)
            }
            genericSuperclass = superclass.genericSuperclass
            superclass = superclass.superclass
        }
        genericParameterizedType
    }