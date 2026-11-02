package ro.punguta.app.data.ocr

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * App-private copies of receipt photos. Gallery images are copied here first, so deleting the
 * copy after saving can never touch the original in the user's gallery.
 */
class ReceiptPhotos(private val context: Context) {
    private val dir: File get() = File(context.cacheDir, "receipts").apply { mkdirs() }

    fun newFile(): File = File(dir, "${UUID.randomUUID()}.jpg")

    /** Copies a picked image into app storage. Throws IOException if it cannot be read. */
    fun copyFrom(uri: Uri): File {
        val target = newFile()
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open image")
        try {
            input.use { src ->
                target.outputStream().use { dst ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = src.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) throw IOException("Image too large")
                        dst.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        return target
    }

    /** Removes leftovers from earlier sessions (e.g. the app was killed during a review). */
    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val MAX_BYTES = 40L * 1024 * 1024
    }
}
