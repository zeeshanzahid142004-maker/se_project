package com.example.myapplication


import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrUtils {
    /**
     * Generate a square QR code bitmap.
     * Call from a background coroutine (Dispatchers.Default) — it's CPU-bound.
     *
     * @param content  String to encode. We encode the full JSON payload:
     *                 {"boxId":"BOX-2026-0042","contents":[{"label":"Shirt","count":3}]}
     * @param sizePx   Pixel size of the output bitmap (512 = good print quality).
     */
    fun generate(content: String, sizePx: Int = 512): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).apply {
            for (x in 0 until sizePx)
                for (y in 0 until sizePx)
                    setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }.getOrNull()

    /** Build the JSON payload we encode into the QR. */
    fun buildPayload(boxId: String, contents: List<DetectedItem>): String = buildString {
        append("{\"boxId\":\"$boxId\",\"contents\":[")
        contents.forEachIndexed { i, item ->
            if (i > 0) append(",")
            append("{\"label\":\"${item.label}\",\"count\":${item.count}}")
        }
        append("]}")
    }
}