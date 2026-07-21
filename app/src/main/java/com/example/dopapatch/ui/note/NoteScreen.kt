package com.example.dopapatch.ui.note

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dopapatch.domain.markdown.MdAction
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import java.io.File
import java.time.LocalDate

/** Toolbar buttons: label shown, action applied, spoken description for TalkBack. */
private val TOOLBAR = listOf(
    Triple("B", MdAction.Bold, "Bold"),
    Triple("I", MdAction.Italic, "Italic"),
    Triple("H1", MdAction.H1, "Heading 1"),
    Triple("H2", MdAction.H2, "Heading 2"),
    Triple("•", MdAction.Bullet, "Bullet list"),
    Triple("1.", MdAction.Number, "Numbered list"),
    Triple("☑", MdAction.Checkbox, "Checkbox"),
)

/** The day's Markdown note: toolbar + plain editor, with a rendered preview toggle. */
@Composable
fun NoteScreen(
    date: LocalDate,
    modifier: Modifier = Modifier,
    vm: NoteViewModel = viewModel(factory = NoteViewModel.Factory),
) {
    LaunchedEffect(date) { vm.showDate(date) }

    val ctx = LocalContext.current
    fun attach(uri: Uri) {
        val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) vm.error = "Couldn't read that image." else vm.attachImage(bytes, "jpg")
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        it?.let(::attach)
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(::attach)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!vm.preview) {
                TOOLBAR.forEach { (label, action, description) ->
                    TextButton(
                        onClick = { vm.toolbar(action) },
                        modifier = Modifier.semantics { contentDescription = description },
                    ) { Text(label) }
                }
                if (vm.canAttachImages) {
                    TextButton(
                        onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.semantics { contentDescription = "Insert image from gallery" },
                    ) { Text("🖼") }
                    TextButton(
                        onClick = { cameraUri = newPhotoUri(ctx).also(takePhoto::launch) },
                        modifier = Modifier.semantics { contentDescription = "Take a photo" },
                    ) { Text("📷") }
                }
            }
            TextButton(onClick = vm::togglePreview) { Text(if (vm.preview) "Edit" else "Preview") }
            if (vm.busy) CircularProgressIndicator(Modifier.padding(8.dp).size(18.dp))
        }

        vm.error?.let {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { vm.error = null }) { Text("Dismiss") }
            }
        }

        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (vm.preview) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RichText { Markdown(vm.previewMd) }
                }
            } else {
                if (vm.field.text.isEmpty()) {
                    Text(
                        "Write about your day…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = vm.field,
                    onValueChange = vm::onTextChange,
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = "Daily note, Markdown" },
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** Camera needs a file it can write to; FileProvider exposes it without a file:// leak. */
private fun newPhotoUri(ctx: android.content.Context): Uri {
    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
}
