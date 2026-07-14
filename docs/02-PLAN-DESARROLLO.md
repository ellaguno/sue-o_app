# Plan Detallado de Desarrollo — "Sueño" (Android)

Complementa a [01-ANALISIS-ALCANCES.md](01-ANALISIS-ALCANCES.md).

---

## 1. Stack técnico

| Área | Elección | Razón |
|---|---|---|
| Lenguaje | Kotlin | Estándar Android actual |
| UI | Jetpack Compose + Material 3 | Productividad, theming oscuro nativo (app nocturna) |
| Arquitectura | MVVM + capas (ui / domain / data) con Hilt | Testeable, escalable a Fase 2 |
| Captura de audio | `AudioRecord` @ 16 kHz mono PCM 16-bit | Suficiente para clasificación y dB; bajo consumo |
| Clasificación | LiteRT (TensorFlow Lite) + modelo **YAMNet** | On-device, ~4 MB, clases de AudioSet incluyen ronquido/tos/habla |
| Compresión de clips | `MediaCodec` → AAC en contenedor M4A (u Opus/OGG) | Nativo, sin dependencias |
| Persistencia | Room (SQLite) + DataStore (preferencias) | Consultas para estadísticas |
| Gráficas | Vico (charts para Compose) | Ligera, mantenida |
| Servicio nocturno | Foreground Service `type=microphone` + WakeLock parcial | Único camino soportado en Android 14+ |
| PDF export | `android.graphics.pdf.PdfDocument` | Nativo |
| Fase 2 | Health Connect SDK (`androidx.health.connect`) | Lectura de sesiones/etapas de sueño de relojes |
| Min SDK | 26 (Android 8.0) — target SDK más reciente | Cubre ~97% de dispositivos |
| CI | GitHub Actions: build + lint + unit tests en cada PR | El repo ya está en GitHub |

## 2. Estructura del proyecto

```
sue-o_app/
├── docs/                        # estos documentos
├── app/src/main/java/com/sesolibre/sueno/
│   ├── ui/                      # Compose: pantallas y ViewModels
│   │   ├── home/                # inicio/detener sesión, estado
│   │   ├── night/               # detalle de una noche (timeline + clips)
│   │   ├── stats/               # tendencias
│   │   ├── profile/             # perfil, acompañantes, cuestionarios
│   │   └── settings/            # sensibilidad, retención, calibración
│   ├── domain/                  # modelos y casos de uso puros
│   ├── data/
│   │   ├── db/                  # Room: entidades, DAOs
│   │   ├── audio/               # AudioRecord, detector, dB, encoder
│   │   └── ml/                  # YAMNet wrapper, mapeo de clases
│   └── service/                 # MonitorForegroundService
├── app/src/test/                # unit tests (detector, dB, mapeo, stats)
└── .github/workflows/ci.yml
```

## 3. Modelo de datos (Room)

```
UserProfile   (id, nombre, edad, sexo, pesoKg, estaturaCm, duermeSolo,
               notasCondiciones, epworthScore?, stopBangScore?)
Companion     (id, alias)                          -- acompañantes de cuarto
Session       (id, inicio, fin, duracionMin, calibracionDbOffset,
               bateriaInicio, bateriaFin, notas)
SleepLog      (sessionId, cafeina, alcohol, ejercicio, cenaTardia,
               estres1a5, posturaConocida?, calidadDespertar1a5)
SoundEvent    (id, sessionId, tsInicio, tsFin, duracionMs,
               dbPico, dbPromedio, clase, confianza,
               clipPath?, atribuidoA? -> Companion, etiquetaManual?)
NoiseSample   (sessionId, minutoIndex, dbMin, dbProm, dbMax)  -- serie 1/min
SleepStage    (sessionId, tsInicio, tsFin, etapa, fuente)     -- Fase 2 (Health Connect)
```

## 4. Pipeline de audio (corazón de la app)

```
AudioRecord 16kHz mono
   └─> buffer circular en RAM (ventanas de 975 ms, el frame de YAMNet)
        ├─> [cada ~1 s]  RMS → dBFS (+offset) → acumulador NoiseSample
        ├─> [detector]   umbral adaptativo: media móvil del ruido de fondo
        │                + margen (sensibilidad configurable); histéresis
        │                para abrir/cerrar evento (mín 0.5 s, máx 60 s)
        └─> [al abrir evento]
              ├─> clasificar ventanas con YAMNet → clase dominante + confianza
              ├─> retener PCM del evento (+1 s de pre-roll del buffer circular)
              └─> [al cerrar] encodear AAC → clip, guardar SoundEvent
```

Decisiones clave:
- **YAMNet solo corre cuando hay evento abierto** → CPU casi nula en silencio.
- Pre-roll de 1 s tomado del buffer circular para no cortar el inicio del ronquido.
- Ronquidos consecutivos separados < 5 s se fusionan en un solo evento "episodio".
- Todo el pipeline es una clase pura testeable con PCM sintético (sin Android).

## 5. Etapas de desarrollo

### Etapa 0 — Fundación (½–1 día)
- [ ] Repo: `git init`, README, licencia, `.gitignore` Android, estos docs. Push inicial.
- [ ] Proyecto Android Studio: Compose, Hilt, Room, minSdk 26. CI en GitHub Actions.
- **Criterio:** el repo compila un APK "hola mundo" en CI.

### Etapa 1 — Captura nocturna confiable (1–2 semanas)
- [ ] Permisos: `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MICROPHONE`; flujo guiado para exención de optimización de batería.
- [ ] `MonitorForegroundService`: notificación persistente con estado (tiempo, eventos, dB actual), botón detener.
- [ ] Cálculo de dB + `NoiseSample` por minuto en Room.
- [ ] Pantalla Home: iniciar/detener sesión, estado en vivo (dB actual, duración).
- [ ] **Prueba de fuego:** 8 h corriendo en ≥2 teléfonos reales sin morir; medir batería.
- **Criterio:** una noche completa registrada con su curva de ruido.

### Etapa 2 — Detección y almacenamiento de eventos (1–2 semanas)
- [ ] Buffer circular + detector adaptativo con histéresis (unit tests con PCM sintético).
- [ ] Encoder AAC de clips con pre-roll; escritura en almacenamiento interno de la app.
- [ ] Entidad `SoundEvent`; pantalla "Noche" v1: lista de eventos con reproductor.
- [ ] Retención configurable + límite de clips por noche.
- **Criterio:** dormir una noche y al despertar escuchar los eventos detectados.

### Etapa 3 — Clasificación (1–2 semanas)
- [ ] Integrar LiteRT + YAMNet; wrapper con mapeo AudioSet → categorías propias.
- [ ] Clasificación en vivo durante eventos; guardar clase + confianza.
- [ ] Reetiquetado manual en la UI (corrige y alimenta métricas de precisión).
- [ ] Heurística "patrón sospechoso de apnea" (ronquido→silencio≥10s→gasp) con wording cuidadoso.
- **Criterio:** ≥80% de los clips de ronquido franco quedan bien clasificados (validación manual).

### Etapa 4 — Perfil, acompañantes y bitácora (1 semana)
- [ ] Perfil de usuario + cuestionarios Epworth / STOP-Bang.
- [ ] Acompañantes: alta, aviso de consentimiento, disclaimer "los sonidos pueden venir de otras personas" en reportes cuando `duermeSolo=false`, atribución manual de eventos/noches.
- [ ] Bitácora rápida por noche (chips: cafeína, alcohol, ejercicio…) al iniciar o al despertar.
- **Criterio:** reportes reflejan el contexto multi-persona y la bitácora.

### Etapa 5 — Estadísticas y reportes (1–2 semanas)
- [ ] Vista Noche v2: timeline de eventos superpuesta a la curva de dB, resumen (episodios, minutos roncando, pico).
- [ ] Tendencias: semana/mes, correlaciones simples con bitácora.
- [ ] "Lo más relevante de anoche" (top clips) + recomendaciones de higiene del sueño basadas en reglas.
- [ ] Exportar PDF/CSV de una noche o rango.
- **Criterio:** con 2+ semanas de datos, la app cuenta una historia útil y exportable.

### Etapa 6 — Endurecimiento y release (1 semana)
- [ ] Pruebas en fabricantes agresivos (Samsung, Xiaomi); guía in-app por fabricante si aplica.
- [ ] Pulido de consumo (perfilar con Battery Historian), accesibilidad, tema oscuro por defecto.
- [ ] Onboarding con privacidad y descargo médico explícitos.
- [ ] Release firmado (APK en GitHub Releases; Play Store opcional después — requiere justificar micrófono en la revisión).

### Etapa 7 — Fase 2: relojes inteligentes (2–3 semanas, posterior)
- [ ] Integrar Health Connect: leer `SleepSessionRecord` + etapas.
- [ ] Superponer etapas de sueño en el timeline; correlaciones sonido↔etapa y sonido↔despertares.
- [ ] Enriquecer recomendaciones con datos de etapas.

**Total estimado Fase 1: ~6–9 semanas** de trabajo efectivo (una persona, con noches reales de prueba entre etapas — el ciclo de validación es literalmente dormir).

## 6. Estrategia de pruebas
- **Unit:** detector de eventos, cálculo de dB, mapeo de clases, fusión de episodios, agregaciones de estadísticas — todo con PCM/datos sintéticos, sin emulador.
- **Instrumentadas:** Room y ciclo de vida del servicio.
- **De campo:** matriz de noches reales (solo / acompañado / ventilador / ventana abierta) registrando falsos positivos.
- **Datasets públicos** para validar clasificación sin esperar noches: AudioSet (clips de snoring), ESC-50 (tos, ruidos).

## 7. Decisiones abiertas (para decidir sobre la marcha)
1. **Opus vs AAC** para clips (Opus comprime mejor a 16 kHz; AAC es más simple con MediaCodec). Se decide en Etapa 2 con pruebas.
2. **Nombre y branding** de la app.
3. **Licencia** del repo (¿MIT? ¿GPL? — YAMNet es Apache 2.0, compatible con ambas).
4. Play Store vs distribución directa por APK.
