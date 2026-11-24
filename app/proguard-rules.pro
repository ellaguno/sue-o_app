# Reglas específicas del proyecto. Room y Hilt incluyen sus propias reglas
# consumer-proguard en sus AARs; no se requiere nada adicional por ahora.

# Núcleo de SesoLibre Sonidos: la librería nativa (libsonidos.so) busca por JNI
# estos nombres exactos; R8 no debe renombrarlos ni quitarlos.
-keep class com.sesolibre.sonidos.motor.Nativo { *; }
