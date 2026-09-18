package com.mikmy.meldpdf

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/** What a tool accepts as input. */
enum class Accept { PDF, IMAGE, PDF_OR_IMAGE }

/** Loose grouping shown as section headers on the home grid. */
enum class Category(val label: String) {
    ORGANIZE("Organize"),
    CONVERT("Convert"),
    EDIT("Edit & stamp"),
    TEXT("Text"),
    SECURITY("Security"),
}

/**
 * The catalog of tools, a native mirror of the web app's TOOLS map. Ids match
 * the site so recents/deep-links can share vocabulary later.
 */
data class ToolDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accept: Accept,
    val multi: Boolean,
    val category: Category,
    val implemented: Boolean = true,
)

object Tools {
    val all: List<ToolDef> = listOf(
        // Organize
        ToolDef("merge", "Merge PDF", "Combine multiple PDFs", Icons.Filled.MergeType, Accept.PDF, multi = true, category = Category.ORGANIZE),
        ToolDef("split", "Split / Extract", "Pull out specific pages", Icons.Filled.ContentCut, Accept.PDF, multi = false, category = Category.ORGANIZE),
        ToolDef("delete", "Delete pages", "Remove unwanted pages", Icons.Filled.Delete, Accept.PDF, multi = false, category = Category.ORGANIZE),
        ToolDef("rotate", "Rotate PDF", "Fix page orientation", Icons.Filled.RotateRight, Accept.PDF, multi = false, category = Category.ORGANIZE),
        ToolDef("organize", "Organize pages", "Reorder and arrange", Icons.Filled.Dashboard, Accept.PDF, multi = false, category = Category.ORGANIZE, implemented = false),
        // Convert
        ToolDef("compress", "Compress PDF", "Reduce file size", Icons.Filled.Compress, Accept.PDF, multi = false, category = Category.CONVERT, implemented = false),
        ToolDef("img2pdf", "Images → PDF", "JPG/PNG to PDF", Icons.Filled.Image, Accept.IMAGE, multi = true, category = Category.CONVERT),
        ToolDef("pdf2img", "PDF → JPG", "Every page as a JPG", Icons.Filled.PhotoLibrary, Accept.PDF, multi = false, category = Category.CONVERT),
        ToolDef("pdf2png", "PDF → PNG", "Every page as a PNG", Icons.Filled.Palette, Accept.PDF, multi = false, category = Category.CONVERT),
        ToolDef("pdf2word", "PDF → Word", "Text to editable .docx", Icons.Filled.Article, Accept.PDF, multi = false, category = Category.CONVERT, implemented = false),
        // Edit & stamp
        ToolDef("pagenum", "Page numbers", "Stamp page numbers", Icons.Filled.Tag, Accept.PDF, multi = false, category = Category.EDIT),
        ToolDef("watermark", "Watermark", "Add a text overlay", Icons.Filled.WaterDrop, Accept.PDF, multi = false, category = Category.EDIT),
        ToolDef("sign", "Sign PDF", "Draw your signature", Icons.Filled.Draw, Accept.PDF, multi = false, category = Category.EDIT, implemented = false),
        // Text
        ToolDef("extract", "Extract text", "Copy text from a PDF", Icons.Filled.Description, Accept.PDF, multi = false, category = Category.TEXT),
        ToolDef("ocr", "OCR scanned", "Read scanned text", Icons.Filled.Visibility, Accept.PDF_OR_IMAGE, multi = false, category = Category.TEXT, implemented = false),
        // Security
        ToolDef("metadata", "Metadata", "View and strip info", Icons.Filled.Info, Accept.PDF, multi = false, category = Category.SECURITY),
        ToolDef("protect", "Protect / Unlock", "Password a PDF", Icons.Filled.Lock, Accept.PDF, multi = false, category = Category.SECURITY),
    )

    fun byId(id: String): ToolDef? = all.firstOrNull { it.id == id }
}
