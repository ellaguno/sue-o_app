# Sonido de calma (SesoLibre Sonidos)

Todo lo de esta carpeta y `../../jniLibs/*/libsonidos.so` viene de
https://github.com/ellaguno/sesolibre-sonidos (commit f272dc8):

- `receta-calma.json` — la receta `sonar.calma-ronquido` (qué suena y a qué nivel).
- `audio/instruments/cello_sus/` — las muestras que esa receta necesita (Versilian Studios,
  VSCO 256, uso libre).
- `libsonidos.so` — el núcleo de sonido en Rust (`sonidos/android`), que genera el audio.

Para actualizarlos: en sesolibre-sonidos, `app/scripts/android-apk.sh` compila los `.so` en
`app/plugin/android/src/main/jniLibs/`; se copian aquí junto con la receta y sus muestras.
La clase `com.sesolibre.sonidos.motor.Nativo` debe conservar ese nombre: el `.so` la busca así.
