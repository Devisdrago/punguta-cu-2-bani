package ro.punguta.app.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ro.punguta.app.domain.receipt.OcrLine
import ro.punguta.app.domain.receipt.RowAssembler
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { IMAGE_UNREADABLE, RECOGNITION_FAILED }
}

/**
 * On-device text recognition with ML Kit's bundled Latin model. No network is used.
 * Returns printed rows top to bottom; the recognized text is never logged.
 */
class ReceiptOcr {
    suspend fun readRows(photo: File): List<String> = withContext(Dispatchers.Default) {
        val bitmap = decodeUpright(photo) ?: throw OcrException(OcrException.Reason.IMAGE_UNREADABLE)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            } catch (e: Exception) {
                throw OcrException(OcrException.Reason.RECOGNITION_FAILED)
            }
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(
                    text = line.text,
                    left = box.left.toFloat(),
                    top = box.top.toFloat(),
                    right = box.right.toFloat(),
                    bottom = box.bottom.toFloat(),
                    angleDegrees = line.angle,
                )
            }
            RowAssembler.assemble(lines)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    /** Decodes the photo scaled to a size that keeps receipt text legible, rotated upright. */
    private fun decodeUpright(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val degrees = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private companion object {
        /** Longest side after downscaling; receipts need detail, but 12+ MP is wasted memory. */
        const val MAX_SIDE = 2400
    }
}
