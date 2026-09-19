package com.mikmy.meldpdf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mikmy.meldpdf.pdf.ToolResult
import java.io.File

/** Read a picked document's bytes. */
fun readUriBytes(context: Context, uri: Uri): ByteArray =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Could not read that file.")

/** Write [bytes] to cache/out and hand them to another app (Share / Open with). */
fun shareBytes(context: Context, bytes: ByteArray, name: String, mime: String) {
    val dir = File(context.cacheDir, "out").apply { mkdirs() }
    val file = File(dir, name).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** A Save (SAF) + Share button row for a produced file. */
@Composable
fun SaveShareRow(file: ToolResult.FileOut, context: Context) {
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(file.mime)) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { save.launch(file.name) }) { Text("Save") }
        OutlinedButton(onClick = { shareBytes(context, file.bytes, file.name, file.mime) }) { Text("Share") }
    }
}
