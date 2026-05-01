package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class DetectionBox(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val label: String,
    val confidence: Float
)

// Exact order from your data.yaml
private val CUSTOM_LABELS = listOf(
    "Jacket", "Jeans", "Jogger", "Polo", "Shirt",
    "Short", "T-Shirt", "Trouser", "shoe"
)

private const val INPUT_SIZE     = 640
private const val CONF_THRESHOLD = 0.35f

class YoloDetector(context: Context) {

    private lateinit var interpreter: Interpreter
    private var numDetections: Int = 0

    init {
        try {
            val model = loadModelFile(context)
            interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
            val outputShape = interpreter.getOutputTensor(0).shape()
            Log.d("YoloDetector", "Output shape: ${outputShape.toList()}")
            numDetections = outputShape[1]
            Log.d("YoloDetector", "numDetections=$numDetections")
        } catch (e: Exception) {
            Log.e("YoloDetector", "Failed to load model: ${e.message}", e)
            throw e
        }
    }

    init {
        try {
            val model = loadModelFile(context)
            interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))

            // Print FULL tensor info
            val outShape = interpreter.getOutputTensor(0).shape()
            val inShape  = interpreter.getInputTensor(0).shape()
            Log.e("YOLO_DEBUG", "✅ Model loaded!")
            Log.e("YOLO_DEBUG", "Input  shape: ${inShape.toList()}")
            Log.e("YOLO_DEBUG", "Output shape: ${outShape.toList()}")
            Log.e("YOLO_DEBUG", "Output count: ${interpreter.outputTensorCount}")

            // Print ALL output tensors in case model has multiple heads
            for (i in 0 until interpreter.outputTensorCount) {
                Log.e("YOLO_DEBUG", "Output[$i] shape: ${interpreter.getOutputTensor(i).shape().toList()}")
            }

            numDetections = outShape[1]
        } catch (e: Exception) {
            Log.e("YOLO_DEBUG", "❌ LOAD FAILED: ${e.message}", e)
            throw e
        }
    }

    fun detectBoxes(bitmap: Bitmap): List<DetectionBox> {
        Log.e("YOLO_DEBUG", "detectBoxes called — bitmap: ${bitmap.width}x${bitmap.height}")
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val input   = bitmapToBuffer(resized)
        val rawOutput = Array(1) { Array(numDetections) { FloatArray(6) } }

        return try {
            interpreter.run(input, rawOutput)
            Log.e("YOLO_DEBUG", "Inference done — checking detections...")

            // Print first 3 raw detections regardless of confidence
            for (i in 0 until minOf(3, numDetections)) {
                val d = rawOutput[0][i]
                Log.e("YOLO_DEBUG", "Raw[$i]: cx=${d[0]} cy=${d[1]} w=${d[2]} h=${d[3]} conf=${d[4]} cls=${d[5]}")
            }

            val results = mutableListOf<DetectionBox>()
            for (i in 0 until numDetections) {
                val det     = rawOutput[0][i]
                val conf    = det[4]
                val classId = det[5].toInt()
                if (conf >= CONF_THRESHOLD) {
                    val label = CUSTOM_LABELS.getOrElse(classId) { "Class$classId" }
                    Log.e("YOLO_DEBUG", "✅ Detection: $label conf=$conf")
                    results.add(DetectionBox(det[0]/INPUT_SIZE, det[1]/INPUT_SIZE, det[2]/INPUT_SIZE, det[3]/INPUT_SIZE, label, conf))
                }
            }
            Log.e("YOLO_DEBUG", "Total detections above threshold: ${results.size}")
            results
        } catch (e: Exception) {
            Log.e("YOLO_DEBUG", "❌ Inference FAILED: ${e.message}", e)
            emptyList()
        }
    }

    fun detect(bitmap: Bitmap): List<DetectedItem> {
        val counts = mutableMapOf<String, Int>()
        detectBoxes(bitmap).forEach { box ->
            counts[box.label] = (counts[box.label] ?: 0) + 1
        }
        return counts.map { (label, count) -> DetectedItem(label, count) }
    }

    fun close() = interpreter.close()

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fd     = context.assets.openFd("yolov11s.tflite")
        val stream = FileInputStream(fd.fileDescriptor)
        return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun bitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buf.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (px in pixels) {
            buf.putFloat(((px shr 16) and 0xFF) / 255f)
            buf.putFloat(((px shr 8)  and 0xFF) / 255f)
            buf.putFloat(( px         and 0xFF) / 255f)
        }
        buf.rewind()
        return buf
    }

}


