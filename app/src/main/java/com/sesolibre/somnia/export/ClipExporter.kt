package com.sesolibre.somnia.export

import android.content.Context
import android.util.Log
import com.sesolibre.somnia.audio.ClipEncoder
import com.sesolibre.somnia.audio.ClipPlaybackGain
import com.sesolibre.somnia.audio.PcmDecoder
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import java.io.File
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.pow

/**
 * Prepara clips para enviarlos fuera del teléfono. Los originales se graban
 * muy bajos (~-45 dBFS) y en la app se amplifican al reproducir; quien reciba
 * el archivo no tendría esa ayuda. Aquí se decodifica cada clip, se le aplica
 * la misma ganancia ([ClipPlaybackGain]) con un tope para no saturar, y se
 * re-codifica en AAC/M4A (se abre en cualquier equipo). El original no cambia.
 */
object ClipExporter {

    private const val TAG = "ClipExporter"
    /** Pico máximo tras amplificar (~-1 dBFS): evita recortes audibles. */
    private const val CEILING = 29_000

    data class Exported(val event: SoundEvent, val file: File)

    /** Tamaño estimado del M4A de un evento (AAC a 32 kbps), para avisar antes de enviar. */
    fun estimatedBytes(event: SoundEvent): Long = (event.durationMs + 1_000L) * 32_000L / 8 / 1000 + 2_000L

    fun export(
        context: Context,
        events: List<SoundEvent>,
        outDir: File,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Exported> {
        outDir.mkdirs()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(zone)
        return events.mapNotNull { event ->
            val path = event.clipPath ?: return@mapNotNull null
            val decoded = PcmDecoder.decode(File(path)) ?: return@mapNotNull null
            val category = slug(ReportLabels.category(context, ApneaHeuristic.effectiveCategory(event)))
            val out = File(outDir, "somnia_${fmt.format(Instant.ofEpochMilli(event.startEpochMs))}_$category.m4a")
            val pcm = amplify(decoded.pcm, ClipPlaybackGain.gainDb(event.dbPeak))
            if (ClipEncoder.encode(pcm, decoded.sampleRate, out, forceAac = true)) {
                Exported(event, out)
            } else {
                Log.w(TAG, "no se pudo exportar el clip ${event.id}")
                null
            }
        }
    }

    /** Ganancia en dB, limitada para que el pico no pase de [CEILING]. */
    fun amplify(pcm: ShortArray, gainDb: Double): ShortArray {
        var peak = 1
        for (s in pcm) peak = maxOf(peak, abs(s.toInt()))
        val gain = minOf(10.0.pow(gainDb / 20.0), CEILING.toDouble() / peak).coerceAtLeast(1.0)
        if (gain == 1.0) return pcm
        return ShortArray(pcm.size) {
            (pcm[it] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** "Respiración" → "respiracion" (nombres de archivo sin acentos ni espacios). */
    fun slug(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
}
