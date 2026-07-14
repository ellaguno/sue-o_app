# Sueño — Monitor Nocturno de Sueño (Android)

Monitor nocturno **no invasivo y privado**: escucha el micrófono durante la noche, detecta y clasifica sonidos (ronquidos, tos, habla, ruido ambiental), mide niveles de sonido aproximados y genera estadísticas para ayudar a mejorar hábitos, posturas y condiciones de sueño.

**Principios:**
- 🔒 Todo el procesamiento y almacenamiento es local — el audio nunca sale del dispositivo.
- 🎙️ No se graba la noche completa; solo clips de los eventos detectados.
- ⚕️ No es un dispositivo médico; orienta y documenta, no diagnostica.

## Documentación
- [Análisis de alcances](docs/01-ANALISIS-ALCANCES.md)
- [Plan detallado de desarrollo](docs/02-PLAN-DESARROLLO.md)

## Estado
📋 En planeación — Etapa 0.

## Fases
1. **Fase 1 (actual):** app Android con captura nocturna, detección/clasificación de eventos (YAMNet on-device), niveles de dB, perfil, bitácora y estadísticas.
2. **Fase 2:** integración con relojes inteligentes vía Health Connect para superponer etapas de sueño.
