package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyFinanceNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DailyFinanceReminderManager.showStoredDailyFinanceNotification(context)
    }
}
