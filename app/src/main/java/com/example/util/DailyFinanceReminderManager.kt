package com.example.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.util.Calendar
import java.util.Locale

data class DailyReminderConfig(
    val enabled: Boolean = false,
    val hour: Int = DailyFinanceReminderManager.DEFAULT_HOUR,
    val minute: Int = DailyFinanceReminderManager.DEFAULT_MINUTE
)

data class FinanceNotificationSnapshot(
    val todayTxCount: Int = 0,
    val todayExpense: Double = 0.0,
    val availableBalance: Double = 0.0,
    val isBangla: Boolean = true
)

data class DailyNotificationCopy(
    val title: String,
    val body: String
)

object DailyFinanceReminderManager {

    const val CHANNEL_ID = "daily_finance_updates"
    const val DEFAULT_HOUR = 21
    const val DEFAULT_MINUTE = 0
    private const val NOTIFICATION_ID = 4102
    private const val REQUEST_CODE = 4102

    private const val PREFS_NAME = "pfm_daily_reminder_prefs"
    private const val KEY_ENABLED = "daily_reminder_enabled"
    private const val KEY_HOUR = "daily_reminder_hour"
    private const val KEY_MINUTE = "daily_reminder_minute"
    private const val KEY_SNAPSHOT_TX_COUNT = "snapshot_today_tx_count"
    private const val KEY_SNAPSHOT_EXPENSE = "snapshot_today_expense"
    private const val KEY_SNAPSHOT_BALANCE = "snapshot_available_balance"
    private const val KEY_SNAPSHOT_IS_BANGLA = "snapshot_is_bangla"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getConfig(context: Context): DailyReminderConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DailyReminderConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            hour = prefs.getInt(KEY_HOUR, DEFAULT_HOUR).coerceIn(0, 23),
            minute = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE).coerceIn(0, 59)
        )
    }

    fun updateFinanceSnapshot(
        context: Context,
        todayTxCount: Int,
        todayExpense: Double,
        availableBalance: Double,
        isBangla: Boolean
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SNAPSHOT_TX_COUNT, todayTxCount)
            .putString(KEY_SNAPSHOT_EXPENSE, todayExpense.toString())
            .putString(KEY_SNAPSHOT_BALANCE, availableBalance.toString())
            .putBoolean(KEY_SNAPSHOT_IS_BANGLA, isBangla)
            .apply()
    }

    fun getFinanceSnapshot(context: Context): FinanceNotificationSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return FinanceNotificationSnapshot(
            todayTxCount = prefs.getInt(KEY_SNAPSHOT_TX_COUNT, 0),
            todayExpense = prefs.getString(KEY_SNAPSHOT_EXPENSE, null)?.toDoubleOrNull() ?: 0.0,
            availableBalance = prefs.getString(KEY_SNAPSHOT_BALANCE, null)?.toDoubleOrNull() ?: 0.0,
            isBangla = prefs.getBoolean(KEY_SNAPSHOT_IS_BANGLA, true)
        )
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Finance Updates"
            val descriptionText = "Regular daily reminder to review balances and log today's expenses."
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = descriptionText
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun calculateNextTriggerMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun formatReminderTime(hour: Int, minute: Int, isBangla: Boolean): String {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val isPm = safeHour >= 12
        val hour12 = when {
            safeHour == 0 -> 12
            safeHour > 12 -> safeHour - 12
            else -> safeHour
        }
        val period = if (isBangla) {
            if (isPm) "PM" else "AM"
        } else {
            if (isPm) "PM" else "AM"
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, safeMinute, period)
    }

    fun scheduleDailyReminder(
        context: Context,
        hour: Int = DEFAULT_HOUR,
        minute: Int = DEFAULT_MINUTE
    ): DailyReminderConfig {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_HOUR, safeHour)
            .putInt(KEY_MINUTE, safeMinute)
            .apply()

        ensureNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = calculateNextTriggerMillis(safeHour, safeMinute)
        val pendingIntent = buildReceiverPendingIntent(context)

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )

        return DailyReminderConfig(enabled = true, hour = safeHour, minute = safeMinute)
    }

    fun cancelDailyReminder(context: Context): DailyReminderConfig {
        val current = getConfig(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildReceiverPendingIntent(context))
        return current.copy(enabled = false)
    }

    fun rescheduleIfEnabled(context: Context) {
        val config = getConfig(context)
        if (config.enabled && hasNotificationPermission(context)) {
            scheduleDailyReminder(context, config.hour, config.minute)
        }
    }

    fun buildNotificationContent(
        snapshot: FinanceNotificationSnapshot
    ): DailyNotificationCopy {
        val balanceFormatted = FinancialEngine.formatBdt(snapshot.availableBalance)
        val expenseFormatted = FinancialEngine.formatBdt(snapshot.todayExpense)

        return if (snapshot.isBangla) {
            val title = "দৈনিক ফাইন্যান্স আপডেট"
            val body = if (snapshot.todayTxCount == 0) {
                "আজকে এখনো কোনো লেনদেন যুক্ত হয়নি। বর্তমান উপলব্ধ ব্যালেন্স $balanceFormatted — আজকের আয় ও ব্যয় আপডেট করুন।"
            } else {
                "আজকে ${snapshot.todayTxCount}টি লেনদেন | ব্যয়: $expenseFormatted | উপলব্ধ ব্যালেন্স: $balanceFormatted।"
            }
            DailyNotificationCopy(title = title, body = body)
        } else {
            val title = "Daily Finance Update"
            val body = if (snapshot.todayTxCount == 0) {
                "No transactions logged today yet. Available balance: $balanceFormatted. Tap to record today's income or expenses."
            } else {
                "Today: ${snapshot.todayTxCount} entries | Expense: $expenseFormatted | Available balance: $balanceFormatted."
            }
            DailyNotificationCopy(title = title, body = body)
        }
    }

    fun showStoredDailyFinanceNotification(
        context: Context,
        overrideSnapshot: FinanceNotificationSnapshot? = null
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureNotificationChannel(context)

        val snapshot = overrideSnapshot ?: getFinanceSnapshot(context)
        val copy = buildNotificationContent(snapshot)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(copy.title)
            .setContentText(copy.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildReceiverPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyFinanceNotificationReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
