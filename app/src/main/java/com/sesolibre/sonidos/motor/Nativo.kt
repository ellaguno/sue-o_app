package com.sesolibre.sonidos.motor

/**
 * El núcleo de sonido de SesoLibre Sonidos (Rust → `libsonidos.so`, en jniLibs). El paquete
 * y el nombre de esta clase no se pueden cambiar: la biblioteca nativa los busca tal cual.
 * Los `Long` son manejadores opacos; los errores de receta llegan como
 * [IllegalArgumentException]. Ver `assets/sonidos/LEEME.md`.
 */
object Nativo {
    init {
        System.loadLibrary("sonidos")
    }

    /** Bits que devuelve [procesar]. */
    const val TERMINADO = 1
    const val EN_SILENCIO = 2

    @JvmStatic external fun validar(receta: String): String?
    @JvmStatic external fun archivos(receta: String): String
    @JvmStatic external fun bancoNuevo(): Long
    @JvmStatic external fun bancoAgregar(banco: Long, nombre: String, ogg: ByteArray)
    @JvmStatic external fun bancoLiberar(banco: Long)
    @JvmStatic external fun crear(receta: String, duracionS: Float, semilla: Long, banco: Long): Long
    @JvmStatic external fun procesar(sesion: Long, salida: FloatArray, cuadros: Int): Int
    @JvmStatic external fun atenuar(sesion: Long, callar: Boolean)
    @JvmStatic external fun volumen(sesion: Long, db: Float)
    @JvmStatic external fun extender(sesion: Long, segundos: Float): Float
    @JvmStatic external fun segundos(sesion: Long): Double
    @JvmStatic external fun duracion(sesion: Long): Double
    @JvmStatic external fun espectro(sesion: Long, bandas: FloatArray)
    @JvmStatic external fun liberar(sesion: Long)
}
