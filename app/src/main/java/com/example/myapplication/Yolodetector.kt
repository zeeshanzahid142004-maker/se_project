package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.*
import java.nio.FloatBuffer

private val CUSTOM_LABELS = listOf(
    "Jacket", "Jeans", "Jogger", "Polo", "Shirt",
    "Short", "T-Shirt", "Trouser", "shoe"
)
val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
private const val INPUT_SIZE     = 640
private const val CONF_THRESHOLD = 0.35f

data class DetectionBox(
    val cx: Float, val cy: Float,
    val w: Float,  val h: Float,
    val label: String,
    val confidence: Float
)

class YoloDetector(context: Context) {

    private val env         = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private fun nms(boxes: List<DetectionBox>, iouThreshold: Float = 0.45f): List<DetectionBox> {
        val sorted     = boxes.sortedByDescending { it.confidence }.toMutableList()
        val kept       = mutableListOf<DetectionBox>()
        val suppressed = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            kept.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (!suppressed[j] && iou(sorted[i], sorted[j]) > iouThreshold)
                    suppressed[j] = true
            }
        }
        return kept.take(1)
    }

    private fun iou(a: DetectionBox, b: DetectionBox): Float {
        val ax1 = a.cx - a.w / 2f; val ay1 = a.cy - a.h / 2f
        val ax2 = a.cx + a.w / 2f; val ay2 = a.cy + a.h / 2f
        val bx1 = b.cx - b.w / 2f; val by1 = b.cy - b.h / 2f
        val bx2 = b.cx + b.w / 2f; val by2 = b.cy + b.h / 2f
        val interW = (minOf(ax2, bx2) - maxOf(ax1, bx1)).coerceAtLeast(0f)
        val interH = (minOf(ay2, by2) - maxOf(ay1, by1)).coerceAtLeast(0f)
        val inter  = interW * interH
        val union  = a.w * a.h + b.w * b.h - inter
        return if (union <= 0f) 0f else inter / union
    }
    init {
        val modelBytes = context.assets.open("best.onnx").readBytes()
        session = env.createSession(modelBytes, OrtSession.SessionOptions())
        Log.e("YOLO_DEBUG", "✅ ONNX model loaded")
        Log.e("YOLO_DEBUG", "Inputs:  ${session.inputNames}")
        Log.e("YOLO_DEBUG", "Outputs: ${session.outputNames}")
    }

    fun detectBoxes(bitmap: Bitmap): List<DetectionBox> {
        val resized     = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuf    = bitmapToFloatBuffer(resized)
        val shape       = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val inputTensor = OnnxTensor.createTensor(env, inputBuf, shape)

        return try {
            val inputName = session.inputNames.iterator().next()
            val results   = session.run(mapOf(inputName to inputTensor))

            // ONNX Runtime returns float[][] for shape [1, 13, 8400]
            // Cast correctly — it's a 2D float array after removing batch dim
            val rawValue = results[0].value

            // Print type to confirm structure
            Log.e("YOLO_DEBUG", "Output type: ${rawValue?.javaClass?.name}")

            // Shape [1, 13, 8400] comes back as Array<Array<FloatArray>>
            val output = (rawValue as Array<*>)[0] as Array<*>  // shape [13, 8400]
            val numRows = output.size                            // should be 13
            val numCols = (output[0] as FloatArray).size        // should be 8400

            Log.e("YOLO_DEBUG", "Parsed shape: rows=$numRows cols=$numCols")

            val rows = Array(numRows) { i -> output[i] as FloatArray }

            val detections = mutableListOf<DetectionBox>()
            for (i in 0 until numCols) {
                val cx = rows[0][i]
                val cy = rows[1][i]
                val w  = rows[2][i]
                val h  = rows[3][i]

                // Find best class score across 9 classes (rows 4..12)
                var maxScore = 0f
                var maxClass = 0
                for (c in 0 until 9) {
                    val score = rows[4 + c][i]
                    if (score > maxScore) { maxScore = score; maxClass = c }
                }
// Add right after the maxScore loop, before the if (maxScore >= CONF_THRESHOLD) check:
                if (i % 1000 == 0) { // only print every 1000th box to avoid spam
                    val scores = (0 until 9).map { c -> "${CUSTOM_LABELS[c]}=${"%.2f".format(rows[4 + c][i])}" }
                    Log.e("YOLO_DEBUG", "Box[$i] scores: $scores")
                }
                if (maxScore >= CONF_THRESHOLD) {
                    detections.add(DetectionBox(
                        cx         = cx / INPUT_SIZE,
                        cy         = cy / INPUT_SIZE,
                        w          = w  / INPUT_SIZE,
                        h          = h  / INPUT_SIZE,
                        label      = CUSTOM_LABELS.getOrElse(maxClass) { "Class$maxClass" },
                        confidence = maxScore
                    ))
                }
            }

            val result = nms(detections)
            Log.e("YOLO_DEBUG", "After NMS: ${result.map { "${it.label}@${"%.2f".format(it.confidence)}" }}")
            result

        } catch (e: Exception) {
            Log.e("YOLO_DEBUG", "❌ Inference failed: ${e.message}", e)
            emptyList()
        } finally {
            inputTensor.close()
        }
    }

    fun detect(bitmap: Bitmap): List<DetectedItem> {
        val counts = mutableMapOf<String, Int>()
        detectBoxes(bitmap).forEach { counts[it.label] = (counts[it.label] ?: 0) + 1 }
        return counts.map { (label, count) -> DetectedItem(label, count) }
    }

    fun close() {
        session.close()
        env.close()
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val buf  = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        val size = INPUT_SIZE * INPUT_SIZE

        // NCHW: R plane, G plane, B plane — normalized 0..1
        // This matches Ultralytics YOLO training preprocessing exactly
        for (i in 0 until size) {
            buf.put(i,            ((pixels[i] shr 16) and 0xFF) / 255f) // R
            buf.put(i + size,     ((pixels[i] shr 8)  and 0xFF) / 255f) // G
            buf.put(i + size * 2,  (pixels[i]          and 0xFF) / 255f) // B
        }
        buf.rewind()
        return buf
    }
}