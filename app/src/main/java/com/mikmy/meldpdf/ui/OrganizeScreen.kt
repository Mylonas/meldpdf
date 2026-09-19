package com.mikmy.meldpdf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikmy.meldpdf.pdf.PdfEngine
import com.mikmy.meldpdf.pdf.ToolResult
import kotlinx.coroutines.launch

private class PageItem(val originalIndex: Int, val rotation: Int, val thumb: ImageBitmap)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(onBack: () -> Unit, onDone: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf<ByteArray?>(null) }
    var pages by remember { mutableStateOf<List<PageItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ToolResult.FileOut?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            loading = true; error = null; result = null
            try {
                val bytes = readUriBytes(context, uri)
                input = bytes
                pages = PdfEngine.renderThumbnails(bytes, 240, context.cacheDir)
                    .mapIndexed { i, b -> PageItem(i, 0, b.asImageBitmap()) }
            } catch (e: Exception) {
                error = e.message ?: "Could not open that PDF."
            } finally {
                loading = false
            }
        }
    }

    fun swap(i: Int, j: Int) {
        if (j < 0 || j >= pages.size) return
        pages = pages.toMutableList().also { it[i] = pages[j]; it[j] = pages[i] }
    }

    fun rotate(i: Int) {
        val p = pages[i]
        pages = pages.toMutableList().also { it[i] = PageItem(p.originalIndex, (p.rotation + 90) % 360, p.thumb) }
    }

    fun remove(i: Int) {
        pages = pages.toMutableList().also { it.removeAt(i) }
    }

    fun save() {
        val src = input ?: return
        busy = true; error = null
        scope.launch {
            try {
                val order = pages.map { it.originalIndex }
                val rot = pages.associate { it.originalIndex to it.rotation }
                result = PdfEngine.reorganize(src, order, rot) as ToolResult.FileOut
                onDone()
            } catch (e: Exception) {
                error = e.message ?: "Could not save."
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organize pages", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            OutlinedButton(onClick = { pick.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (pages.isEmpty()) "Choose a PDF" else "${pages.size} pages · change PDF")
            }

            if (loading) {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            error?.let {
                Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error)
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(pages) { i, item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = item.thumb,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).rotate(item.rotation.toFloat()),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Page ${item.originalIndex + 1}", modifier = Modifier.weight(1f))
                            IconButton(onClick = { swap(i, i - 1) }) { Icon(Icons.Filled.ArrowUpward, "Up") }
                            IconButton(onClick = { swap(i, i + 1) }) { Icon(Icons.Filled.ArrowDownward, "Down") }
                            IconButton(onClick = { rotate(i) }) { Icon(Icons.Filled.RotateRight, "Rotate") }
                            IconButton(onClick = { remove(i) }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    }
                }
            }

            if (pages.isNotEmpty()) {
                Button(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Save organized PDF")
                }
            }

            result?.let {
                Spacer(Modifier.height(12.dp))
                Text("Done — ${it.name}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SaveShareRow(it, context)
            }
        }
    }
}
