package com.sesolibre.somnia.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sesolibre.somnia.data.db.NoiseSample

/** Curva simple de dB promedio por minuto (gráficas completas llegan en Etapa 5). */
@Composable
fun NoiseSparkline(
    samples: List<NoiseSample>,
    calibrationOffset: Double,
    height: Dp = 72.dp,
) {
    if (samples.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    val values = samples.map { (it.dbAvg + calibrationOffset).toFloat() }
    val minV = values.min()
    val maxV = values.max().coerceAtLeast(minV + 1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 4.dp),
    ) {
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height * (1f - (v - minV) / (maxV - minV))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 3f))
    }
}
