package com.sesolibre.somnia.calm

/**
 * Decide cuándo poner el sonido de calma: varios ronquidos seguidos, no uno suelto, y sin
 * insistir. Lógica pura (sin Android) para poder probarla.
 *
 * Dispara cuando hay [Config.snores] ronquidos dentro de [Config.windowMs], siempre que
 * haya pasado [Config.cooldownMs] desde el último disparo y no se haya llegado a
 * [Config.maxPerNight].
 */
class CalmTrigger(private val config: Config = Config()) {

    data class Config(
        val snores: Int = 6,
        val windowMs: Long = 5 * 60_000L,
        /** Desde que empieza a sonar; incluye los 10 min que dura el sonido. */
        val cooldownMs: Long = 45 * 60_000L,
        val maxPerNight: Int = 4,
    )

    private val recent = ArrayDeque<Long>()
    private var lastFiredMs: Long? = null
    var firedCount = 0
        private set

    /** Registra un ronquido en [nowMs]; devuelve true si toca empezar el sonido. */
    fun onSnore(nowMs: Long): Boolean {
        recent.addLast(nowMs)
        while (recent.isNotEmpty() && nowMs - recent.first() > config.windowMs) recent.removeFirst()
        if (firedCount >= config.maxPerNight) return false
        lastFiredMs?.let { if (nowMs - it < config.cooldownMs) return false }
        if (recent.size < config.snores) return false
        lastFiredMs = nowMs
        firedCount++
        recent.clear()
        return true
    }
}
