package de.charlex.compose.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.fop.svg.PDFTranscoder
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.jetbrains.skia.DynamicMemoryWStream
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.svg.SVGCanvas
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader


@OptIn(InternalComposeUiApi::class)
internal actual suspend fun renderComposeToPdfMultiPage(
    context: PdfContext,
    widthPt: Float,
    heightPt: Float,
    pages: List<@Composable () -> Unit>
): ByteArray = withContext(PdfMainDispatcher) {

    if (pages.isEmpty()) {
        return@withContext createEmptyPdf()
    }

    val merger = PDFMergerUtility()
    val mergedOutput = ByteArrayOutputStream()
    merger.destinationStream = mergedOutput

    try {
        pages.forEach { pageContent ->
            val svgString = renderComposePageToSvg(widthPt, heightPt, pageContent)
            val sanitizedSvg = sanitizeSvgFontFamily(svgString)
            val pagePdfBytes = svgToPdfBytes(sanitizedSvg, widthPt, heightPt)
            merger.addSource(ByteArrayInputStream(pagePdfBytes))
        }

        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())

        return@withContext mergedOutput.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }
}


@OptIn(InternalComposeUiApi::class)
internal actual suspend fun measureContentHeight(
    context: PdfContext,
    widthPt: Float,
    content: @Composable () -> Unit
): Int = withContext(PdfMainDispatcher) {
    val heightMeasured = CompletableDeferred<Int>()

    val maxMeasureHeight = PdfFormat.A4.heightPt.toInt() * 100 // Large height to allow measuring content taller than screen

    val scene = CanvasLayersComposeScene(
        density = PdfDensity
    )

    try {
        scene.setContent {
            HeightMeasurmentLayout(
                completeDeferred = {
                    if (!heightMeasured.isCompleted) {
                        heightMeasured.complete(it)
                    }
                },
                content = content
            )
        }

        // Trigger rendering to measure content (vector, no raster allocation)
        val svgStream = DynamicMemoryWStream()
        val bounds = SkiaRect.makeWH(widthPt, maxMeasureHeight.toFloat())
        val svgCanvas = SVGCanvas.make(bounds, svgStream)
        try {
            scene.render(svgCanvas.asComposeCanvas(), nanoTime = 0L)
        } finally {
            svgCanvas.close()
        }

        return@withContext withTimeout(10000) {
            heightMeasured.await()
        }
    } finally {
        scene.close()
    }
}

@OptIn(InternalComposeUiApi::class)
private fun renderComposePageToSvg(
    widthPt: Float,
    heightPt: Float,
    pageContent: @Composable () -> Unit
): String {
    val svgStream = DynamicMemoryWStream()
    val bounds = SkiaRect.makeWH(widthPt, heightPt)
    val svgCanvas = SVGCanvas.make(bounds, svgStream)

    svgCanvas.clear(Color.White.toArgb())

    val scene = CanvasLayersComposeScene(
        density = PdfDensity,
        size = IntSize(widthPt.toInt(), heightPt.toInt())
    )

    try {
        scene.setContent {
            ProvidePdfDefaults {
                with(LocalDensity.current) {
                    Box(
                        modifier = Modifier
                            .size(widthPt.toDp(), heightPt.toDp())
                    ) {
                        pageContent()
                    }
                }
            }
        }

        val composeCanvas = svgCanvas.asComposeCanvas()
        scene.render(composeCanvas, nanoTime = 0L)
    } finally {
        scene.close()
        svgCanvas.close()
    }

    val bytesWritten = svgStream.bytesWritten().toInt()
    val svgBytes = ByteArray(bytesWritten)
    svgStream.read(svgBytes, 0, bytesWritten)
    return svgBytes.decodeToString()
}

private fun svgToPdfBytes(svgString: String, widthPt: Float, heightPt: Float): ByteArray {
    val transcoder = PDFTranscoder()
    transcoder.addTranscodingHint(PDFTranscoder.KEY_WIDTH, widthPt)
    transcoder.addTranscodingHint(PDFTranscoder.KEY_HEIGHT, heightPt)
    val input = TranscoderInput(StringReader(svgString))
    val outputStream = ByteArrayOutputStream()
    val output = TranscoderOutput(outputStream)

    transcoder.transcode(input, output)

    return outputStream.toByteArray()
}

private fun sanitizeSvgFontFamily(svg: String): String {
    return svg.replace(Regex("""font-family="([^"]*)"""")) { match ->
        val raw = match.groupValues[1]
        val sanitized = raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ") { family ->
                val lower = family.lowercase()
                if (family.startsWith("'") || family.startsWith("\"")) {
                    family
                } else if (lower in GenericFontFamilies) {
                    family
                } else {
                    "'${family.replace("'", "&apos;")}'"
                }
            }
        "font-family=\"$sanitized\""
    }
}

private val GenericFontFamilies = setOf(
    "serif",
    "sans-serif",
    "monospace",
    "cursive",
    "fantasy",
    "system-ui",
    "ui-serif",
    "ui-sans-serif",
    "ui-monospace",
    "ui-rounded",
    "emoji",
    "math",
    "fangsong"
)

private fun createEmptyPdf(): ByteArray {
    PDDocument().use { document ->
        val output = ByteArrayOutputStream()
        document.save(output)
        return output.toByteArray()
    }
}
