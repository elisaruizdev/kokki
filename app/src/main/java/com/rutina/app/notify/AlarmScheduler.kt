package com.rutina.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.rutina.app.data.PlannedActivity
import com.rutina.app.data.Status
import com.rutina.app.data.categoryEnum
import com.rutina.app.data.localDate
import com.rutina.app.data.statusEnum
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Cada actividad pendiente tiene dos alarmas:
 *  - PREP: X minutos antes de empezar ("prepárate").
 *  - CHECK: al terminar, para marcar si se ha cumplido.
 *  - EXPIRE: si 2 horas después sigue sin marcar, cuenta como no hecha (solo si tiene castigos).
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(activity: PlannedActivity) {
        if (activity.statusEnum != Status.PENDIENTE) return
        val dayStart = activity.localDate.atStartOfDay(ZoneId.systemDefault())
        val start = dayStart.plusMinutes(activity.startMinute.toLong()).toInstant().toEpochMilli()
        val end = dayStart.plusMinutes(activity.endMinute.toLong()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val prep = start - activity.reminderMinutes * 60_000L
        if (prep > now) set(prep, alarmIntent(activity.id, TYPE_PREP))
        if (activity.categoryEnum.asksCheck && end > now) set(end, alarmIntent(activity.id, TYPE_CHECK))
        val expire = end + EXPIRE_AFTER_MINUTES * 60_000L
        if (activity.categoryEnum.asksCheck && expire > now) set(expire, alarmIntent(activity.id, TYPE_EXPIRE))
    }

    fun cancel(activityId: Long) {
        for (type in listOf(TYPE_PREP, TYPE_CHECK, TYPE_EXPIRE)) {
            val intent = Intent(context, AlarmReceiver::class.java).setAction(actionFor(type))
            PendingIntent.getBroadcast(
                context, requestCode(activityId, type), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            NotificationManagerCompat.from(context).cancel(notificationId(activityId, type))
        }
    }

    /** Cada domingo a las 20:00 se pregunta qué es lo más difícil que se lleva de la semana. */
    fun scheduleWeeklyReflection() {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(REFLECTION_TIME)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(actionFor(TYPE_REFLECTION))
            .putExtra(EXTRA_TYPE, TYPE_REFLECTION)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REFLECTION_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        set(next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pendingIntent)
    }

    private fun set(triggerAt: Long, pendingIntent: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun alarmIntent(activityId: Long, type: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(actionFor(type))
            .putExtra(EXTRA_ID, activityId)
            .putExtra(EXTRA_TYPE, type)
        return PendingIntent.getBroadcast(
            context, requestCode(activityId, type), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val TYPE_PREP = 0
        const val TYPE_CHECK = 1
        const val TYPE_REFLECTION = 2
        const val TYPE_EXPIRE = 3
        const val EXPIRE_AFTER_MINUTES = 120
        private const val REFLECTION_REQUEST_CODE = Int.MAX_VALUE - 1
        private val REFLECTION_TIME: LocalTime = LocalTime.of(20, 0)
        const val EXTRA_ID = "activity_id"
        const val EXTRA_TYPE = "type"

        private fun actionFor(type: Int) = "com.rutina.app.ALARM_$type"
        private fun requestCode(activityId: Long, type: Int) = (activityId * 2 + type).toInt()
        fun notificationId(activityId: Long, type: Int) = (activityId * 2 + type).toInt()
    }
}
