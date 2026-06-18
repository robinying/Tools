package com.robin.tools.core.event

import com.robin.tools.core.base.BaseViewModel
import com.robin.tools.core.callback.livedata.event.EventLiveData

/**
 * 作者　: hegaojian
 * 时间　: 2019/5/2
 * 描述　:APP全局的ViewModel，可以在这里发送全局通知替代EventBus，LiveDataBus等
 */

/**
 * 早期设计的 APP 全局通知 ViewModel，当前无任何调用方。
 *
 * 如需全局事件通信，请优先使用 [SharedFlowBus]，勿再继承本类。
 */
@Deprecated(
    "全局通知 ViewModel 已废弃，请使用 SharedFlowBus 替代 EventBus/LiveDataBus 模式",
    level = DeprecationLevel.WARNING
)
class EventViewModel : BaseViewModel() {


    //添加TODO通知
    val todoEvent = EventLiveData<Boolean>()


}
