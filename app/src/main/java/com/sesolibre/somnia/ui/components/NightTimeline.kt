package com.sesolibre.somnia.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.data.db.SoundPlay
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.util.LocalDates
import java.time.Instant

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
 * sonido como puntos de su color, y un eje de horas.
 *
 * Interactiva:
 * - **Tocar** marca un momento y llama a [onSelect] con la hora y la tolerancia
 *   (unos dedos de ancho, en ms) para buscar el evento más cercano.
 * - **Pellizcar** o **tocar dos veces** acerca alrededor del dedo; con zoom,
 *   arrastrar a los lados recorre la noche. Un solo dedo en vertical sigue
 *   desplazando la pantalla.
 */
@Composable
fun NightTimeline(
    samples: List<NoiseSample>,
    events: List<SoundEvent>,
    startEpochMs: Long,
    endEpochMs: Long?,
    calibrationOffset: Double,
    height: Dp = 150.dp,
    /** Veces que sonó el sonido suave: se marcan como franjas (sin detección). */
    soundPlays: List<SoundPlay> = emptyList(),
    selectedMs: Long? = null,
    onSelect: (timeMs: Long, toleranceMs: Long) -> Unit = { _, _ -> },
) {
    if (samples.size < 2) return
    val context = LocalContext.current
    val curveColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    val density = LocalDensity.current
    val tapTolerancePx = with(density) { 14.dp.toPx() }
    val labelBandPx = with(density) { 16.dp.toPx() }
    val dotBandPx = with(density) { 16.dp.toPx() }

    // Ventana temporal: fin real de la sesión o el último minuto con muestra.
    val spanMs = ((endEpochMs ?: (startEpochMs + (samples.last().minuteIndex + 1) * 60_000L))
        - startEpochMs).coerceAtLeast(60_000L)
    // Lo más que se puede acercar: unos 5 minutos de noche en todo el ancho.
    val minWidth = (5 * 60_000f / spanMs).coerceAtMost(1f)

    var window by remember(startEpochMs) { mutableStateOf(TimelineWindow.FULL) }
    val hourFmt = remember(context) { LocalDates.hour(context) }
    val minuteFmt = remember(context) { LocalDates.time(context) }

    fun msAtScreen(xPx: Float, widthPx: Float): Long =
        startEpochMs + (window.fromScreen((xPx / widthPx).coerceIn(0f, 1f)) * spanMs).toLong()

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(vertical = 4.dp)
                // Pellizco (dos dedos): zoom + desplazamiento. Solo se consume con
                // dos dedos, para que un dedo siga desplazando la lista.
                .pointerInput(spanMs) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (zoom != 1f || pan.x != 0f) {
                                    window = window
                                        .zoom(zoom, (centroid.x / size.width).coerceIn(0f, 1f), minWidth)
                                        .pan(pan.x / size.width)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                // Con zoom, arrastrar a los lados recorre la noche.
                .pointerInput(spanMs) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        if (window.isZoomed) {
                            window = window.pan(dragAmount / size.width)
                            change.consume()
                        }
                    }
                }
                .pointerInput(spanMs) {
                    detectTapGestures(
                        onTap = { offset ->
                            val toleranceMs = (tapTolerancePx / size.width * window.width * spanMs).toLong()
                            onSelect(msAtScreen(offset.x, size.width.toFloat()), toleranceMs)
                        },
                        onDoubleTap = { offset ->
                            val anchor = (offset.x / size.width).coerceIn(0f, 1f)
                            window = if (window.width > 0.2f) window.zoom(3f, anchor, minWidth) else TimelineWindow.FULL
                        },
                    )
                },
        ) {
            val plotTop = dotBandPx
            val plotBottom = size.height - labelBandPx
            val plotH = plotBottom - plotTop
            fun xOf(epochMs: Long): Float =
                window.toScreen((epochMs - startEpochMs).toFloat() / spanMs) * size.width

            // Escala vertical según lo que se ve (al acercar se aprecian los detalles).
            val visStart = startEpochMs + (window.start * spanMs).toLong()
            val visEnd = startEpochMs + (window.end * spanMs).toLong()
            val visible = samples.filter {
                val t = startEpochMs + it.minuteIndex * 60_000L
                t in (visStart - 60_000L)..(visEnd + 60_000L)
            }.ifEmpty { samples }
            val values = visible.map { (it.dbAvg + calibrationOffset).toFloat() }
            val minV = values.min() - 1f
            val maxV = (values.max() + 1f).coerceAtLeast(minV + 2f)
            fun yOf(db: Float) = plotTop + plotH * (1f - (db - minV) / (maxV - minV))

            // Marcas de hora (o de minutos al acercar).
            val stepMin = TimelineWindow.tickStepMinutes(visEnd - visStart)
            val stepMs = stepMin * 60_000L
            val zoneOffset = java.util.TimeZone.getDefault().getOffset(visStart).toLong()
            var tick = Math.floorDiv(visStart + zoneOffset, stepMs) * stepMs - zoneOffset + stepMs
            var lastLabelEnd = Float.NEGATIVE_INFINITY
            val labelGap = 8f
            while (tick < visEnd) {
                val x = xOf(tick)
                drawLine(gridColor, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1f)
                val formatter = if (stepMin >= 60) hourFmt else minuteFmt
                val layout = textMeasurer.measure(formatter.format(Instant.ofEpochMilli(tick)), labelStyle)
                val lx = (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                // Con formato de 12 h las etiquetas son largas: si no cabe, se omite
                // (la línea de la marca sí se dibuja).
                if (lx >= lastLabelEnd + labelGap) {
                    drawText(layout, topLeft = Offset(lx, plotBottom + 2f))
                    lastLabelEnd = lx + layout.size.width
                }
                tick += stepMs
            }

            clipRect(0f, 0f, size.width, plotBottom) {
                soundPlays.forEach { play ->
                    val end = play.endEpochMs ?: (play.startEpochMs + 10 * 60_000L)
                    val x0 = xOf(play.startEpochMs)
                    val x1 = xOf(end)
                    drawRect(
                        color = SoundBandColor,
                        topLeft = Offset(x0, 0f),
                        size = Size((x1 - x0).coerceAtLeast(2f), plotBottom),
                    )
                }

                val path = Path()
                samples.forEachIndexed { i, s ->
                    val x = xOf(startEpochMs + s.minuteIndex * 60_000L)
                    val y = yOf((s.dbAvg + calibrationOffset).toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = curveColor, style = Stroke(width = 3f))

                events.forEach { event ->
                    val category = SomniaCategory.fromKey(ApneaHeuristic.effectiveCategory(event))
                    val isBody = category in NightTimelineDefaults.bodyCategories
                    drawCircle(
                        color = categoryColor(category),
                        radius = if (isBody) 4.5f else 2.5f,
                        center = Offset(xOf(event.startEpochMs), dotBandPx / 2f),
                    )
                }

                // Momento elegido: línea punteada y punto sobre la curva.
                selectedMs?.let { t ->
                    val x = xOf(t)
                    drawLine(
                        axisColor, Offset(x, 0f), Offset(x, plotBottom), strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    )
                    val minute = ((t - startEpochMs) / 60_000L).toInt()
                    samples.minByOrNull { kotlin.math.abs(it.minuteIndex - minute) }?.let { s ->
                        drawCircle(curveColor, radius = 6f, center = Offset(x, yOf((s.dbAvg + calibrationOffset).toFloat())))
                    }
                }
            }
        }

        if (window.isZoomed) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val from = startEpochMs + (window.start * spanMs).toLong()
                val to = startEpochMs + (window.end * spanMs).toLong()
                Text(
                    stringResource(
                        R.string.timeline_range,
                        minuteFmt.format(Instant.ofEpochMilli(from)),
                        minuteFmt.format(Instant.ofEpochMilli(to)),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = axisColor,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { window = TimelineWindow.FULL }) {
                    Text(stringResource(R.string.timeline_reset))
                }
            }
        }
    }
}

/** Franja de los minutos en que sonó el sonido suave (morado translúcido). */
val SoundBandColor = Color(0x407B6CF6)

private object NightTimelineDefaults {
    val bodyCategories = setOf(
        SomniaCategory.SNORING,
        SomniaCategory.BREATHING,
        SomniaCategory.COUGH,
        SomniaCategory.SPEECH,
        SomniaCategory.MOVEMENT,
    )
}
