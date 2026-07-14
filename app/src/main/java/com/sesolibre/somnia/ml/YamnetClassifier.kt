package com.sesolibre.somnia.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.channels.FileChannel

/**
 * Clasificador de sonidos on-device con YAMNet (LiteRT/TensorFlow Lite).
 * El modelo (~4 MB, 521 clases de AudioSet) corre 100% local; se le pasan
 * ventanas de 0.975 s y se promedian los puntajes de todas las ventanas
 * del evento.
 */
class YamnetClassifier(context: Context) : AutoCloseable {

    data class Classification(
        val label: String,
        val score: Float,
        val category: SomniaCategory,
    )

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputIsBatched: Boolean
    private val numClasses: Int

    init {
        val fd = context.assets.openFd(MODEL_ASSET)
        val model = fd.createInputStream().channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength,
        )
        interpreter = Interpreter(model)

        val inputShape = interpreter.getInputTensor(0).shape()
        inputIsBatched = inputShape.size == 2
        val outputShape = interpreter.getOutputTensor(0).shape()
        numClasses = outputShape.last()

        labels = context.assets.open(LABELS_ASSET).bufferedReader().readLines()
            .drop(1) // encabezado index,mid,display_name
            .map { parseDisplayName(it) }

        Log.i(TAG, "YAMNet listo: input=${inputShape.toList()} clases=$numClasses")
    }

    /** Clasifica el PCM de un evento; null si no hay audio suficiente. */
    fun classify(pcm: ShortArray): Classification? {
        val windows = AudioWindows.windows(pcm)
        if (windows.isEmpty()) return null

        val avgScores = FloatArray(numClasses)
        for (window in windows) {
            val scores = runWindow(window)
            for (i in avgScores.indices) avgScores[i] += scores[i] / windows.size
        }

        var topIdx = 0
        for (i in avgScores.indices) if (avgScores[i] > avgScores[topIdx]) topIdx = i
        val label = labels.getOrElse(topIdx) { "?" }
        val score = avgScores[topIdx]

        val category = if (score < MIN_CONFIDENCE) {
            SomniaCategory.OTHER
        } else {
            CategoryMapper.fromAudioSetLabel(label)
        }
        return Classification(label = label, score = score, category = category)
    }

    private fun runWindow(window: FloatArray): FloatArray {
        return if (inputIsBatched) {
            val output = Array(1) { FloatArray(numClasses) }
            interpreter.run(arrayOf(window), output)
            output[0]
        } else {
            val output = Array(1) { FloatArray(numClasses) }
            interpreter.run(window, output)
            output[0]
        }
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "YamnetClassifier"
        private const val MODEL_ASSET = "yamnet.tflite"
        private const val LABELS_ASSET = "yamnet_class_map.csv"
        const val MIN_CONFIDENCE = 0.2f

        /** `38,/m/03cczk,"Chewing, mastication"` -> `Chewing, mastication` */
        fun parseDisplayName(csvLine: String): String {
            val parts = csvLine.split(",", limit = 3)
            if (parts.size < 3) return csvLine
            return parts[2].trim().removeSurrounding("\"")
        }
    }
}
