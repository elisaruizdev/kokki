package com.rutina.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rutina.app.app
import androidx.core.app.RemoteInput
import com.rutina.app.data.Status
import com.rutina.app.data.mondayOf
import com.rutina.app.data.statusEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ejecuta trabajo suspendido desde un receiver sin que el sistema lo mate antes de terminar. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1)
        val type = intent.getIntExtra(AlarmScheduler.EXTRA_TYPE, -1)
        if (type == AlarmScheduler.TYPE_REFLECTION) {
            Notifier.showReflection(context, mondayOf(LocalDate.now()))
            context.app().scheduler.scheduleWeeklyReflection()
            return
        }
        runAsync {
            val activity = context.app().repository.get(id) ?: return@runAsync
            if (activity.statusEnum != Status.PENDIENTE) return@runAsync
            if (type == AlarmScheduler.TYPE_EXPIRE) {
                // Sin contestar al check: si había castigos para esto, cuenta como no hecho
                val target = context.app().repository.punishTarget(activity)
                if (context.app().penalties.warningFor(target) != null) {
                    context.app().repository.setStatus(activity.id, Status.NO_HECHO)
                }
                return@runAsync
            }
            val warning = context.app().penalties.warningFor(context.app().repository.punishTarget(activity))
            when (type) {
                AlarmScheduler.TYPE_PREP -> Notifier.showPrep(context, activity, warning)
                AlarmScheduler.TYPE_CHECK -> Notifier.showCheck(context, activity, warning)
            }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFLECTION) {
            val answer = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REFLECTION)?.toString()
            val monday = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_WEEK, mondayOf(LocalDate.now()).toEpochDay()))
            runAsync {
                if (!answer.isNullOrBlank()) context.app().repository.saveReflection(monday, answer)
                Notifier.showReflectionSaved(context)
            }
            return
        }
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1)
        val status = Status.from(intent.getStringExtra(EXTRA_STATUS))
        runAsync { context.app().repository.setStatus(id, status) }
    }

    companion object {
        const val EXTRA_STATUS = "status"
        const val EXTRA_WEEK = "week"
        const val KEY_REFLECTION = "reflection"
        const val ACTION_REFLECTION = "com.rutina.app.REFLECTION_REPLY"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync { context.app().repository.rescheduleAll() }
    }
}
