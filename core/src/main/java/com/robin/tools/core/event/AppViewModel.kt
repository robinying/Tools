package com.robin.tools.core.event

import com.robin.tools.core.base.BaseViewModel

/**
 * 早期设计的 Application 级全局 ViewModel 空壳，当前无任何调用方。
 *
 * 如需全局事件通信，请优先使用 [SharedFlowBus]；如需 Application 级状态，请使用
 * [com.robin.tools.core.base.BaseApp] 的共享 ViewModelStore，勿再继承本空壳类。
 */
@Deprecated(
    "空壳 ViewModel 已废弃，全局事件请使用 SharedFlowBus，Application 级状态请用 BaseApp 共享 ViewModelStore",
    level = DeprecationLevel.WARNING
)
class AppViewModel : BaseViewModel() {



    init {

    }
}