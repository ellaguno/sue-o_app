# SesoLibre Somnia — Monitor Nocturno de Sueño (Android)

Monitor nocturno **no invasivo y privado**: escucha el micrófono durante la noche, detecta y clasifica sonidos (ronquidos, tos, habla, ruido ambiental), mide niveles de sonido aproximados y genera estadísticas para ayudar a mejorar hábitos, posturas y condiciones de sueño.

**Principios:**
- 🔒 Todo el procesamiento y almacenamiento es local — el audio nunca sale del dispositivo.
- 🎙️ No se graba la noche completa; solo clips de los eventos detectados.
- ⚕️ No es un dispositivo médico; orienta y documenta, no diagnostica.

## Documentación
- [Análisis de alcances](docs/01-ANALISIS-ALCANCES.md)
- [Plan detallado de desarrollo](docs/02-PLAN-DESARROLLO.md)
- [Guía de release / Google Play](docs/03-RELEASE.md)

## Estado
🔨 Etapas 0–1 completadas: proyecto Android (Kotlin + Compose + Hilt + Room), servicio en primer plano con captura a 16 kHz, medición de dB por segundo agregada por minuto, sesiones en Room, pantalla principal con estado en vivo e historial de noches. CI en GitHub Actions. Pendiente: prueba de una noche completa en dispositivos reales.

## Compilar

```bash
./gradlew assembleDebug        # APK de desarrollo
./gradlew testDebugUnitTest    # tests unitarios
./gradlew bundleRelease        # AAB firmado para Play (requiere keystore.properties)
```

## Decisiones técnicas
- Códec de clips (Etapa 2): **Opus** (más eficiente que AAC a 16 kHz mono para voz/ronquido).
- Clasificación: YAMNet sobre LiteRT, 100% on-device.
- Licencia: MIT.

## Fases
1. **Fase 1 (actual):** app Android con captura nocturna, detección/clasificación de eventos (YAMNet on-device), niveles de dB, perfil, bitácora y estadísticas.
2. **Fase 2:** integración con relojes inteligentes vía Health Connect para superponer etapas de sueño.
