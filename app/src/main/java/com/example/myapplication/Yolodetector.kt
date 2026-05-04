package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.*
import java.nio.FloatBuffer

private val CUSTOM_LABELS = listOf("Pants", "Shirt", "Shoe")
val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
private const val INPUT_SIZE     = 640
private val CLASS_THRESHOLDS = mapOf(
    0 to 0.87f,  // Pants — strict, lots of false positives from furniture
    1 to 0.87f,  // Shirt — strict, cupboards trigger this too
    2 to 0.80f,  // Shoe  — lenient, model is less confident on shoes
)
private const val CONF_THRESHOLD_DEFAULT = 0.82f

data class DetectionBox(
    val cx: Float, val cy: Float,
    val w: Float,  val h: Float,
    val label: String,
    val confidence: Float
)

class YoloDetector(context: Context) {
    private val env         = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val isRunning   = java.util.concurrent.atomic.AtomicBoolean(false)

    // Presence-based tracking state (replaces lastDetectedMs cooldown map)
    private var currentLabel: String? = null  // label currently being tracked
    private var lastSeenMs:   Long    = 0L    // last time a detection fired
    private var registered:   Boolean = false // has this presentation been counted

    companion object {
        private const val ABSENCE_GRACE_MS = 1500L // ms of no detection before reset
    }

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

    // Make session access synchronized
    private val sessionLock = Any()

    fun detectBoxes(bitmap: Bitmap): List<DetectionBox> {
        if (!isRunning.compareAndSet(false, true)) {
            return emptyList()
        }

        return synchronized(sessionLock) {
            val resized     = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            val inputBuf    = bitmapToFloatBuffer(resized)
            val shape       = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            val inputTensor = OnnxTensor.createTensor(env, inputBuf, shape)

            try {
                val inputName  = session.inputNames.iterator().next()
                val results    = session.run(mapOf(inputName to inputTensor))
                val rawValue   = results[0].value
                val output     = (rawValue as Array<*>)[0] as Array<*>
                val rows       = Array(output.size) { i -> output[i] as FloatArray }
                val numCols    = rows[0].size

                val detections = mutableListOf<DetectionBox>()
                for (i in 0 until numCols) {
                    val cx = rows[0][i]; val cy = rows[1][i]
                    val w  = rows[2][i]; val h  = rows[3][i]
                    var maxScore = 0f;   var maxClass = 0
                    for (c in 0 until 3) {
                        val score = rows[4 + c][i]
                        if (score > maxScore) { maxScore = score; maxClass = c }
                    }
                    val threshold = CLASS_THRESHOLDS[maxClass] ?: CONF_THRESHOLD_DEFAULT
                    if (maxScore >= threshold) {
                        detections.add(DetectionBox(
                            cx    = cx / INPUT_SIZE, cy = cy / INPUT_SIZE,
                            w     = w  / INPUT_SIZE, h  = h  / INPUT_SIZE,
                            label = CUSTOM_LABELS.getOrElse(maxClass) { "Class$maxClass" },
                            confidence = maxScore
                        ))
                    }
                }

                val nmsResult = nms(detections)

                if (nmsResult.isEmpty()) {
                    // Nothing detected this frame — check if grace period expired
                    val now = System.currentTimeMillis()
                    if (currentLabel != null && now - lastSeenMs > ABSENCE_GRACE_MS) {
                        // Object gone long enough — reset ready for next scan
                        currentLabel = null
                        registered   = false
                        Log.e("YOLO_DEBUG", "Reset — ready for next object")
                    }
                    return@synchronized emptyList()
                }

                val topBox = nmsResult.first()
                val now    = System.currentTimeMillis()
                lastSeenMs = now

                if (!registered || currentLabel != topBox.label) {
                    // New object or different object — register it
                    currentLabel = topBox.label
                    registered   = true
                    Log.e("YOLO_DEBUG", "NEW detection: ${topBox.label}@${"%.2f".format(topBox.confidence)}")
                } else {
                    // Same object still in frame — show outline but don't re-register
                    Log.e("YOLO_DEBUG", "TRACKING: ${topBox.label} still in frame")
                }
                // Always return box so overlay stays visible regardless of registration state
                listOf(topBox)
            } catch (e: Exception) {
                Log.e("YOLO_DEBUG", "Inference failed: ${e.message}", e)
                emptyList()
            } finally {
                inputTensor.close()
                isRunning.set(false)
            }
        }
    }

    fun close() {
        synchronized(sessionLock) {
            session.close()
            env.close()
        }
    }

    fun detect(bitmap: Bitmap): List<DetectedItem> {
        val counts = mutableMapOf<String, Int>()
        detectBoxes(bitmap).forEach { counts[it.label] = (counts[it.label] ?: 0) + 1 }
        return counts.map { (label, count) -> DetectedItem(label, count) }
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