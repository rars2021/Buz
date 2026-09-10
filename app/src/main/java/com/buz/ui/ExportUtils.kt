package com.buz.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Picture
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import java.io.OutputStream

/**
 * Renders a Compose Canvas-based composable to a Bitmap of the given size.
 * The composable receives a fixed-size DrawScope, so it must be a pure
 * canvas drawing (no children, no layout logic beyond the canvas).
 */
object PngExport {
    fun renderToBitmap(widthPx: Int, heightPx: Int, density: Density, draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Size) -> Unit): Bitmap {
        val picture = Picture()
        val pictureCanvas = picture.beginRecording(widthPx, heightPx)
        val composeCanvas = ComposeCanvas(pictureCanvas)
        val drawScope = CanvasDrawScope()
        drawScope.draw(density, LayoutDirection.Ltr, composeCanvas, Size(widthPx.toFloat(), heightPx.toFloat())) {
            draw(Size(widthPx.toFloat(), heightPx.toFloat()))
        }
        picture.endRecording()
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val nc = AndroidCanvas(bmp)
        nc.drawColor(android.graphics.Color.WHITE)
        nc.drawPicture(picture)
        return bmp
    }

    /**
     * Save a bitmap as PNG in Pictures/Buz/. Returns the content Uri.
     * Uses scoped-storage MediaStore on API 29+; direct file on older APIs.
     */
    fun savePng(context: Context, bitmap: Bitmap, filename: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Buz")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out: OutputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    fun saveTextAsFile(context: Context, text: String, filename: String, mime: String = "text/csv"): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Buz")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Files.getContentUri("external")
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
