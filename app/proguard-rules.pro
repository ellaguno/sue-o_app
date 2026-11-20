# Reglas específicas del proyecto. Room y Hilt incluyen sus propias reglas
# consumer-proguard en sus AARs; no se requiere nada adicional por ahora.

# El núcleo de SesoLibre Sonidos (libsonidos.so) busca estos métodos por nombre desde JNI.
-keep class com.sesolibre.sonidos.motor.Nativo { *; }
