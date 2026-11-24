# Integrar SesoLibre Sonidos en Somnia

Nota para el equipo de Somnia (21 nov 2026). Sonidos ya está listo de su lado: la receta,
el núcleo para Android y para iOS, y los enlaces directos. Falta todo lo de este repositorio.
La guía completa está en `sesolibre-sonidos/docs/INTEGRAR.md`, y el razonamiento en
`sesolibre-sonidos/DISEÑO.md §7`.

## Qué queremos

Cuando Somnia detecta ronquidos de noche, toca **un sonido muy suave generado en el teléfono**:
la receta `sonar.calma-ronquido`, que dura 10 min, tiene un techo de −20 dBFS, entra en 30 s
y sale en 2 min. Es ruido marrón con un vaivén lento y cuerdas graves, sin nada que despierte.
Luego Somnia **compara noches con y sin sonido**.

**Esto es un experimento, no una función que "reduce ronquidos".** No hay evidencia de que
un sonido los reduzca (lo que tiene respaldo es la vibración para cambiar de postura). La
receta está etiquetada *Prometedor* y lo dice. En la app y en la ficha de tienda vale lo
de `sesolibre-sonidos/docs/PALABRAS.md`: nada de curar, tratar ni terapia, y "no sustituye
atención médica".

## Por qué el sonido tiene que vivir dentro de Somnia

Somnia no puede pedirle a la app Sonidos que suene a las 3 a.m. Android 12+ no deja
arrancar el servicio en primer plano de otra app desde segundo plano, y en iOS una app
suspendida no puede despertar a otra. El núcleo de Sonidos (Rust, sin dependencias, unos
cientos de KB) se embebe en Somnia y suena desde `MonitorService`, que ya está en primer plano.

## Android (Kotlin)

1. **Archivos.** De `sesolibre-sonidos` copiar:
   - `app/plugin/android/src/main/java/com/sesolibre/sonidos/motor/Nativo.kt`: el puente JNI.
     Hay que conservar el paquete `com.sesolibre.sonidos.motor`, porque la librería busca
     esos nombres. Además añadir `-keep class com.sesolibre.sonidos.motor.Nativo { *; }` a
     las reglas de R8.
   - `libsonidos.so` de cada arquitectura, que se compila desde `sesolibre-sonidos` con:
     `cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o <somnia>/app/src/main/jniLibs build --release -p sonidos-android`.
   - Las muestras que use la receta, en `assets/audio/`. `Nativo.archivos(receta)` dice
     cuáles son.
   - `recetas/sonar.calma-ronquido.json`, en `assets/`.

   Aún no hay AAR publicado; por ahora se copian archivos. Si prefieren AAR, lo
   preparamos del lado de Sonidos.
2. **Reproductor.** `SonidosPlayer.kt` (mismo paquete) es la referencia, aunque Somnia
   no necesita MediaSession: un hilo basta. Lo mínimo:

   ```kotlin
   val receta = assets.open("sonar.calma-ronquido.json").bufferedReader().readText()
   val banco = Nativo.bancoNuevo()
   for (a in Nativo.archivos(receta).split('\n')) if (a.isNotEmpty())
       Nativo.bancoAgregar(banco, a, assets.open("audio/$a").readBytes())
   val sesion = Nativo.crear(receta, 0f, -1L, banco)  // duración y semilla de la receta
   Nativo.bancoLiberar(banco)
   // hilo de audio: AudioTrack estéreo float 48 kHz, USAGE_MEDIA
   while (true) {
       val estado = Nativo.procesar(sesion, bloque, CUADROS)  // estéreo intercalado
       track.write(bloque, 0, bloque.size, AudioTrack.WRITE_BLOCKING)
       if (estado and 1 != 0) break                            // TERMINADO
   }
   Nativo.liberar(sesion)
   ```
   Para cortar antes de tiempo, llamar a `Nativo.atenuar(sesion, true)` y seguir
   procesando hasta que devuelva el bit `EN_SILENCIO` (2): así no hay chasquido.
3. **Dónde engancharlo.** En `MonitorService.onEventCaptured`, cuando la clasificación sea
   `SomniaCategory.SNORING`. Conviene no disparar con el primer ronquido sino con, por
   ejemplo, 3 en 5 minutos, y no volver a sonar antes de 30–45 min. El tipo de servicio
   tiene que pasar a ser `microphone|mediaPlayback`, en el manifiesto
   (`foregroundServiceType`) y en `startForeground`. Además hay que pedir
   `FOREGROUND_SERVICE_MEDIA_PLAYBACK` y declararlo en Play Console.

## iOS (Swift)

`sesolibre-sonidos/sonidos/ios` expone el mismo núcleo como **API de C** (`include/sonidos.h`,
módulo `SonidosNucleo`). `app/scripts/ios-nucleo.sh` lo empaqueta como XCFramework y se añade
al proyecto de XcodeGen como framework estático. El uso en Swift es el de
`app/plugin/ios/Sources/SonidosPlugin/Nativo.swift` y `SonidosPlayer.swift`: un hilo que
llama `sonidos_procesar` y llena un anillo (`sonidos_anillo_*`), y un `AVAudioSourceNode`
que solo lee del anillo.

**Ojo con la sesión de audio.** Hoy `AudioServices` usa `.record` / `.measurement`. Para
grabar y sonar a la vez hay que pasar a `.playAndRecord` con `.defaultToSpeaker` (y
`.mixWithOthers` si se quiere convivir con otras apps). Hay que comprobar que `.measurement`
siga sin AGC con esa categoría. `UIBackgroundModes` ya tiene `audio`.

## El problema que hay que resolver: el micrófono va a oír el sonido

Somnia graba mientras suena. Sin cuidado:

- El ruido marrón sube el **nivel por minuto** (`onSecondReading`) y ensucia la gráfica.
- Puede **disparar eventos** falsos y llevar a YAMNet a clasificar "ruido" o "música".

Propuesta:

- Guardar en la sesión **cuándo sonó** (inicio y fin). Es la columna que hace falta para
  comparar noches, así que hay que añadirla a la base de datos.
- **No detectar eventos** mientras suena. Una alternativa es subir el umbral unos dB, ya
  que el techo de la receta es −20 dBFS en la salida y en el micrófono llega mucho menos.
- En la gráfica, marcar esos minutos en lugar de esconderlos.
- Probarlo en un teléfono real: altavoz a la distancia de la mesa de noche y volumen bajo.

## El caso del usuario despierto

Para la rutina de acostarse (por ejemplo, un botón "sonido para dormir" en la pantalla de
noche) no hace falta embeber nada: basta abrir la app Sonidos con un enlace directo.

```
sesolibre-sonidos://tocar?receta=dormir.mar-cuerdas&min=30
```

Si Sonidos no está instalada, conviene mandar a su ficha en Play (`com.sesolibre.sonidos`).
En iPhone Sonidos cuesta 0.99 USD; en Android es gratis.

## Para preguntar

Cualquier duda del núcleo o de las recetas va al repositorio `sesolibre-sonidos`. Las recetas
nuevas para Somnia se agregan ahí: el núcleo rechaza las que tengan techo alto, tonos fuera
de 20–16 000 Hz o palabras prohibidas.
