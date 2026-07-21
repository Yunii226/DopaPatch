package com.example.dopapatch.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dopapatch.DopaPatchApp
import com.example.dopapatch.data.remote.NoteImageStore
import com.example.dopapatch.data.repository.NoteRepository
import com.example.dopapatch.data.sync.SyncManager
import com.example.dopapatch.domain.markdown.MdAction
import com.example.dopapatch.domain.markdown.MdEdit
import com.example.dopapatch.domain.markdown.applyMd
import com.example.dopapatch.domain.markdown.insertMd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private val IMG_RE = Regex("""!\[([^\]]*)]\(([^)\s]+)\)""")
private const val SAVE_DEBOUNCE_MS = 700L

/**
 * One Markdown doc per day. Room is the source of truth; edits autosave debounced and then sync.
 * The editor state lives here (not in the composable) because the toolbar needs the selection.
 *
 * ponytail: the note is loaded once per day (not observed) — a remote edit pulled by sync while
 * you are typing that day won't hot-swap under the cursor. That's the safe direction.
 */
class NoteViewModel(
    private val notes: NoteRepository,
    private val images: NoteImageStore?,
    private val sync: SyncManager,
    private val appScope: CoroutineScope,
) : ViewModel() {

    var field by mutableStateOf(TextFieldValue(""))
        private set
    var preview by mutableStateOf(false)
        private set
    /** [field] with storage paths swapped for signed URLs, ready to render. */
    var previewMd by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    val canAttachImages get() = images != null

    private var saved = ""            // last text known to be in Room — skips no-op writes
    private var loadedDate: LocalDate? = null   // also the "which day is open" clock for saves
    private var saveJob: Job? = null
    private val signed = mutableMapOf<String, String>()

    /** Open [d]'s note, flushing whatever day was open before. */
    fun showDate(d: LocalDate) {
        if (d == loadedDate) return
        if (loadedDate != null) flush()   // reads the *old* date/text — must run before we swap
        loadedDate = d
        preview = false
        viewModelScope.launch {
            saved = notes.observe(d).first()?.contentMd.orEmpty()
            field = TextFieldValue(saved)
        }
    }

    fun onTextChange(v: TextFieldValue) {
        field = v
        saveJob?.cancel()
        val d = loadedDate ?: return
        saveJob = appScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist(d, v.text)
        }
    }

    /** Write pending edits now — on day change, on leaving the screen, before preview. */
    fun flush() {
        saveJob?.cancel()
        val d = loadedDate ?: return
        val text = field.text
        appScope.launch { persist(d, text) }
    }

    private suspend fun persist(d: LocalDate, text: String) {
        if (d == loadedDate && text == saved) return
        runCatching { notes.saveForDate(d, text) }
            // `saved` tracks the loaded day only — a flush for a day we just left mustn't clobber it
            .onSuccess { if (d == loadedDate) saved = text; runCatching { sync.sync() } }
            .onFailure { error = "Couldn't save the note: ${it.message}" }
    }

    fun toolbar(action: MdAction) =
        edit(applyMd(action, field.text, field.selection.start, field.selection.end))

    fun togglePreview() {
        preview = !preview
        if (!preview) return
        flush()
        viewModelScope.launch { previewMd = resolveImages(field.text) }
    }

    /** Upload [bytes] to Storage and embed the returned path at the cursor. */
    fun attachImage(bytes: ByteArray, ext: String) {
        val store = images ?: return
        busy = true
        viewModelScope.launch {
            runCatching { store.upload(bytes, ext) }
                .onSuccess { path -> edit(insertMd(field.text, field.selection.end, "\n![](" + path + ")\n")) }
                .onFailure { error = "Image upload failed: ${it.message}" }
            busy = false
        }
    }

    private fun edit(e: MdEdit) {
        field = TextFieldValue(e.text, TextRange(e.selStart, e.selEnd))
        onTextChange(field)
    }

    /** Bucket is private: rewrite `![](path)` to a signed URL so the renderer (Coil) can fetch it. */
    private suspend fun resolveImages(md: String): String {
        val store = images ?: return md
        val paths = IMG_RE.findAll(md).map { it.groupValues[2] }
            .filterNot { it.startsWith("http") || it in signed }.toSet()
        for (p in paths) signed[p] = runCatching { store.signedUrl(p) }.getOrDefault(p)
        return IMG_RE.replace(md) { m ->
            "![${m.groupValues[1]}](${signed[m.groupValues[2]] ?: m.groupValues[2]})"
        }
    }

    override fun onCleared() {
        flush() // appScope outlives us, so this actually lands
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as DopaPatchApp).container
                NoteViewModel(c.noteRepository, c.noteImageStore, c.syncManager, c.appScope)
            }
        }
    }
}
