package com.sesolibre.somnia.ui.components

/**
 * Tramo visible de la gráfica de la noche, como fracciones de la noche
 * completa (0 = inicio, 1 = fin). Sin zoom es [0, 1]. Lógica pura, testeable.
 */
data class TimelineWindow(val start: Float = 0f, val end: Float = 1f) {

    val width: Float get() = end - start
    val isZoomed: Boolean get() = width < 0.999f

    /** Posición en pantalla (0..1 del ancho) de un punto de la noche. */
    fun toScreen(fraction: Float): Float = (fraction - start) / width

    /** Punto de la noche (0..1) bajo una posición de la pantalla. */
    fun fromScreen(screen: Float): Float = start + screen * width

    /**
     * Acerca ([factor] > 1) o aleja (< 1) dejando fijo el punto que está bajo
     * [anchorScreen] (el dedo). El tramo nunca es más angosto que [minWidth].
     */
    fun zoom(factor: Float, anchorScreen: Float, minWidth: Float): TimelineWindow {
        if (factor <= 0f) return this
        val anchor = fromScreen(anchorScreen)
        val newWidth = (width / factor).coerceIn(minWidth.coerceAtMost(1f), 1f)
        return fit(anchor - anchorScreen * newWidth, newWidth)
    }

    /** Desplaza el tramo; [deltaScreen] es la fracción del ancho que se arrastró. */
    fun pan(deltaScreen: Float): TimelineWindow = fit(start - deltaScreen * width, width)

    companion object {
        val FULL = TimelineWindow()

        /** Mantiene el tramo dentro de la noche sin cambiar su ancho. */
        private fun fit(start: Float, width: Float): TimelineWindow {
            val s = start.coerceIn(0f, 1f - width)
            return TimelineWindow(s, s + width)
        }

        /**
         * Cada cuántos minutos va una marca del eje según lo que se ve, para que
         * haya unas 3–8 etiquetas: horas con la noche completa, minutos al acercar.
         */
        fun tickStepMinutes(visibleMs: Long): Int {
            val minutes = visibleMs / 60_000.0
            return when {
                minutes > 8 * 60 -> 120
                minutes > 3 * 60 -> 60
                minutes > 90 -> 30
                minutes > 40 -> 15
                minutes > 15 -> 5
                else -> 1
            }
        }
    }
}
