package com.example.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow

val battery = MutableStateFlow(100)

class BatteryChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val batteryPct = intent?.let {
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale
        }
        battery.value = batteryPct ?: 100
    }
}