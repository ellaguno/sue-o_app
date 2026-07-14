# Análisis de Alcances — App de Monitoreo Nocturno de Sueño ("Sueño")

**Fecha:** 2026-07-14
**Plataforma inicial:** Android
**Repositorio:** https://github.com/ellaguno/sue-o_app.git

---

## 1. Visión y propósito

Una app Android que actúa como **monitor nocturno no invasivo**: escucha el micrófono del teléfono durante la noche, detecta y registra eventos de sonido (ronquidos, tos, habla, movimiento, ruidos ambientales), mide niveles de sonido, y presenta estadísticas que ayuden a **diagnosticar situaciones de sueño** para sugerir mejoras en hábitos, posturas y condiciones del entorno.

**Principio rector:** privacidad primero. Todo el procesamiento y almacenamiento es **local en el dispositivo**; no se graba audio continuo, solo los fragmentos donde se detectó un evento.

> ⚠️ **No es un dispositivo médico.** La app orienta y documenta; no diagnostica. Todo reporte debe incluir este descargo. Si se detectan patrones compatibles con apnea, la app solo puede *sugerir consultar a un médico*.

---

## 2. Alcance funcional — Fase 1 (Android)

### 2.1 Sesión de monitoreo nocturno
- El usuario inicia una sesión al acostarse (o programa hora de inicio automático) y la termina al despertar (manual o por hora programada).
- Servicio en primer plano (*foreground service* tipo `microphone`) que mantiene la captura activa toda la noche con pantalla apagada.
- Indicador claro y persistente de que el micrófono está activo (notificación + requerimiento del sistema Android).
- Consumo de batería objetivo: < 12–15% en 8 horas con el teléfono conectado o no; se recomendará cargar durante la noche.

### 2.2 Detección y almacenamiento de eventos de sonido
- El audio se procesa en tiempo real en memoria; **no se guarda la noche completa**.
- Un detector de eventos (umbral adaptativo sobre el ruido de fondo) segmenta los momentos con sonido relevante.
- Por cada evento se guarda:
  - **Metadatos:** timestamp de inicio/fin, duración, nivel pico y promedio (dB), clase detectada y confianza, sesión a la que pertenece.
  - **Audio del evento:** clip comprimido (AAC/Opus) solo del fragmento detectado, con margen de ~1 s antes/después.
- Política de retención configurable (ej. borrar clips > 30 días, conservar solo metadatos).

### 2.3 Clasificación de sonidos
- Clasificador **on-device** con LiteRT (TensorFlow Lite) usando **YAMNet** (entrenado sobre AudioSet), que ya incluye clases relevantes: *Snoring, Snort, Cough, Speech, Breathing, Gasp, Dog, Traffic, Music*, etc.
- Mapeo a categorías propias: **Ronquido, Respiración/Apnea sospechosa, Tos, Habla/Somniloquia, Movimiento, Ruido ambiental, Otro**.
- Confianza mínima configurable; los eventos de baja confianza quedan como "Otro" y el usuario puede reetiquetarlos (esto genera datos para mejorar el mapeo).

### 2.4 Medición de niveles de sonido (decibeles)
- Cálculo de RMS → dBFS del micrófono de forma continua (cada ~1 s), guardando una serie de tiempo agregada (min/prom/máx por minuto) — muy ligera, sin audio.
- **Limitación honesta:** los micrófonos de teléfono no están calibrados; los valores serán **dB SPL aproximados** mediante un offset de calibración configurable (con opción de calibrar contra un sonómetro o app de referencia). La UI lo indicará como "dB (aprox.)".

### 2.5 Perfil de usuario y estadísticas
- Perfil: nombre, edad, sexo, peso/estatura (IMC es factor de ronquido), condiciones conocidas, medicamentos, si duerme solo o acompañado.
- Bitácora opcional por noche (diario de sueño): cafeína, alcohol, ejercicio, cena tardía, estrés, postura al dormir, calidad subjetiva al despertar (1–5).
- Estadísticas:
  - **Por noche:** línea de tiempo de eventos sobre gráfica de dB, total de episodios de ronquido, duración roncando, distribución por clase, periodos de silencio (proxy de sueño tranquilo).
  - **Tendencias:** por semana/mes — episodios por noche, % de la noche con ronquido, nivel de ruido ambiental promedio, correlaciones simples con la bitácora ("las noches con alcohol roncas 2.3× más").
- Reproducción de los clips más relevantes de cada noche ("lo más fuerte de anoche").

### 2.6 Múltiples personas en la habitación
- Si el perfil indica que **no duerme solo**, la app:
  - Muestra un aviso claro en cada reporte: *"Los sonidos pueden provenir de otras personas presentes"*.
  - Permite registrar acompañantes (nombre/alias) y **atribuir manualmente** eventos o noches completas a una persona.
  - Aviso de consentimiento: grabar a terceros requiere su conocimiento/consentimiento (responsabilidad del usuario; la app lo recuerda).
- **Fuera de alcance v1:** diarización automática de hablantes (identificar *quién* ronca por la voz). Es técnicamente frágil con ronquidos; se evalúa a futuro.

### 2.7 Ideas de valor agregado (incluidas en el plan)
- **Cuestionarios estandarizados** al configurar el perfil: escala de somnolencia de **Epworth** y opcionalmente **STOP-Bang** (screening de apnea) — dan contexto clínico real a las estadísticas.
- **Exportar reporte PDF/CSV** de una noche o un rango, pensado para llevarlo al médico.
- **Detección de patrón sospechoso de apnea** (heurística: ronquido fuerte → silencio ≥10 s → resoplido/gasp) marcada solo como "patrón a comentar con un médico".
- **Recomendaciones de higiene del sueño** basadas en los datos (ruido ambiental alto → tapones/máquina de ruido; ronquido correlacionado con alcohol → sugerencia; etc.).
- **Alarma inteligente** (futuro): despertar dentro de una ventana cuando hay sonido de movimiento (sueño ligero).
- **Nota de postura:** al reetiquetar, el usuario puede anotar postura si la conoce (boca arriba agrava el ronquido) — insumo para sugerencias.

---

## 3. Fuera de alcance — Fase 1
- iOS, web, sincronización en la nube, cuentas de usuario remotas.
- Diagnóstico médico o alertas de emergencia.
- Diarización automática de hablantes.
- Detección de fases del sueño por audio (no es confiable solo con sonido; llegará vía relojes en Fase 2).
- Grabación continua de toda la noche.

---

## 4. Fase 2 — Integración con relojes inteligentes
- **Vía Health Connect (Android):** es el camino estándar; Fitbit, Samsung Health, Garmin (vía apps puente), Oura y otros escriben sesiones y etapas de sueño ahí. La app **lee** `SleepSessionRecord` (con etapas: despierto, ligero, profundo, REM) y las superpone a la línea de tiempo de sonidos.
- Valor: correlacionar ronquidos/ruidos con etapas ("tus ronquidos ocurren en sueño profundo", "el ruido del tráfico coincide con tus despertares").
- No se requiere SDK por marca en la mayoría de los casos; Health Connect desacopla eso.

---

## 5. Restricciones y riesgos técnicos (Android)

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Restricciones de micrófono en segundo plano (Android 14/15+) | El sistema puede matar la captura | Foreground service con tipo `microphone` declarado, iniciado con la app visible; permiso `RECORD_AUDIO` "mientras se usa" |
| Doze / optimización de batería | Servicio suspendido a media noche | Solicitar exención de optimización de batería (flujo guiado), wake lock parcial, pruebas por fabricante (Samsung/Xiaomi son agresivos) |
| Consumo de batería | Usuario abandona la app | Procesamiento por ventanas, clasificación solo cuando el detector dispara, sample rate 16 kHz mono |
| Precisión del clasificador | Falsos positivos/negativos | YAMNet como base + umbral de confianza + reetiquetado manual; métrica de precisión con datos reales |
| dB no calibrados | Datos engañosos | Etiquetar "aprox.", offset de calibración, enfocarse en valores relativos y tendencias |
| Privacidad / terceros | Legal/confianza | Todo local, clips solo de eventos, retención configurable, avisos de consentimiento, sin analytics de audio |
| Almacenamiento | Llenar el teléfono | Compresión Opus/AAC, límite por noche, retención automática |

---

## 6. Criterios de éxito de la Fase 1
1. Una sesión de 8 horas corre completa sin que el sistema mate el servicio (en ≥3 fabricantes probados).
2. Consumo < 15% de batería en 8 h.
3. Los ronquidos francos se detectan y clasifican con ≥80% de precisión percibida (validado escuchando clips).
4. El usuario puede ver la noche, escuchar los eventos clave, y ver tendencias semanales.
5. Cero audio sale del dispositivo.
