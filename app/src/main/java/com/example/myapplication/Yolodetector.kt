package com.example.myapplication

// ─────────────────────────────────────────────────────────────────────────────
//  YoloDetector.kt
//  Runs YOLOv8n float32 TFLite model on a cropped Bitmap.
//  Returns a list of DetectedItem (label + count) for items above threshold,
//  or a list of DetectionBox for per-detection bounding boxes.
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * A single detection with normalized bounding box coordinates [0,1] relative
 * to the bitmap that was passed to [YoloDetector.detectBoxes], plus the
 * friendly label and confidence score.
 */
data class DetectionBox(
    /** Normalized center-x in [0, 1] relative to the input bitmap width. */
    val cx: Float,
    /** Normalized center-y in [0, 1] relative to the input bitmap height. */
    val cy: Float,
    /** Normalized box width in [0, 1] relative to the input bitmap width. */
    val w: Float,
    /** Normalized box height in [0, 1] relative to the input bitmap height. */
    val h: Float,
    /** Human-readable label (may be remapped via [LABEL_MAP]). */
    val label: String,
    /** Detection confidence in [0, 1]. */
    val confidence: Float
)

// COCO class names — YOLOv8n outputs 80 classes in this order
private val COCO_LABELS = listOf(
    "person","bicycle","car","motorcycle","airplane","bus","train","truck","boat",
    "traffic light","fire hydrant","stop sign","parking meter","bench","bird","cat",
    "dog","horse","sheep","cow","elephant","bear","zebra","giraffe","backpack",
    "umbrella","handbag","tie","suitcase","frisbee","skis","snowboard","sports ball",
    "kite","baseball bat","baseball glove","skateboard","surfboard","tennis racket",
    "bottle","wine glass","cup","fork","knife","spoon","bowl","banana","apple",
    "sandwich","orange","broccoli","carrot","hot dog","pizza","donut","cake","chair",
    "couch","potted plant","bed","dining table","toilet","tv","laptop","mouse",
    "remote","keyboard","cell phone","microwave","oven","toaster","sink",
    "refrigerator","book","clock","vase","scissors","teddy bear","hair drier",
    "toothbrush"
)

// Map COCO classes we care about to friendly names
// Extend this once you swap in your fine-tuned model
private val LABEL_MAP = mapOf(
    "tie"        to "Shirt",   // closest COCO proxy for clothing
    "suitcase"   to "Pants",
    "backpack"   to "Shoes",
    "handbag"    to "Bag",
    "person"     to "Person",
    "cell phone" to "Phone",
    "bottle"     to "Bottle",
    "cup"        to "Cup",
    "book"       to "Book",
    "laptop"     to "Laptop"
)

private const val INPUT_SIZE   = 640        // YOLOv8n input: 640×640
private const val CONF_THRESHOLD = 0.35f   // minimum confidence to count detection
private const val IOU_THRESHOLD  = 0.45f   // NMS IoU threshold
private const val MAX_DETECTIONS = 8400    // YOLOv8n output boxes

class YoloDetector(context: Context) {

    private lateinit var interpreter: Interpreter

    init {
        try {
            Log.d("YoloDetector", "Loading model from assets")
            val model = loadModelFile(context)
            interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
            Log.d("YoloDetector", "Model loaded OK")
        } catch (e: Exception) {
            Log.e("YoloDetector", "Failed to load model: ${e.message}", e)
            throw e
        }
    }

    /**
     * Run inference on [bitmap] (should already be cropped to the viewfinder region).
     * Returns one [DetectionBox] per NMS-surviving detection with normalized [0,1]
     * bounding-box coordinates relative to [bitmap] and the friendly label.
     *
     * YOLOv8n outputs cx/cy/w/h in the model's INPUT_SIZE pixel space (0..640).
     * Dividing by INPUT_SIZE converts them to the [0,1] range used by the caller.
     */
    fun detectBoxes(bitmap: Bitmap): List<DetectionBox> {
        val resized  = resizeBitmap(bitmap, INPUT_SIZE, INPUT_SIZE)
        val input    = bitmapToBuffer(resized)

        // YOLOv8n output shape: [1, 84, 8400]  (4 box coords + 80 class scores)
        val rawOutput = Array(1) { Array(84) { FloatArray(MAX_DETECTIONS) } }
        interpreter.run(input, rawOutput)

        val detections = parseOutput(rawOutput[0])
        return nms(detections).map { det ->
            val cocoLabel = COCO_LABELS.getOrElse(det.classId) { "unknown" }
            DetectionBox(
                cx         = det.cx / INPUT_SIZE,
                cy         = det.cy / INPUT_SIZE,
                w          = det.w  / INPUT_SIZE,
                h          = det.h  / INPUT_SIZE,
                label      = LABEL_MAP[cocoLabel] ?: cocoLabel,
                confidence = det.confidence
            )
        }
    }

    /**
     * Run inference on [bitmap] and return aggregated item counts.
     * Delegates to [detectBoxes] so logic is kept in one place.
     */
    fun detect(bitmap: Bitmap): List<DetectedItem> {
        val counts = mutableMapOf<String, Int>()
        detectBoxes(bitmap).forEach { box ->
            counts[box.label] = (counts[box.label] ?: 0) + 1
        }
        return counts.map { (label, count) -> DetectedItem(label, count) }
    }

    fun close() {
        interpreter.close()
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private data class RawDetection(
        val cx: Float, val cy: Float,
        val w: Float,  val h: Float,
        val confidence: Float,
        val classId: Int
    )

    private fun parseOutput(output: Array<FloatArray>): List<RawDetection> {
        val result = mutableListOf<RawDetection>()
        for (i in 0 until MAX_DETECTIONS) {
            val cx = output[0][i]
            val cy = output[1][i]
            val w  = output[2][i]
            val h  = output[3][i]

            var maxScore = 0f
            var maxClass = 0
            for (c in 0 until 80) {
                val score = output[4 + c][i]
                if (score > maxScore) { maxScore = score; maxClass = c }
            }

            if (maxScore >= CONF_THRESHOLD) {
                result.add(RawDetection(cx, cy, w, h, maxScore, maxClass))
            }
        }
        return result
    }

    /** Simple greedy NMS */
    private fun nms(detections: List<RawDetection>): List<RawDetection> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept   = mutableListOf<RawDetection>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept.add(best)
            sorted.removeAll { iou(best, it) > IOU_THRESHOLD }
        }
        return kept
    }

    private fun iou(a: RawDetection, b: RawDetection): Float {
        val ax1 = a.cx - a.w / 2; val ay1 = a.cy - a.h / 2
        val ax2 = a.cx + a.w / 2; val ay2 = a.cy + a.h / 2
        val bx1 = b.cx - b.w / 2; val by1 = b.cy - b.h / 2
        val bx2 = b.cx + b.w / 2; val by2 = b.cy + b.h / 2

        val interW = (minOf(ax2, bx2) - maxOf(ax1, bx1)).coerceAtLeast(0f)
        val interH = (minOf(ay2, by2) - maxOf(ay1, by1)).coerceAtLeast(0f)
        val inter  = interW * interH
        val union  = a.w * a.h + b.w * b.h - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fd     = context.assets.openFd("yolov8n.tflite")
        val stream = FileInputStream(fd.fileDescriptor)
        return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun resizeBitmap(src: Bitmap, w: Int, h: Int): Bitmap =
        Bitmap.createScaledBitmap(src, w, h, true)

    private fun bitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buf.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (px in pixels) {
            buf.putFloat(((px shr 16) and 0xFF) / 255f)  // R
            buf.putFloat(((px shr 8)  and 0xFF) / 255f)  // G
            buf.putFloat(( px         and 0xFF) / 255f)  // B
        }
        buf.rewind()
        return buf
    }
}