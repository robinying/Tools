package com.robin.tools.core.utils

import android.os.CountDownTimer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

object CountDownManager {
    private val mRemainSecond = AtomicLong(0)
    private var mTimer: CountDownTimer? = null
    private val mListener = CopyOnWriteArrayList<CountDataChangeListener>()

    fun startCount(remainSecond: Long = 10L) {
        cancelCount()
        mRemainSecond.set(remainSecond)
        mTimer = object : CountDownTimer(remainSecond * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                mRemainSecond.decrementAndGet()
                dispatchMessage("剩余：${mRemainSecond.get()} 秒")
            }

            override fun onFinish() {
                dispatchMessage("倒计时结束")
            }
        }.also { it.start() }
    }

    fun cancelCount() {
        mTimer?.cancel()
        mTimer = null
        mListener.clear()
    }

    private fun dispatchMessage(msg: String) {
        mListener.forEach { it.onChange(msg) }
    }

    fun setListener(listener: CountDataChangeListener) {
        mListener.add(listener)
    }

    fun removeListener(listener: CountDataChangeListener) {
        mListener.remove(listener)
    }
}

interface CountDataChangeListener {
    fun onChange(msg: String)
}