package com.sesolibre.somnia.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory

/** Color de marca para cada categoría en gráficas (data-viz, no del tema). */
fun categoryColor(category: SomniaCategory?): Color = when (category) {
    SomniaCategory.SNORING -> Color(0xFFE4572E)   // naranja-rojo
    SomniaCategory.BREATHING -> Color(0xFF17A398) // verde-azulado
    SomniaCategory.COUGH -> Color(0xFFF3A712)     // ámbar
    SomniaCategory.SPEECH -> Color(0xFF7B6CF6)    // violeta
    SomniaCategory.MOVEMENT -> Color(0xFF4E8FD9)  // azul
    SomniaCategory.ENVIRONMENT -> Color(0xFF9AA0A6) // gris (ruido base)
    SomniaCategory.OTHER, null -> Color(0xFFBFC4C9) // gris claro
}

/**
 * Timeline de la noche: curva de dB promedio por minuto con los eventos de
 * sonido superpuestos como puntos ubicados por hora y coloreados por categoría
 * (Vista Noche v2). El ruido ambiente y lo no clasificado se dibujan más
 * tenues para que no tapen los sonidos del cuerpo.
 */
@Composable
fun NightTimeline(
    samples: List<NoiseSample>,
    events: List<SoundEvent>,
    startEpochMs: Long,
    endEpochMs: Long?,
    calibrationOffset: Double,
    height: Dp = 96.dp,
) {
    if (samples.size < 2) return
    val curveColor = MaterialTheme.colorScheme.primary
    val values = samples.map { (it.dbAvg + calibrationOffset).toFloat() }
    val minV = values.min()
    val maxV = values.max().coerceAtLeast(minV + 1f)

    // Ventana temporal: fin real de la sesión o el último minuto con muestra.
    val spanMs = ((endEpochMs ?: (startEpochMs + samples.last().minuteIndex * 60_000L))
        - startEpochMs).coerceAtLeast(1L)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 4.dp),
    ) {
        val curveTop = size.height * 0.28f // deja una banda arriba para los puntos
        val curveH = size.height - curveTop

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = (samples[i].minuteIndex * 60_000L).toFloat() / spanMs * size.width
            val y = curveTop + curveH * (1f - (v - minV) / (maxV - minV))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = curveColor, style = Stroke(width = 3f))

        events.forEach { event ->
            val category = SomniaCategory.fromKey(ApneaHeuristic.effectiveCategory(event))
            val x = (event.startEpochMs - startEpochMs).toFloat() / spanMs * size.width
            val isBody = category in NightTimelineDefaults.bodyCategories
            drawCircle(
                color = categoryColor(category),
                radius = if (isBody) 4f else 2.5f,
                center = Offset(x.coerceIn(0f, size.width), curveTop * 0.5f),
            )
        }
    }
}

private object NightTimelineDefaults {
    val bodyCategories = setOf(
        SomniaCategory.SNORING,
        SomniaCategory.BREATHING,
        SomniaCategory.COUGH,
        SomniaCategory.SPEECH,
        SomniaCategory.MOVEMENT,
    )
}
