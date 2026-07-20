package com.sesolibre.somnia.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteOrder

/** Resultado de decodificar un clip: PCM 16-bit mono + su frecuencia. */
data class DecodedPcm(val pcm: ShortArray, val sampleRate: Int)

/**
 * Decodifica un clip comprimido (Opus/OGG o AAC/M4A) a PCM 16-bit mono para
 * volver a procesarlo (p. ej. transcribir el habla). Contraparte de
 * [ClipEncoder]. Bloqueante; llamar fuera del hilo principal.
 */
object PcmDecoder {

    private const val TAG = "PcmDecoder"
    private const val TIMEOUT_US = 10_000L

    fun decode(file: File): DecodedPcm? {
        if (!file.exists()) return null
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val out = drain(codec, extractor)
            codec.stop()
            codec.release()
            extractor.release()
            DecodedPcm(out, sampleRate)
        } catch (t: Throwable) {
            Log.w(TAG, "no se pudo decodificar ${file.name}", t)
            runCatching { extractor.release() }
            null
        }
    }

    private fun drain(codec: MediaCodec, extractor: MediaExtractor): ShortArray {
        val info = MediaCodec.BufferInfo()
        val shorts = ArrayList<Short>()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex >= 0) {
                val buffer = codec.getOutputBuffer(outIndex)!!
                val sb = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (sb.hasRemaining()) shorts.add(sb.get())
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        return ShortArray(shorts.size) { shorts[it] }
    }
}
