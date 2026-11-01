package com.rutina.app

import com.rutina.app.data.Commitments
import com.rutina.app.data.Rewards
import androidx.compose.runtime.mutableStateOf
import android.app.Application
import android.content.Context
import com.rutina.app.data.AppDatabase
import com.rutina.app.data.PenaltyRepository
import com.rutina.app.data.Prefs
import com.rutina.app.data.RoutineRepository
import com.rutina.app.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.rutina.app.notify.AlarmScheduler
import com.rutina.app.notify.Notifier

class RutinaApp : Application() {
    lateinit var repository: RoutineRepository
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var scheduler: AlarmScheduler
        private set
    lateinit var penalties: PenaltyRepository
        private set
    lateinit var rewards: Rewards
        private set

    /** Tema elegido en Ajustes; al cambiarlo se repinta la app al momento. */
    val themeMode by lazy { mutableStateOf(prefs.themeMode) }

    /** Para trabajos que no deben cortarse al cerrar una pantalla (p. ej. la IA corrigiendo un reto). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        scheduler = AlarmScheduler(this)
        val db = AppDatabase.create(this)
        val commitments = Commitments(this)
        penalties = PenaltyRepository(this, db.penaltyDao(), commitments)
        rewards = Rewards(this)
        repository = RoutineRepository(db.activityDao(), db.reflectionDao(), penalties, scheduler, rewards, commitments)
        penalties.onRecoveryResult = { id, done -> repository.setStatus(id, if (done) Status.HECHO else Status.NO_HECHO) }
        Notifier.createChannels(this)
    }
}

fun Context.app(): RutinaApp = applicationContext as RutinaApp
