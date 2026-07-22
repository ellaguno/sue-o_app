package com.sesolibre.somnia.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Preferencias del usuario (DataStore). Por ahora solo el umbral de apertura
 * del detector de eventos; el valor se lee al INICIAR una sesión, así que
 * cambiarlo a media noche no afecta la sesión en curso.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Margen en dB sobre el ruido de fondo para abrir un evento. */
    val openMarginDb: Flow<Double> = context.settingsDataStore.data
        .map { it[KEY_OPEN_MARGIN_DB] ?: DEFAULT_OPEN_MARGIN_DB }

    suspend fun setOpenMarginDb(value: Double) {
        context.settingsDataStore.edit {
            it[KEY_OPEN_MARGIN_DB] = value.coerceIn(MIN_OPEN_MARGIN_DB, MAX_OPEN_MARGIN_DB)
        }
    }

    /** Transcribir on-device los eventos de habla (opt-in; default apagado). */
    val transcribeSpeech: Flow<Boolean> = context.settingsDataStore.data
        .map { it[KEY_TRANSCRIBE_SPEECH] ?: false }

    suspend fun setTranscribeSpeech(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_TRANSCRIBE_SPEECH] = enabled }
    }

    // --- Horario de sueño (inicio/fin automático por hora) ---

    val scheduleEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { it[KEY_SCHED_ENABLED] ?: false }

    val scheduleAutoStart: Flow<Boolean> = context.settingsDataStore.data
        .map { it[KEY_SCHED_AUTOSTART] ?: false }

    /** Hora de dormir, en minutos desde medianoche (p. ej. 22:30 = 1350). */
    val bedtimeMinutes: Flow<Int> = context.settingsDataStore.data
        .map { it[KEY_BEDTIME_MIN] ?: DEFAULT_BEDTIME_MIN }

    /** Hora de despertar, en minutos desde medianoche (p. ej. 6:30 = 390). */
    val wakeMinutes: Flow<Int> = context.settingsDataStore.data
        .map { it[KEY_WAKE_MIN] ?: DEFAULT_WAKE_MIN }

    /** Horario alterno para ciertos días (p. ej. fin de semana). */
    val scheduleAltEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { it[KEY_SCHED_ALT_ENABLED] ?: false }

    /** Días (ISO: lunes=1…domingo=7) cuya NOCHE usa el horario alterno. */
    val scheduleAltDays: Flow<Set<Int>> = context.settingsDataStore.data
        .map { p -> p[KEY_SCHED_ALT_DAYS]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: DEFAULT_ALT_DAYS }

    val altBedtimeMinutes: Flow<Int> = context.settingsDataStore.data
        .map { it[KEY_ALT_BEDTIME_MIN] ?: DEFAULT_ALT_BEDTIME_MIN }

    val altWakeMinutes: Flow<Int> = context.settingsDataStore.data
        .map { it[KEY_ALT_WAKE_MIN] ?: DEFAULT_ALT_WAKE_MIN }

    /** Vista combinada del horario, para que el scheduler lo lea de una sola vez. */
    val sleepSchedule: Flow<SleepSchedule> = context.settingsDataStore.data.map { p ->
        SleepSchedule(
            enabled = p[KEY_SCHED_ENABLED] ?: false,
            autoStart = p[KEY_SCHED_AUTOSTART] ?: false,
            bedtimeMinutes = p[KEY_BEDTIME_MIN] ?: DEFAULT_BEDTIME_MIN,
            wakeMinutes = p[KEY_WAKE_MIN] ?: DEFAULT_WAKE_MIN,
            altEnabled = p[KEY_SCHED_ALT_ENABLED] ?: false,
            altDays = p[KEY_SCHED_ALT_DAYS]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: DEFAULT_ALT_DAYS,
            altBedtimeMinutes = p[KEY_ALT_BEDTIME_MIN] ?: DEFAULT_ALT_BEDTIME_MIN,
            altWakeMinutes = p[KEY_ALT_WAKE_MIN] ?: DEFAULT_ALT_WAKE_MIN,
        )
    }

    suspend fun setScheduleEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SCHED_ENABLED] = enabled }
    }

    suspend fun setScheduleAutoStart(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SCHED_AUTOSTART] = enabled }
    }

    suspend fun setBedtimeMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[KEY_BEDTIME_MIN] = minutes.coerceIn(0, 24 * 60 - 1) }
    }

    suspend fun setWakeMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[KEY_WAKE_MIN] = minutes.coerceIn(0, 24 * 60 - 1) }
    }

    suspend fun setScheduleAltEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SCHED_ALT_ENABLED] = enabled }
    }

    suspend fun setScheduleAltDays(days: Set<Int>) {
        context.settingsDataStore.edit { p ->
            p[KEY_SCHED_ALT_DAYS] = days.filter { it in 1..7 }.map { it.toString() }.toSet()
        }
    }

    suspend fun setAltBedtimeMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[KEY_ALT_BEDTIME_MIN] = minutes.coerceIn(0, 24 * 60 - 1) }
    }

    suspend fun setAltWakeMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[KEY_ALT_WAKE_MIN] = minutes.coerceIn(0, 24 * 60 - 1) }
    }

    companion object {
        private val KEY_OPEN_MARGIN_DB = doublePreferencesKey("open_margin_db")
        private val KEY_TRANSCRIBE_SPEECH = booleanPreferencesKey("transcribe_speech")
        private val KEY_SCHED_ENABLED = booleanPreferencesKey("schedule_enabled")
        private val KEY_SCHED_AUTOSTART = booleanPreferencesKey("schedule_auto_start")
        private val KEY_BEDTIME_MIN = intPreferencesKey("schedule_bedtime_min")
        private val KEY_WAKE_MIN = intPreferencesKey("schedule_wake_min")
        private val KEY_SCHED_ALT_ENABLED = booleanPreferencesKey("schedule_alt_enabled")
        private val KEY_SCHED_ALT_DAYS = stringSetPreferencesKey("schedule_alt_days")
        private val KEY_ALT_BEDTIME_MIN = intPreferencesKey("schedule_alt_bedtime_min")
        private val KEY_ALT_WAKE_MIN = intPreferencesKey("schedule_alt_wake_min")

        /** Igual al default de EventDetector.Config; validado en noches reales. */
        const val DEFAULT_OPEN_MARGIN_DB = 12.0
        const val MIN_OPEN_MARGIN_DB = 6.0
        const val MAX_OPEN_MARGIN_DB = 20.0

        const val DEFAULT_BEDTIME_MIN = 22 * 60 + 30 // 22:30
        const val DEFAULT_WAKE_MIN = 6 * 60 + 30 // 06:30

        /** Noches de viernes y sábado (ISO: lunes=1…domingo=7). */
        val DEFAULT_ALT_DAYS = setOf(5, 6)
        const val DEFAULT_ALT_BEDTIME_MIN = 23 * 60 + 30 // 23:30
        const val DEFAULT_ALT_WAKE_MIN = 8 * 60 // 08:00
    }
}

/** Configuración del horario de sueño (inmutable, para pasarla al scheduler). */
data class SleepSchedule(
    val enabled: Boolean,
    val autoStart: Boolean,
    val bedtimeMinutes: Int,
    val wakeMinutes: Int,
    val altEnabled: Boolean,
    /** Días ISO (lunes=1…domingo=7) cuya noche —la que EMPIEZA ese día— usa el horario alterno. */
    val altDays: Set<Int>,
    val altBedtimeMinutes: Int,
    val altWakeMinutes: Int,
) {
    fun bedtimeFor(isoDay: Int): Int =
        if (altEnabled && isoDay in altDays) altBedtimeMinutes else bedtimeMinutes

    fun wakeFor(isoDay: Int): Int =
        if (altEnabled && isoDay in altDays) altWakeMinutes else wakeMinutes
}
