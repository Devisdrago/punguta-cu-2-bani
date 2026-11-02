package ro.punguta.app.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.data.ocr.OcrException
import ro.punguta.app.data.ocr.ReceiptOcr
import ro.punguta.app.data.ocr.ReceiptPhotos
import ro.punguta.app.data.repo.CategoryRepository
import ro.punguta.app.domain.receipt.ReceiptParser
import ro.punguta.app.ui.appViewModelFactory
import ro.punguta.app.ui.expenses.ExpenseForm
import ro.punguta.app.ui.expenses.ItemForm
import ro.punguta.app.ui.expenses.PendingDraftStore
import java.io.File
import java.io.IOException
import java.time.LocalDate

enum class ScanError { CAPTURE_FAILED, IMAGE_UNREADABLE, NO_TEXT, OCR_FAILED }

sealed interface ScanState {
    data object Camera : ScanState
    data object Processing : ScanState
    data class Failed(val error: ScanError) : ScanState
    /** The parsed draft is waiting in [PendingDraftStore] under [draftKey]. */
    data class Ready(val draftKey: String) : ScanState
}

class ScanViewModel(
    private val photos: ReceiptPhotos,
    private val ocr: ReceiptOcr,
    private val categories: CategoryRepository,
    private val pending: PendingDraftStore,
) : ViewModel() {
    private val _state = MutableStateFlow<ScanState>(ScanState.Camera)
    val state = _state.asStateFlow()

    /** The current photo copy; owned by this screen until handed to the review screen. */
    private var photo: File? = null

    fun newCaptureFile(): File = photos.newFile().also { replacePhoto(it) }

    fun onCaptured() = photo?.let(::process)

    fun onCaptureFailed() {
        replacePhoto(null)
        _state.value = ScanState.Failed(ScanError.CAPTURE_FAILED)
    }

    fun onPicked(uri: Uri) = viewModelScope.launch {
        _state.value = ScanState.Processing
        val copy = try {
            withContext(Dispatchers.IO) { photos.copyFrom(uri) }
        } catch (e: IOException) {
            _state.value = ScanState.Failed(ScanError.IMAGE_UNREADABLE)
            return@launch
        } catch (e: SecurityException) {
            _state.value = ScanState.Failed(ScanError.IMAGE_UNREADABLE)
            return@launch
        }
        replacePhoto(copy)
        process(copy)
    }

    fun retry() {
        replacePhoto(null)
        _state.value = ScanState.Camera
    }

    /** Skips OCR and opens an empty receipt form, e.g. when the text cannot be read. */
    fun enterManually() {
        replacePhoto(null)
        val form = ExpenseForm(source = ExpenseSource.RECEIPT, items = listOf(ItemForm(key = 1)))
        _state.value = ScanState.Ready(pending.put(form))
    }

    fun onNavigated() {
        // Ownership of the photo moved to the review screen, which deletes it on save or discard.
        photo = null
        _state.value = ScanState.Camera
    }

    private fun process(file: File) = viewModelScope.launch {
        _state.value = ScanState.Processing
        try {
            val rows = ocr.readRows(file)
            if (rows.none { line -> line.any { it.isLetterOrDigit() } }) {
                _state.value = ScanState.Failed(ScanError.NO_TEXT)
                return@launch
            }
            val today = LocalDate.now()
            val parsed = withContext(Dispatchers.Default) { ReceiptParser(today).parse(rows) }
            val form = ReceiptFormMapper.toForm(parsed, categories.suggester(), file.path, today)
            _state.value = ScanState.Ready(pending.put(form))
        } catch (e: OcrException) {
            _state.value = ScanState.Failed(
                if (e.reason == OcrException.Reason.IMAGE_UNREADABLE) ScanError.IMAGE_UNREADABLE else ScanError.OCR_FAILED,
            )
        }
    }

    private fun replacePhoto(file: File?) {
        photo?.takeIf { it != file }?.delete()
        photo = file
    }

    override fun onCleared() {
        // Leaving the scanner without reaching the review screen: drop the copy.
        photo?.delete()
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> ScanViewModel(c.receiptPhotos, c.ocr, c.categories, c.pendingDrafts) }
    }
}
