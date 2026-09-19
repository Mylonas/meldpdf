package com.mikmy.meldpdf.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mikmy.meldpdf.Accept
import com.mikmy.meldpdf.Rules
import com.mikmy.meldpdf.ToolDef
import com.mikmy.meldpdf.pdf.PdfEngine
import com.mikmy.meldpdf.pdf.Rotation
import com.mikmy.meldpdf.pdf.ToolResult
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(tool: ToolDef, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pages by remember { mutableStateOf("") }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var password by remember { mutableStateOf("") }
    var unlockMode by remember { mutableStateOf(false) }
    var rotation by remember { mutableStateOf(Rotation.CW90) }
    var compressLevel by remember { mutableStateOf(PdfEngine.CompressLevel.BALANCED) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ToolResult?>(null) }

    val mimes = when (tool.accept) {
        Accept.PDF -> arrayOf("application/pdf")
        Accept.IMAGE -> arrayOf("image/*")
        Accept.PDF_OR_IMAGE -> arrayOf("application/pdf", "image/*")
    }

    val singlePick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { picked = listOf(uri); result = null; error = null }
    }
    val multiPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { picked = uris; result = null; error = null }
    }

    fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read that file.")

    fun run() {
        error = null
        result = null
        busy = true
        scope.launch {
            try {
                val bytes = picked.map { readBytes(it) }
                val r = when (tool.id) {
                    "merge" -> PdfEngine.merge(bytes)
                    "split" -> PdfEngine.split(bytes.first(), pages)
                    "delete" -> PdfEngine.delete(bytes.first(), pages)
                    "rotate" -> PdfEngine.rotate(bytes.first(), rotation)
                    "img2pdf" -> PdfEngine.imagesToPdf(bytes)
                    "pdf2img" -> PdfEngine.pdfToImages(bytes.first(), png = false, cacheDir = context.cacheDir)
                    "pdf2png" -> PdfEngine.pdfToImages(bytes.first(), png = true, cacheDir = context.cacheDir)
                    "compress" -> PdfEngine.compress(bytes.first(), compressLevel, context.cacheDir)
                    "pdf2word" -> PdfEngine.pdfToDocx(bytes.first())
                    "pagenum" -> PdfEngine.addPageNumbers(bytes.first())
                    "watermark" -> PdfEngine.watermark(bytes.first(), watermarkText)
                    "extract" -> PdfEngine.extractText(bytes.first())
                    "metadata" -> PdfEngine.metadata(bytes.first())
                    "protect" -> if (unlockMode) PdfEngine.unlock(bytes.first(), password)
                                 else PdfEngine.protect(bytes.first(), password)
                    else -> throw IllegalStateException("This tool is coming in a later update.")
                }
                result = r
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(tool.subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(Modifier.height(16.dp))

            if (!tool.implemented) {
                InfoCard("This tool is on the way — it lands in the next update. The tools without a “soon” badge are ready to use now.")
                return@Column
            }

            // ---- File picker --------------------------------------------
            val pickLabel = when {
                picked.isEmpty() && tool.multi -> "Choose files"
                picked.isEmpty() -> "Choose a file"
                tool.multi -> "${picked.size} selected · change"
                else -> "1 selected · change"
            }
            OutlinedButton(
                onClick = { if (tool.multi) multiPick.launch(mimes) else singlePick.launch(mimes) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(pickLabel) }

            // ---- Per-tool options ---------------------------------------
            Spacer(Modifier.height(12.dp))
            when (tool.id) {
                "split", "delete" -> OutlinedTextField(
                    value = pages,
                    onValueChange = { pages = it },
                    label = { Text("Pages, e.g. 1-3, 5, 8") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                "watermark" -> OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Watermark text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                "rotate" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rotation.entries.forEach { r ->
                        FilterChip(
                            selected = rotation == r,
                            onClick = { rotation = r },
                            label = { Text(r.label) },
                        )
                    }
                }
                "compress" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfEngine.CompressLevel.entries.forEach { lvl ->
                        FilterChip(
                            selected = compressLevel == lvl,
                            onClick = { compressLevel = lvl },
                            label = { Text(lvl.label) },
                        )
                    }
                }
                "protect" -> Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !unlockMode, onClick = { unlockMode = false }, label = { Text("Add password") })
                        FilterChip(selected = unlockMode, onClick = { unlockMode = true }, label = { Text("Remove password") })
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (unlockMode) "Current password" else "New password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---- Run ----------------------------------------------------
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = ::run,
                enabled = picked.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Run")
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                InfoCard(it, isError = true)
            }

            // ---- Result -------------------------------------------------
            result?.let { r ->
                Spacer(Modifier.height(16.dp))
                when (r) {
                    is ToolResult.FileOut -> FileResult(r, context, clipboard)
                    is ToolResult.TextOut -> TextResult(r, clipboard, context)
                    is ToolResult.MetaOut -> MetaResult(r, context, clipboard)
                    is ToolResult.CompareOut -> Column {
                        InfoCard(r.summary)
                        Spacer(Modifier.height(12.dp))
                        FileResult(r.file, context, clipboard)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(text: String, isError: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FileResult(
    r: ToolResult.FileOut,
    context: android.content.Context,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
) {
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(r.mime)) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(r.bytes) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Done — ${r.name}", fontWeight = FontWeight.Bold)
            Text(Rules.formatSize(r.bytes.size.toLong()), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { save.launch(r.name) }) { Text("Save") }
                OutlinedButton(onClick = { shareBytes(context, r.bytes, r.name, r.mime) }) { Text("Share") }
            }
        }
    }
}

@Composable
private fun TextResult(
    r: ToolResult.TextOut,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(r.stats, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Text(
                    r.text.ifBlank { "No selectable text found — this PDF may be scanned. Try OCR." },
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { clipboard.setText(AnnotatedString(r.text)) }) { Text("Copy") }
                OutlinedButton(onClick = {
                    shareBytes(context, r.text.toByteArray(), "text.txt", "text/plain")
                }) { Text("Share .txt") }
            }
        }
    }
}

@Composable
private fun MetaResult(
    r: ToolResult.MetaOut,
    context: android.content.Context,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Metadata", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            r.rows.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(k, modifier = Modifier.width(96.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(v, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Save a clean copy with all of this stripped:", fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            FileResult(r.cleaned, context, clipboard)
        }
    }
}

// shareBytes lives in Io.kt (shared with the custom screens).
