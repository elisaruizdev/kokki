package com.rutina.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.rutina.app.MainActivity
import com.rutina.app.R
import com.rutina.app.data.AppBlock
import com.rutina.app.data.isEditPass
import com.rutina.app.data.Challenge
import com.rutina.app.data.PlannedActivity
import com.rutina.app.data.challengeText
import com.rutina.app.data.durationText
import com.rutina.app.penalty.BlockedActivity
import com.rutina.app.data.Status
import com.rutina.app.data.categoryEnum
import com.rutina.app.data.minutesToText
import java.time.LocalDate

object Notifier {
    private const val CHANNEL_REMINDERS = "recordatorios"
    private const val CHANNEL_CHECKS = "checks"
    private const val CHANNEL_PENALTIES = "castigos"
    private const val TEST_ID = Int.MAX_VALUE
    private const val REFLECTION_ID = Int.MAX_VALUE - 1
    private const val PINK = 0xFFD06A87.toInt()
    const val EXTRA_OPEN_TAB = "open_tab"
    const val TAB_PROGRESS = "progreso"
    const val TAB_PENALTIES = "castigos"
    private const val BLOCKER_OFF_ID = Int.MAX_VALUE - 2
    private const val BONUS_ID = Int.MAX_VALUE - 4

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Recordatorios", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Aviso antes de cada actividad para que te prepares" },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHECKS, "¿Lo has cumplido?", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Al terminar una actividad, para marcarla como hecha o no" },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PENALTIES, "Castigos", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Apps bloqueadas por no cumplir y desbloqueos" },
        )
    }

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showPrep(context: Context, activity: PlannedActivity, warning: String?) {
        val title = if (activity.reminderMinutes > 0) {
            "En ${activity.reminderMinutes} min: ${activity.title}"
        } else {
            "¡Ahora toca ${activity.title}!"
        }
        val text = buildString {
            append("${minutesToText(activity.startMinute)}–${minutesToText(activity.endMinute)} · ${activity.categoryEnum.label}")
            if (activity.note.isNotBlank()) append("\n${activity.note}")
            if (warning != null) append("\n$warning")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PINK)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        post(context, AlarmScheduler.notificationId(activity.id, AlarmScheduler.TYPE_PREP), notification)
    }

    fun showCheck(context: Context, activity: PlannedActivity, warning: String?) {
        val text = "Era de ${minutesToText(activity.startMinute)} a ${minutesToText(activity.endMinute)}. Márcalo y suma a tu semana" +
            if (warning != null) "\n$warning. Si no contestas en ${AlarmScheduler.EXPIRE_AFTER_MINUTES / 60} h, cuenta como no hecho." else ""
        val notification = NotificationCompat.Builder(context, CHANNEL_CHECKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PINK)
            .setContentTitle("¿Has cumplido «${activity.title}»?")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Hecho", statusIntent(context, activity.id, Status.HECHO))
            .addAction(0, "No hecho", statusIntent(context, activity.id, Status.NO_HECHO))
            .setAutoCancel(true)
            .build()
        post(context, AlarmScheduler.notificationId(activity.id, AlarmScheduler.TYPE_CHECK), notification)
    }

    fun showTest(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PINK)
            .setContentTitle("¡Las notificaciones funcionan!")
            .setContentText("Así te avisaré antes de cada actividad")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        post(context, TEST_ID, notification)
    }

    fun showReflection(context: Context, monday: LocalDate) {
        val replyIntent = PendingIntent.getBroadcast(
            context, REFLECTION_ID,
            Intent(context, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_REFLECTION)
                .putExtra(ActionReceiver.EXTRA_WEEK, monday.toEpochDay()),
            // RemoteInput necesita un PendingIntent mutable para poder añadir el texto escrito
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val remoteInput = RemoteInput.Builder(ActionReceiver.KEY_REFLECTION)
            .setLabel("Lo más difícil ha sido…")
            .build()
        val reply = NotificationCompat.Action.Builder(0, "Responder", replyIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
        val text = "Cuéntamelo y lo tendré en cuenta al planificar tu próxima semana"
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PINK)
            .setContentTitle("¿Qué es lo más difícil que llevas?")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context, TAB_PROGRESS))
            .addAction(reply)
            .setAutoCancel(true)
            .build()
        post(context, REFLECTION_ID, notification)
    }

    /** Sustituye la notificación tras responder (si no, Android la deja "enviando"). */
    fun showReflectionSaved(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PINK)
            .setContentTitle("Guardado")
            .setContentText("Gracias por contármelo. Lo verás en tu progreso.")
            .setTimeoutAfter(4_000)
            .setSilent(true)
            .build()
        post(context, REFLECTION_ID, notification)
    }

    fun showBlocked(context: Context, activity: PlannedActivity, blocks: List<AppBlock>) {
        val text = blocks.groupBy { it.ruleId }.values.joinToString("\n") { group ->
            val first = group.first()
            val minutes = (first.remainingMs / 60_000).toInt().coerceAtLeast(1)
            "${group.joinToString(", ") { it.appLabel }}: ${durationText(minutes)}" +
                if (first.current != Challenge.NINGUNO) ", o antes si consigues ${first.challengeText}" else ""
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PENALTIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PINK)
            .setContentTitle("Castigo por no cumplir «${activity.title}»")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, activity.id.toInt(), BlockedActivity.forActivity(context, activity.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        post(context, blockedNotificationId(activity.id), notification)
    }

    fun showUnlocked(context: Context, block: AppBlock, byTime: Boolean) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PENALTIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PINK)
            .setContentTitle(if (byTime) "Castigo cumplido" else "¡Reto superado!")
            .setContentText(
                if (block.isEditPass) "Ya puedes cambiar o desactivar el castigo de «${block.reason}»."
                else "Ya puedes volver a usar las apps del castigo de «${block.reason}».",
            )
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        post(context, blockedNotificationId(block.activityId), notification)
    }

    /** Hay castigos pero Kokki no tiene permiso para bloquear apps. */
    fun showBlockerOff(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PENALTIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PINK)
            .setContentTitle("Tu castigo no se puede aplicar")
            .setContentText("Activa el bloqueo de apps de Kokki: son 2 pasos y te guío.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context, TAB_PENALTIES))
            .setAutoCancel(true)
            .build()
        post(context, BLOCKER_OFF_ID, notification)
    }

    /** Se ha ganado un comodín. */
    fun showBonus(context: Context, total: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PENALTIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PINK)
            .setContentTitle("¡Has ganado un comodín!")
            .setContentText(
                if (total == 1) "Tienes 1 comodín: úsalo para saltarte un castigo cuando lo necesites."
                else "Tienes $total comodines: cada uno te salta un castigo.",
            )
            .setContentIntent(openAppIntent(context, TAB_PENALTIES))
            .setAutoCancel(true)
            .build()
        post(context, BONUS_ID, notification)
    }

    /** Notificación fija mientras se comprueba con el GPS el reto de ir a un sitio. */
    fun placeTracking(context: Context, text: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_PENALTIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PINK)
            .setContentTitle("Reto del sitio en marcha")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(context, TAB_PENALTIES))
            .build()

    fun updatePlaceTracking(context: Context, text: String) =
        post(context, com.rutina.app.penalty.PlaceService.NOTIFICATION_ID, placeTracking(context, text))

    /** Ids negativos para no chocar con los de las alarmas. */
    private fun blockedNotificationId(activityId: Long) = -activityId.toInt() - 1

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!canNotify(context)) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun openAppIntent(context: Context, tab: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context, tab?.hashCode() ?: 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { tab?.let { putExtra(EXTRA_OPEN_TAB, it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun statusIntent(context: Context, activityId: Long, status: Status): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction("com.rutina.app.STATUS_${status.name}")
            .putExtra(AlarmScheduler.EXTRA_ID, activityId)
            .putExtra(ActionReceiver.EXTRA_STATUS, status.name)
        return PendingIntent.getBroadcast(
            context, (activityId * 4 + status.ordinal).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
