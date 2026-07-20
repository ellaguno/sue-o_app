package com.sesolibre.somnia.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
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

    companion object {
        private val KEY_OPEN_MARGIN_DB = doublePreferencesKey("open_margin_db")
        private val KEY_TRANSCRIBE_SPEECH = booleanPreferencesKey("transcribe_speech")

        /** Igual al default de EventDetector.Config; validado en noches reales. */
        const val DEFAULT_OPEN_MARGIN_DB = 12.0
        const val MIN_OPEN_MARGIN_DB = 6.0
        const val MAX_OPEN_MARGIN_DB = 20.0
    }
}
