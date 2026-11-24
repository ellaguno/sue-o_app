package com.sesolibre.somnia.sonidos

/**
 * Decide cuándo tocar el sonido suave al detectar ronquidos (experimento
 * opcional). No dispara con el primer ronquido: hacen falta [snoresNeeded]
 * dentro de [windowMs]. Después de que termina un sonido, espera
 * [cooldownMs] antes de poder volver a sonar. Lógica pura, testeable.
 */
class SnoreSoundTrigger(
    private val snoresNeeded: Int = 3,
    private val windowMs: Long = 5 * 60_000L,
    private val cooldownMs: Long = 40 * 60_000L,
) {
    private val recent = ArrayDeque<Long>()
    private var playing = false
    private var lastEndMs: Long? = null

    /** Registra un ronquido; devuelve true si ahora toca empezar a sonar. */
    fun onSnore(atMs: Long): Boolean {
        if (playing) return false
        lastEndMs?.let { if (atMs - it < cooldownMs) return false }
        recent.addLast(atMs)
        while (recent.isNotEmpty() && atMs - recent.first() > windowMs) recent.removeFirst()
        if (recent.size < snoresNeeded) return false
        recent.clear()
        playing = true
        return true
    }

    /** El sonido terminó (o no pudo empezar): arranca la espera. */
    fun onPlaybackEnded(atMs: Long) {
        playing = false
        lastEndMs = atMs
    }
}
