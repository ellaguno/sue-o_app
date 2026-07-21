package com.sesolibre.somnia.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gráfica de barras simple para una serie por noche (p. ej. minutos roncando).
 * Las barras se dibujan de izquierda (más antigua) a derecha (más reciente).
 *
 * Si se pasan [labels] (una por valor), se dibuja una etiqueta bajo cada barra
 * para dejar claro qué representa —normalmente la fecha de esa noche—. Para no
 * saturar el eje cuando hay muchas noches, solo se muestran algunas etiquetas
 * espaciadas de forma pareja.
 */
@Composable
fun TrendBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    barColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 120.dp,
) {
    if (values.isEmpty()) return
    val maxV = values.max().coerceAtLeast(0.001f)
    val baseline = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
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
        if (labels.isNotEmpty() && labels.size == values.size) {
            // Muestra como mucho ~7 etiquetas para que no se encimen.
            val step = ((labels.size + 6) / 7).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                labels.forEachIndexed { i, label ->
                    Text(
                        text = if (i % step == 0) label else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
