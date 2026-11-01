package com.example.superznalci

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

object ReminderManager {
    private const val PREFS = "reminder_prefs"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val CHANNEL_ID = "daily_reminder"

    fun savedTime(ctx: Context): Pair<Int, Int>? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.contains(KEY_HOUR)) p.getInt(KEY_HOUR, 0) to p.getInt(KEY_MINUTE, 0) else null
    }

    fun set(ctx: Context, hour: Int, minute: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
        scheduleNext(ctx)
    }

    fun scheduleNext(ctx: Context) {
        val (hour, minute) = savedTime(ctx) ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val alarms = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 4711, Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms())
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        else
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    fun showNotification(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val tap = launch?.let { PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE) }

        nm.notify(
            1,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle(ctx.getString(R.string.reminder_title))
                .setContentText(ctx.getString(R.string.reminder_text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderManager.showNotification(context)
        ReminderManager.scheduleNext(context)
    }
}