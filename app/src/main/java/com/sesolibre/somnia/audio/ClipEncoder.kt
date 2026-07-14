package com.sesolibre.somnia.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import java.io.File
import java.nio.ByteOrder

/**
 * Codifica PCM 16-bit mono a un clip comprimido.
 *
 * - Android 10+ (API 29): **Opus** en contenedor OGG (~24 kbps, el más
 *   eficiente para voz/ronquido a 16 kHz).
 * - Android 8–9: fallback **AAC-LC** en M4A (no traen codificador Opus).
 */
object ClipEncoder {

    /** Extensión correcta según el códec disponible en este dispositivo. */
    val fileExtension: String
        get() = if (opusSupported) "ogg" else "m4a"

    private val opusSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 29

    /**
     * Codifica [pcm] y lo escribe en [outFile]. Devuelve `true` si el archivo
     * quedó escrito correctamente.
     */
    fun encode(pcm: ShortArray, sampleRate: Int, outFile: File): Boolean {
        outFile.parentFile?.mkdirs()
        return try {
            if (opusSupported) {
                encodeInternal(
                    pcm, sampleRate, outFile,
                    mime = MediaFormat.MIMETYPE_AUDIO_OPUS,
                    bitRate = 24_000,
                    muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG,
                )
            } else {
                encodeInternal(
                    pcm, sampleRate, outFile,
                    mime = MediaFormat.MIMETYPE_AUDIO_AAC,
                    bitRate = 32_000,
                    muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error codificando clip", e)
            outFile.delete()
            false
        }
    }

    private fun encodeInternal(
        pcm: ShortArray,
        sampleRate: Int,
        outFile: File,
        mime: String,
        bitRate: Int,
        muxerFormat: Int,
    ) {
        val format = MediaFormat.createAudioFormat(mime, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                )
            }
        }
        val codec = MediaCodec.createEncoderByType(mime)
        val muxer = MediaMuxer(outFile.absolutePath, muxerFormat)
        var muxerStarted = false
        var trackIndex = -1

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var samplesFed = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        inBuf.order(ByteOrder.LITTLE_ENDIAN)
                        val room = inBuf.remaining() / 2
                        val toFeed = minOf(room, pcm.size - samplesFed)
                        val ptsUs = samplesFed * 1_000_000L / sampleRate
                        if (toFeed > 0) {
                            inBuf.asShortBuffer().put(pcm, samplesFed, toFeed)
                            samplesFed += toFeed
                            codec.queueInputBuffer(inIndex, 0, toFeed * 2, ptsUs, 0)
                        }
                        if (samplesFed >= pcm.size) {
                            val eosIndex = if (toFeed > 0) codec.dequeueInputBuffer(TIMEOUT_US) else inIndex
                            if (eosIndex >= 0) {
                                codec.queueInputBuffer(
                                    eosIndex, 0, 0,
                                    samplesFed * 1_000_000L / sampleRate,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            }
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private const val TAG = "ClipEncoder"
    private const val TIMEOUT_US = 10_000L
}
