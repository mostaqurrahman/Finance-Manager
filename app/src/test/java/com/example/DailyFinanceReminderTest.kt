package com.example

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.util.DailyFinanceReminderManager
import com.example.util.FinanceNotificationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyFinanceReminderTest {

    @Test
    fun `calculateNextTriggerMillis picks same day when target time is in future`() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 14, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val triggerMillis = DailyFinanceReminderManager.calculateNextTriggerMillis(
            hour = 21,
            minute = 0,
            nowMillis = nowCal.timeInMillis
        )
        val triggerCal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
        assertEquals(2026, triggerCal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, triggerCal.get(Calendar.MONTH))
        assertEquals(14, triggerCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(21, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
    }

    @Test
    fun `calculateNextTriggerMillis rolls over to next day when target time already passed`() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 14, 22, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val triggerMillis = DailyFinanceReminderManager.calculateNextTriggerMillis(
            hour = 21,
            minute = 0,
            nowMillis = nowCal.timeInMillis
        )
        val triggerCal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
        assertEquals(15, triggerCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(21, triggerCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `buildNotificationContent formats bilingual daily digest accurately`() {
        val bnEmpty = DailyFinanceReminderManager.buildNotificationContent(
            FinanceNotificationSnapshot(
                todayTxCount = 0,
                todayExpense = 0.0,
                availableBalance = 25000.0,
                isBangla = true
            )
        )
        assertTrue(bnEmpty.title.contains("দৈনিক"))
        assertTrue(bnEmpty.body.contains("৳"))

        val enSummary = DailyFinanceReminderManager.buildNotificationContent(
            FinanceNotificationSnapshot(
                todayTxCount = 3,
                todayExpense = 1200.0,
                availableBalance = 18500.0,
                isBangla = false
            )
        )
        assertEquals("Daily Finance Update", enSummary.title)
        assertTrue(enSummary.body.contains("3 entries"))
        assertTrue(enSummary.body.contains("৳1,200"))
        assertTrue(enSummary.body.contains("৳18,500"))
    }

    @Test
    fun `scheduleDailyReminder and cancelDailyReminder update config and AlarmManager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduled = DailyFinanceReminderManager.scheduleDailyReminder(context, 20, 30)
        assertTrue(scheduled.enabled)
        assertEquals(20, scheduled.hour)
        assertEquals(30, scheduled.minute)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = Shadows.shadowOf(alarmManager)
        assertTrue(shadowAlarm.scheduledAlarms.isNotEmpty())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            assertNotNull(nm.getNotificationChannel(DailyFinanceReminderManager.CHANNEL_ID))
        }

        val canceled = DailyFinanceReminderManager.cancelDailyReminder(context)
        assertFalse(canceled.enabled)
        assertFalse(DailyFinanceReminderManager.getConfig(context).enabled)
    }
}
