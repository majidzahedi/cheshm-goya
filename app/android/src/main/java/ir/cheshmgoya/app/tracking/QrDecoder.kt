package ir.cheshmgoya.app.tracking

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Decodes the pairing QR code shown on the server's page. */
class QrDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }

    fun decode(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        // A code held up to the front camera can appear mirrored; try both ways.
        return tryDecode(w, h, pixels) ?: tryDecode(w, h, mirrored(w, h, pixels))
    }

    private fun tryDecode(w: Int, h: Int, pixels: IntArray): String? = try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels)))).text
    } catch (e: NotFoundException) {
        null
    } catch (e: Exception) {
        null
    } finally {
        reader.reset()
    }

    private fun mirrored(w: Int, h: Int, src: IntArray): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) out[row + x] = src[row + w - 1 - x]
        }
        return out
    }
}
