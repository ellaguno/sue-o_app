package com.sesolibre.sonidos.motor

/**
 * El núcleo de sonido (Rust, `sonidos/android` → libsonidos.so). Los `Long` son manejadores
 * opacos: un banco de muestras y una sesión (una receta sonando). Los errores de receta
 * llegan como [IllegalArgumentException] con el mensaje del núcleo.
 */
internal object Nativo {
    init {
        System.loadLibrary("sonidos")
    }

    /** Bits que devuelve [procesar]. */
    const val TERMINADO = 1
    const val EN_SILENCIO = 2

    /** `null` si la receta es válida; si no, el motivo. */
    @JvmStatic external fun validar(receta: String): String?

    /** Archivos de `assets/audio/` que la receta necesita, uno por línea. */
    @JvmStatic external fun archivos(receta: String): String

    @JvmStatic external fun bancoNuevo(): Long
    @JvmStatic external fun bancoAgregar(banco: Long, nombre: String, ogg: ByteArray)
    @JvmStatic external fun bancoLiberar(banco: Long)

    /** `duracionS` ≤ 0 y `semilla` < 0 = las de la receta. */
    @JvmStatic external fun crear(receta: String, duracionS: Float, semilla: Long, banco: Long): Long

    /** Llena `salida` (estéreo intercalado). Devuelve bits de estado, o -1 si falló. */
    @JvmStatic external fun procesar(sesion: Long, salida: FloatArray, cuadros: Int): Int

    @JvmStatic external fun atenuar(sesion: Long, callar: Boolean)
    @JvmStatic external fun volumen(sesion: Long, db: Float)
    @JvmStatic external fun extender(sesion: Long, segundos: Float): Float
    @JvmStatic external fun segundos(sesion: Long): Double
    @JvmStatic external fun duracion(sesion: Long): Double
    /** Mueve el tono en vivo (capa `mando`). Devuelve la frecuencia que quedó (20–2000 Hz). */
    @JvmStatic external fun mando(sesion: Long, hz: Float): Float

    /** Espectro de lo último que sonó: 0..1 por banda, graves primero. */
    @JvmStatic external fun espectro(sesion: Long, bandas: FloatArray)
    @JvmStatic external fun liberar(sesion: Long)
}
