#!/usr/bin/env python3
"""Genera Localizable.xcstrings e InfoPlist.xcstrings (inglés -> español).

La clave es el texto en inglés tal como lo arma SwiftUI (las interpolaciones
quedan como %lld / %@ / %.1f y un % literal como %%). Al agregar un texto en
la app, añádelo aquí y vuelve a correr: python3 ios/scripts/gen_strings.py
"""
import json
import pathlib

ES = {
    # Inicio
    "View trends": "Ver tendencias",
    "Recorded nights": "Noches registradas",
    "Settings": "Ajustes",
    "Microphone blocked": "Micrófono bloqueado",
    "Open settings": "Abrir ajustes",
    "Cancel": "Cancelar",
    "OK": "OK",
    "To monitor your sleep, allow microphone access for Somnia in Settings. Audio is processed only on your phone.":
        "Para monitorear tu sueño, permite el acceso al micrófono para Somnia en Ajustes. El audio se procesa solo en tu teléfono.",
    "Monitoring": "Monitoreo activo",
    "Estimate: the phone’s microphone isn’t calibrated.": "Estimado: el micrófono del teléfono no está calibrado.",
    "Stop": "Detener",
    "Listen": "Escuchar",
    "Ready to monitor": "Listo para monitorear",
    "Start when you go to bed. Audio is processed on your phone; nothing leaves the device.":
        "Inicia al acostarte. El audio se procesa en tu teléfono; nada sale del dispositivo.",
    "Start monitoring": "Iniciar monitoreo",
    "in progress": "en curso",
    "%lld dB (approx.)": "%lld dB (aprox.)",
    "%lld min recorded · %lld events detected": "%lld min registrados · %lld eventos detectados",
    "%lld events · snoring: %lld (%.1f min)": "%lld eventos · ronquido: %lld (%.1f min)",
    "%lld events · no snoring": "%lld eventos · sin ronquidos",
    # Noche
    "Duration: %@ · %lld sound events": "Duración: %@ · %lld eventos de sonido",
    "Snoring: %lld events · %.1f min": "Ronquidos: %lld eventos · %.1f min",
    "Battery: %lld%% → %lld%%": "Batería: %lld%% → %lld%%",
    "Body sounds": "Sonidos del cuerpo",
    "Ambient: %lld · Other: %lld": "Ambiente: %lld · Otros: %lld",
    "Pause pattern observed": "Patrón de pausas observado",
    "%lld snoring → long pause → abrupt resumption sequences were observed. This is NOT a diagnosis; if it happens often, talk to a doctor.":
        "Se observaron %lld secuencias de ronquido → pausa larga → reanudación brusca. Esto NO es un diagnóstico; si se repite con frecuencia, coméntalo con un médico.",
    "No sound events were detected in this session.": "No se detectaron eventos de sonido en esta sesión.",
    "Events (%lld of %lld)": "Eventos (%lld de %lld)",
    "Show all": "Ver todos",
    "Hide “Other”": "Ocultar «Otro»",
    "Show “Other”": "Mostrar «Otro»",
    "Delete session": "Eliminar sesión",
    "More options": "Más opciones",
    "Delete this night?": "¿Eliminar esta noche?",
    "The session and its audio clips will be deleted. This can’t be undone.":
        "Se borrarán la sesión y sus clips de audio. No se puede deshacer.",
    "Couldn’t play this clip (the file may be damaged).": "No se pudo reproducir este clip (el archivo puede estar dañado).",
    "No body sounds classified tonight.": "Sin sonidos del cuerpo clasificados esta noche.",
    "%.1f s · peak %lld dB (approx.)": "%.1f s · pico %lld dB (aprox.)",
    "no clip": "sin clip",
    "Time": "Hora", "Min": "Mín", "Max": "Máx", "Night": "Noche", "Minutes": "Minutos", "Events": "Eventos",
    # Categorías
    "Snoring": "Ronquido", "Breathing": "Respiración", "Cough": "Tos", "Speech": "Habla",
    "Movement": "Movimiento", "Ambient": "Ambiente", "Other": "Otro",
    # Tendencias
    "Trends": "Tendencias",
    "Not enough nights yet. Record a few nights to see your trends.":
        "Aún no hay suficientes noches. Registra varias noches para ver tus tendencias.",
    "Summary": "Resumen",
    "Average snoring: %.1f min/night": "Ronquido promedio: %.1f min/noche",
    "Body sounds: %.1f per night": "Sonidos del cuerpo: %.1f por noche",
    "Average duration: %@": "Duración promedio: %@",
    "Pause pattern in %lld of %lld nights": "Patrón de pausas en %lld de %lld noches",
    "Minutes snoring per night": "Minutos roncando por noche",
    "Body sounds per night": "Sonidos del cuerpo por noche",
    # Ajustes
    "+%lld dB above background noise": "+%lld dB sobre el ruido de fondo",
    "← more sensitive (more events) · less sensitive (loud sounds only) →":
        "← más sensible (más eventos) · menos sensible (solo sonidos fuertes) →",
    "Reset to the recommended value (+12 dB)": "Restablecer al valor recomendado (+12 dB)",
    "Detection sensitivity": "Sensibilidad de detección",
    "An event is recorded when sound rises above the background noise by this margin. If you sleep somewhere noisy and too many events are recorded, raise it; if soft snores are missed, lower it. Applies from the next session.":
        "Un evento se registra cuando el sonido supera el ruido de fondo por este margen. Si duermes en un lugar ruidoso y se registran demasiados eventos, sube el valor; si se pierden ronquidos suaves, bájalo. Aplica a partir de la siguiente sesión.",
    "Your privacy": "Tu privacidad",
    "The whole night isn’t recorded: only short clips of each detected sound. Everything is analyzed and stored on your phone; audio isn’t uploaded to any server or included in backups.":
        "No se graba la noche completa: solo clips cortos de cada sonido detectado. Todo se analiza y guarda en tu teléfono; el audio no se sube a ningún servidor ni se incluye en respaldos.",
    "Somnia guides and documents, but doesn’t diagnose. If you notice breathing pauses or a lot of daytime tiredness, talk to a doctor.":
        "Somnia orienta y documenta, pero no diagnostica. Si notas pausas al respirar o mucho cansancio de día, coméntalo con un médico.",
    # Bienvenida
    "Welcome to Somnia": "Bienvenido a Somnia",
    "Somnia listens while you sleep, detects snoring, coughing, speech and movement, and shows you how your night went. Before you start:":
        "Somnia escucha mientras duermes, detecta ronquidos, tos, habla y movimiento, y te muestra cómo fue tu noche. Antes de empezar:",
    "Where to put the phone": "Dónde poner el teléfono",
    "On the nightstand, 30–60 cm (1–2 ft) from your head, with the microphone (bottom edge) facing you. Keep it plugged into the charger and don’t put it under the pillow.":
        "En el buró o mesita, a 30–60 cm de tu cabeza, con el micrófono (borde inferior) hacia ti. Déjalo conectado al cargador y no lo pongas bajo la almohada.",
    "Keep it running all night": "Que no se detenga a media noche",
    "Start monitoring and lock the phone: Somnia keeps listening in the background. Avoid closing it from the app switcher.":
        "Inicia el monitoreo y bloquea el teléfono: Somnia sigue escuchando en segundo plano. Evita cerrarla desde el selector de apps.",
    "Not a medical device": "No es un dispositivo médico",
    "Get started": "Empezar",
}

# Claves de un solo número con singular/plural en ambos idiomas.
PLURALS = {
    "%lld nights recorded": (("%lld night recorded", "%lld nights recorded"),
                             ("%lld noche registrada", "%lld noches registradas")),
}

INFO_PLIST = {
    "NSMicrophoneUsageDescription": (
        "Somnia listens while you sleep to detect snoring and other sounds. Audio is processed only on your phone.",
        "Somnia escucha mientras duermes para detectar ronquidos y otros sonidos. El audio se procesa solo en tu teléfono.",
    ),
    "CFBundleDisplayName": ("Somnia", "Somnia"),
}


def unit(value):
    return {"stringUnit": {"state": "translated", "value": value}}


def plural(one, other):
    return {"variations": {"plural": {"one": unit(one), "other": unit(other)}}}


def catalog(strings):
    return {"sourceLanguage": "en", "strings": strings, "version": "1.0"}


here = pathlib.Path(__file__).resolve().parent.parent / "Somnia" / "Resources"
strings = {k: {"localizations": {"es": unit(v)}} for k, v in sorted(ES.items())}
for key, ((en1, en2), (es1, es2)) in PLURALS.items():
    strings[key] = {"localizations": {"en": plural(en1, en2), "es": plural(es1, es2)}}
(here / "Localizable.xcstrings").write_text(json.dumps(catalog(strings), ensure_ascii=False, indent=2) + "\n")

info = {k: {"localizations": {"en": unit(en), "es": unit(es)}} for k, (en, es) in INFO_PLIST.items()}
(here / "InfoPlist.xcstrings").write_text(json.dumps(catalog(info), ensure_ascii=False, indent=2) + "\n")
print(f"{len(strings)} textos, {len(info)} de Info.plist")
