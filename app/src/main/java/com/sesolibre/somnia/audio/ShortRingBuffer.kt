package com.sesolibre.somnia.audio

/**
 * Buffer circular de muestras PCM. Mantiene las últimas [capacity] muestras
 * para poder extraer el "pre-roll" (el audio inmediatamente anterior al
 * disparo de un evento) sin grabar la noche completa.
 */
class ShortRingBuffer(private val capacity: Int) {

    private val buffer = ShortArray(capacity)
    private var writePos = 0
    private var filled = 0

    fun write(samples: ShortArray, length: Int = samples.size) {
        for (i in 0 until length) {
            buffer[writePos] = samples[i]
            writePos = (writePos + 1) % capacity
        }
        filled = minOf(filled + length, capacity)
    }

    /** Copia las últimas [n] muestras (o menos, si aún no hay suficientes). */
    fun snapshotLast(n: Int): ShortArray {
        val count = minOf(n, filled)
        val out = ShortArray(count)
        var pos = (writePos - count + capacity * 2) % capacity
        for (i in 0 until count) {
            out[i] = buffer[pos]
            pos = (pos + 1) % capacity
        }
        return out
    }

    val size: Int get() = filled
}
