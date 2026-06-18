package com.robin.tools.core.base

import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.robin.tools.core.util.inflateBindingWithGeneric
import com.robin.tools.core.event.AppViewModel
import com.robin.tools.core.event.EventViewModel
import com.robin.tools.core.ext.getAppViewModel
import com.robin.tools.core.ext.getVmClazz
import java.lang.reflect.ParameterizedType

/**
 * Legacy ViewBinding 基类。本项目为单 Activity Jetpack Compose 应用，不再使用 ViewBinding，
 * 且无任何 Activity 继承此类。新代码请直接继承 [androidx.activity.ComponentActivity] 并使用
 * Compose 管理 UI 与 loading 状态，勿再继承本类。
 */
@Deprecated(
    "ViewBinding 基类已在 Compose 架构下废弃，新代码请直接使用 ComponentActivity + Compose",
    level = DeprecationLevel.WARNING
)
abstract class BaseActivity<VM : BaseViewModel, VB : ViewBinding> : AppCompatActivity(){

    //Application全局的ViewModel，里面存放了一些账户信息，基本配置信息等
    //val appViewModel: AppViewModel by lazy { getAppViewModel() }

    //Application全局的ViewModel，用于发送全局通知操作
    //val eventViewModel: EventViewModel by lazy { getAppViewModel() }

    lateinit var mViewModel: VM

    lateinit var mActivity: AppCompatActivity

    lateinit var binding: VB


    abstract fun initView(savedInstanceState: Bundle?)

    open fun showLoading(message: String = "请求网络中..."){
        // Override in subclass to show loading UI
    }

    open fun dismissLoading(){
        // Override in subclass to dismiss loading UI
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mActivity = this
        binding = inflateBindingWithGeneric(layoutInflater)
        setContentView(binding.root)
        init(savedInstanceState)
    }

    private fun init(savedInstanceState: Bundle?) {
        mViewModel = createViewModel()
        registerUiChange()
        initView(savedInstanceState)
        createObserver()
    }

    /**
     * 创建viewModel
     */
    private fun createViewModel(): VM {
        val clazz = (this.javaClass.genericSuperclass as ParameterizedType)
            .actualTypeArguments[0] as Class<VM>
        return ViewModelProvider(this)[clazz]
    }
    /**
     * 创建LiveData数据观察者
     */
    open fun createObserver(){}

    /**
     * 注册 UI 事件
     */
    private fun registerUiChange() {
        //显示弹窗
        mViewModel.loadingChange.showDialog.observe(this, Observer {
            showLoading(it)
        })
        //关闭弹窗
        mViewModel.loadingChange.dismissDialog.observe(this, Observer {
            dismissLoading()
        })
    }

    /**
     * 将非该Activity绑定的ViewModel添加 loading回调 防止出现请求时不显示 loading 弹窗bug
     * @param viewModels Array<out BaseViewModel>
     */
    protected fun addLoadingObserve(vararg viewModels: BaseViewModel){
        viewModels.forEach {viewModel ->
            //显示弹窗
            viewModel.loadingChange.showDialog.observe(this, Observer {
                showLoading(it)
            })
            //关闭弹窗
            viewModel.loadingChange.dismissDialog.observe(this, Observer {
                dismissLoading()
            })
        }
    }


    }