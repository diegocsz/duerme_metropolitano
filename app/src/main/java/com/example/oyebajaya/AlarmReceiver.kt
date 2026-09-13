package com.example.oyebajaya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP_ALARM = "com.example.duermemetropolitano.STOP_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_ALARM) {
            val stopServiceIntent = Intent(context, AlarmForegroundService::class.java).apply {
                action = AlarmForegroundService.ACTION_STOP
            }
            context.startService(stopServiceIntent)
        }
    }
}
