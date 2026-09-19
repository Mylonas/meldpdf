package com.mikmy.meldpdf.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mikmy.meldpdf.pdf.PdfEngine
import com.mikmy.meldpdf.pdf.ToolResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf<ByteArray?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var pageIndex by remember { mutableStateOf(0) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var posX by remember { mutableStateOf(0.85f) }
    var posY by remember { mutableStateOf(0.9f) }
    var sizeFrac by remember { mutableStateOf(0.3f) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ToolResult.FileOut?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            error = null; result = null
            try {
                val bytes = readUriBytes(context, uri)
                input = bytes
                pageCount = PdfEngine.pageCount(bytes)
                pageIndex = 0
            } catch (e: Exception) {
                error = e.message ?: "Could not open that PDF."
            }
        }
    }

    // Re-render the page preview whenever the file or page changes.
    LaunchedEffect(input, pageIndex) {
        val bytes = input ?: return@LaunchedEffect
        preview = try {
            PdfEngine.renderPage(bytes, pageIndex, 1000, context.cacheDir).asImageBitmap()
        } catch (e: Exception) {
            error = e.message; null
        }
    }

    fun rasterizeSignature(): Bitmap {
        val padW = padSize.width.toFloat().coerceAtLeast(1f)
        val padH = padSize.height.toFloat().coerceAtLeast(1f)
        val outW = 600
        val outH = (outW * padH / padW).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 6f
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }
        val sx = outW / padW
        val sy = outH / padH
        for (stroke in strokes) {
            if (stroke.size < 2) continue
            val path = android.graphics.Path()
            path.moveTo(stroke[0].x * sx, stroke[0].y * sy)
            for (p in stroke.drop(1)) path.lineTo(p.x * sx, p.y * sy)
            canvas.drawPath(path, paint)
        }
        return bmp
    }

    fun save() {
        val bytes = input ?: return
        if (strokes.isEmpty()) { error = "Draw a signature first."; return }
        busy = true; error = null
        scope.launch {
            try {
                val sig = rasterizeSignature()
                val (wPt, hPt) = PdfEngine.pagePointSize(bytes, pageIndex)
                val sigWpt = wPt * sizeFrac
                val sigHpt = sigWpt * (padSize.height.toFloat() / padSize.width.coerceAtLeast(1))
                val xPt = (wPt - sigWpt) * posX
                val yPt = (hPt - sigHpt) * (1f - posY)
                result = PdfEngine.placeSignature(bytes, pageIndex, sig, xPt, yPt, sigWpt, sigHpt) as ToolResult.FileOut
            } catch (e: Exception) {
                error = e.message ?: "Could not sign."
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            OutlinedButton(onClick = { pick.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (input == null) "Choose a PDF" else "Change PDF")
            }

            error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }

            val pv = preview
            if (pv != null) {
                // Page selector
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (pageIndex > 0) pageIndex-- }, enabled = pageIndex > 0) { Text("Prev") }
                    Text("Page ${pageIndex + 1} of $pageCount", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { if (pageIndex < pageCount - 1) pageIndex++ }, enabled = pageIndex < pageCount - 1) { Text("Next") }
                }

                // Page preview with the signature overlay positioned by the sliders.
                Spacer(Modifier.height(12.dp))
                val aspect = pv.height.toFloat() / pv.width.toFloat()
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val boxW = maxWidth
                    val boxH = boxW * aspect
                    Box(Modifier.width(boxW).height(boxH)) {
                        Image(pv, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        if (strokes.isNotEmpty() && padSize != IntSize.Zero) {
                            val sigW = boxW * sizeFrac
                            val sigH = sigW * (padSize.height.toFloat() / padSize.width.coerceAtLeast(1))
                            val offX = (boxW - sigW) * posX
                            val offY = (boxH - sigH) * posY
                            Box(
                                Modifier
                                    .offset(x = offX, y = offY)
                                    .size(width = sigW, height = sigH)
                                    .border(1.dp, MaterialTheme.colorScheme.primary),
                            ) {
                                SignatureCanvas(strokes, padSize, Modifier.fillMaxSize())
                            }
                        }
                    }
                }

                // Placement sliders
                Spacer(Modifier.height(8.dp))
                Text("Horizontal"); Slider(value = posX, onValueChange = { posX = it })
                Text("Vertical"); Slider(value = posY, onValueChange = { posY = it })
                Text("Size"); Slider(value = sizeFrac, onValueChange = { sizeFrac = it }, valueRange = 0.1f..0.7f)
            }

            // Signature pad
            Spacer(Modifier.height(12.dp))
            Text("Draw your signature", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .onSizeChanged { padSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { o -> strokes = strokes + listOf(listOf(o)) },
                            onDrag = { change, _ ->
                                val last = (strokes.lastOrNull() ?: emptyList()) + change.position
                                strokes = strokes.dropLast(1) + listOf(last)
                                change.consume()
                            },
                        )
                    },
            ) {
                SignatureCanvas(strokes, padSize, Modifier.fillMaxSize())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { strokes = emptyList() }) { Text("Clear") }
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = ::save, enabled = input != null && !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Sign & save")
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

/** Draws the captured strokes (in [padSize] px space) scaled to fill this box. */
@Composable
private fun SignatureCanvas(strokes: List<List<Offset>>, padSize: IntSize, modifier: Modifier) {
    Canvas(modifier) {
        if (padSize == IntSize.Zero) return@Canvas
        val sx = size.width / padSize.width
        val sy = size.height / padSize.height
        for (stroke in strokes) {
            if (stroke.size < 2) continue
            val path = Path()
            path.moveTo(stroke[0].x * sx, stroke[0].y * sy)
            for (p in stroke.drop(1)) path.lineTo(p.x * sx, p.y * sy)
            drawPath(path, Color.Black, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
    }
}
