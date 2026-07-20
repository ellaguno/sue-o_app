package com.sesolibre.somnia.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gráfica de barras simple para una serie por noche (p. ej. minutos roncando).
 * Las barras se dibujan de izquierda (más antigua) a derecha (más reciente).
 */
@Composable
fun TrendBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 120.dp,
) {
    if (values.isEmpty()) return
    val maxV = values.max().coerceAtLeast(0.001f)
    val baseline = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 4.dp),
    ) {
        val n = values.size
        val slot = size.width / n
        val barW = slot * 0.6f
        val gap = (slot - barW) / 2f
        values.forEachIndexed { i, v ->
            val h = size.height * (v / maxV)
            val x = i * slot + gap
            // pista de fondo
            drawRect(
                color = baseline,
                topLeft = Offset(x, 0f),
                size = Size(barW, size.height),
            )
            drawRect(
                color = barColor,
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
            )
        }
    }
}
