package com.sesolibre.somnia.ml

/**
 * Preparación de PCM para YAMNet: ventanas de [windowSize] muestras float
 * normalizadas a [-1, 1], con salto de [hopSize]. Si el audio es más corto
 * que una ventana se rellena con ceros. Lógica pura, testeable en JVM.
 */
object AudioWindows {

    const val YAMNET_WINDOW = 15_600 // 0.975 s @ 16 kHz
    const val YAMNET_HOP = 7_800     // 50% de traslape

    fun windows(
        pcm: ShortArray,
        windowSize: Int = YAMNET_WINDOW,
        hopSize: Int = YAMNET_HOP,
        maxWindows: Int = 16,
    ): List<FloatArray> {
        if (pcm.isEmpty()) return emptyList()
        val result = ArrayList<FloatArray>()
        var start = 0
        while (start < pcm.size && result.size < maxWindows) {
            val available = minOf(windowSize, pcm.size - start)
            // Una ventana de cola mayormente en cero (relleno) hace que YAMNet
            // prediga "Silence" con alta confianza y contamina la clasificación;
            // la descartamos salvo que sea la única ventana del evento.
            if (result.isNotEmpty() && available < windowSize / 2) break
            val window = FloatArray(windowSize)
            for (i in 0 until available) {
                window[i] = pcm[start + i] / 32768f
            }
            result.add(window) // el resto queda en 0 (padding)
            if (start + windowSize >= pcm.size) break
            start += hopSize
        }
        return result
    }
}
