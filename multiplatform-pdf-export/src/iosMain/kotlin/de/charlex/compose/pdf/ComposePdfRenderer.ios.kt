package de.charlex.compose.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.DynamicMemoryWStream
import org.jetbrains.skia.svg.SVGCanvas
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreText.CTLineCreateWithAttributedString
import platform.CoreText.CTLineDraw
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64DecodingIgnoreUnknownCharacters
import platform.Foundation.NSMutableData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginPDFContextToData
import platform.UIKit.UIGraphicsBeginPDFPageWithInfo
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIImage
import platform.posix.memcpy
import org.jetbrains.skia.Rect as SkiaRect


@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, InternalComposeUiApi::class)
internal actual suspend fun renderComposeToPdfMultiPage(
    context: PdfContext,
    widthPt: Float,
    heightPt: Float,
    pages: List<@Composable () -> Unit>
): ByteArray = withContext(Dispatchers.Main) {

    val pdfData = NSMutableData()
    val pageBounds = CGRectMake(0.0, 0.0, widthPt.toDouble(), heightPt.toDouble())

    try {
        UIGraphicsBeginPDFContextToData(pdfData, pageBounds, null)

        pages.forEach { pageContent ->

            UIGraphicsBeginPDFPageWithInfo(pageBounds, null)
            val pdfContext = UIGraphicsGetCurrentContext()

            if (pdfContext != null) {
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
                }

                svgCanvas.close()

                val bytesWritten = svgStream.bytesWritten().toInt()
                val svgBytes = ByteArray(bytesWritten)
                svgStream.read(svgBytes, 0, bytesWritten) // liest in das ByteArray
                val svgString = svgBytes.decodeToString()

                renderSvgToPdfContext(pdfContext, svgString, widthPt, heightPt)

            }
        }

        UIGraphicsEndPDFContext()

        val bytes = pdfData.toByteArray()
        bytes

    } catch (e: Exception) {
        e.printStackTrace()
        try { UIGraphicsEndPDFContext() } catch (_: Exception) {}
        throw e
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun renderSvgToPdfContext(
    pdfContext: platform.CoreGraphics.CGContextRef,
    svgString: String,
    widthPt: Float,
    heightPt: Float
) {
    renderSvgWithCoreGraphics(pdfContext, svgString, widthPt, heightPt)
}


@OptIn(ExperimentalForeignApi::class)
private fun renderSvgWithCoreGraphics(
    pdfContext: platform.CoreGraphics.CGContextRef,
    svgString: String,
    widthPt: Float,
    heightPt: Float
): Boolean {
    return try {

        val root = parseSvgTree(svgString) ?: return false

        platform.CoreGraphics.CGContextSaveGState(pdfContext)

        val viewBox = extractViewBox(root)
        if (viewBox != null) {
            applyViewBoxTransform(pdfContext, viewBox, widthPt, heightPt)
        }

        val logicalHeight = (viewBox?.height ?: heightPt.toDouble())
        val elementsRendered = renderSvgNodeTree(pdfContext, root, pageHeight = logicalHeight)

        platform.CoreGraphics.CGContextRestoreGState(pdfContext)


        elementsRendered > 0
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun parseSvgPathData(d: String): platform.CoreGraphics.CGPathRef? {
    val path = platform.CoreGraphics.CGPathCreateMutable() ?: return null

    var currentX = 0.0
    var currentY = 0.0

    val tokens = tokenizePathData(d)
    var i = 0

    while (i < tokens.size) {
        val token = tokens[i]

        when (token.uppercase()) {
            "M" -> { // MoveTo absolute
                if (i + 2 < tokens.size) {
                    currentX = tokens[i + 1].toDoubleOrNull() ?: 0.0
                    currentY = tokens[i + 2].toDoubleOrNull() ?: 0.0
                    platform.CoreGraphics.CGPathMoveToPoint(path, null, currentX, currentY)
                    i += 3
                } else i++
            }
            "m" -> { // MoveTo relative
                if (i + 2 < tokens.size) {
                    currentX += tokens[i + 1].toDoubleOrNull() ?: 0.0
                    currentY += tokens[i + 2].toDoubleOrNull() ?: 0.0
                    platform.CoreGraphics.CGPathMoveToPoint(path, null, currentX, currentY)
                    i += 3
                } else i++
            }
            "L" -> { // LineTo absolute
                if (i + 2 < tokens.size) {
                    currentX = tokens[i + 1].toDoubleOrNull() ?: 0.0
                    currentY = tokens[i + 2].toDoubleOrNull() ?: 0.0
                    platform.CoreGraphics.CGPathAddLineToPoint(path, null, currentX, currentY)
                    i += 3
                } else i++
            }
            "l" -> { // LineTo relative
                if (i + 2 < tokens.size) {
                    currentX += tokens[i + 1].toDoubleOrNull() ?: 0.0
                    currentY += tokens[i + 2].toDoubleOrNull() ?: 0.0
                    platform.CoreGraphics.CGPathAddLineToPoint(path, null, currentX, currentY)
                    i += 3
                } else i++
            }
            "H" -> { // Horizontal line absolute
                if (i + 1 < tokens.size) {
                    currentX = tokens[i + 1].toDoubleOrNull() ?: 0.0
                    platform.CoreGraphics.CGPathAddLineToPoint(path, null, currentX, currentY)
                    i += 2
                } else i++
            }
            "h" -> { // Horizontal line relative
                if (i + 1 < tokens.size) {
                    currentX += tokens[i + 1].toDoubleOrNull() ?: 0.0
                    platform.CoreGraphics.CGPathAddLineToPoint(path, null, currentX, currentY)
                    i += 2
                } else i++
            }
            "V" -> { // Vertical line absolute
                if (i + 1 < tokens.size) {
                    currentY = tokens[i + 1].toDoubleOrNull() ?: 0.0
                    platform.CoreGraphics.CGPathAddLineToPoint(path, null, currentX, currentY)
                    i += 2
                } else i++
            }
            "v" -> { // Vertical line relative
                if (i + 1 < tokens.size) {
                    currentY += tokens[i + 1].toDoubleOrNull() ?: 0.0
                    platform.CoreGraphics.CGPathAddLineToPoint(path, null, currentX, currentY)
                    i += 2
                } else i++
            }
            "C" -> { // Cubic bezier absolute
                if (i + 6 < tokens.size) {
                    val x1 = tokens[i + 1].toDoubleOrNull() ?: 0.0
                    val y1 = tokens[i + 2].toDoubleOrNull() ?: 0.0
                    val x2 = tokens[i + 3].toDoubleOrNull() ?: 0.0
                    val y2 = tokens[i + 4].toDoubleOrNull() ?: 0.0
                    currentX = tokens[i + 5].toDoubleOrNull() ?: 0.0
                    currentY = tokens[i + 6].toDoubleOrNull() ?: 0.0
                    platform.CoreGraphics.CGPathAddCurveToPoint(path, null, x1, y1, x2, y2, currentX, currentY)
                    i += 7
                } else i++
            }
            "Q" -> { // Quadratic bezier absolute
                if (i + 4 < tokens.size) {
                    val x1 = tokens[i + 1].toDoubleOrNull() ?: 0.0
                    val y1 = tokens[i + 2].toDoubleOrNull() ?: 0.0
                    currentX = tokens[i + 3].toDoubleOrNull() ?: 0.0
                    currentY = tokens[i + 4].toDoubleOrNull() ?: 0.0
                    platform.CoreGraphics.CGPathAddQuadCurveToPoint(path, null, x1, y1, currentX, currentY)
                    i += 5
                } else i++
            }
            "Z", "z" -> { // ClosePath
                platform.CoreGraphics.CGPathCloseSubpath(path)
                i++
            }
            else -> i++
        }
    }

    return path
}

private fun tokenizePathData(d: String): List<String> {
    val tokens = mutableListOf<String>()
    val regex = """([MmLlHhVvCcSsQqTtAaZz])|(-?\d*\.?\d+)""".toRegex()

    regex.findAll(d).forEach { match ->
        tokens.add(match.value)
    }

    return tokens
}

@OptIn(ExperimentalForeignApi::class)
private fun setFillColor(context: platform.CoreGraphics.CGContextRef, color: String) {
    val (r, g, b, a) = parseColor(color)
    platform.CoreGraphics.CGContextSetRGBFillColor(context, r, g, b, a)
}

@OptIn(ExperimentalForeignApi::class)
private fun setStrokeColor(context: platform.CoreGraphics.CGContextRef, color: String) {
    val (r, g, b, a) = parseColor(color)
    platform.CoreGraphics.CGContextSetRGBStrokeColor(context, r, g, b, a)
}

@OptIn(ExperimentalForeignApi::class)
private fun setFillColorWithOpacity(context: platform.CoreGraphics.CGContextRef, color: String, opacity: Double) {
    val (r, g, b, a) = parseColor(color)
    platform.CoreGraphics.CGContextSetRGBFillColor(context, r, g, b, a * opacity)
}

@OptIn(ExperimentalForeignApi::class)
private fun setStrokeColorWithOpacity(context: platform.CoreGraphics.CGContextRef, color: String, opacity: Double) {
    val (r, g, b, a) = parseColor(color)
    platform.CoreGraphics.CGContextSetRGBStrokeColor(context, r, g, b, a * opacity)
}

private fun parseColor(color: String): List<Double> {
    return when {
        color.startsWith("#") && color.length == 7 -> {
            val r = color.substring(1, 3).toInt(16) / 255.0
            val g = color.substring(3, 5).toInt(16) / 255.0
            val b = color.substring(5, 7).toInt(16) / 255.0
            listOf(r, g, b, 1.0)
        }
        color.startsWith("#") && color.length == 4 -> {
            val r = color.substring(1, 2).repeat(2).toInt(16) / 255.0
            val g = color.substring(2, 3).repeat(2).toInt(16) / 255.0
            val b = color.substring(3, 4).repeat(2).toInt(16) / 255.0
            listOf(r, g, b, 1.0)
        }
        color.startsWith("rgb(") -> {
            val values = color.removePrefix("rgb(").removeSuffix(")").split(",")
            if (values.size >= 3) {
                val r = values[0].trim().toDoubleOrNull()?.div(255.0) ?: 0.0
                val g = values[1].trim().toDoubleOrNull()?.div(255.0) ?: 0.0
                val b = values[2].trim().toDoubleOrNull()?.div(255.0) ?: 0.0
                listOf(r, g, b, 1.0)
            } else listOf(0.0, 0.0, 0.0, 1.0)
        }
        color.startsWith("rgba(") -> {
            val values = color.removePrefix("rgba(").removeSuffix(")").split(",")
            if (values.size >= 4) {
                val r = values[0].trim().toDoubleOrNull()?.div(255.0) ?: 0.0
                val g = values[1].trim().toDoubleOrNull()?.div(255.0) ?: 0.0
                val b = values[2].trim().toDoubleOrNull()?.div(255.0) ?: 0.0
                val a = values[3].trim().toDoubleOrNull() ?: 1.0
                listOf(r, g, b, a)
            } else listOf(0.0, 0.0, 0.0, 1.0)
        }
        color == "white" -> listOf(1.0, 1.0, 1.0, 1.0)
        color == "black" -> listOf(0.0, 0.0, 0.0, 1.0)
        color == "red" -> listOf(1.0, 0.0, 0.0, 1.0)
        color == "green" -> listOf(0.0, 0.5, 0.0, 1.0)
        color == "blue" -> listOf(0.0, 0.0, 1.0, 1.0)
        color == "transparent" -> listOf(0.0, 0.0, 0.0, 0.0)
        else -> listOf(0.0, 0.0, 0.0, 1.0) // Default: schwarz
    }
}

private fun parseSvgNumberListFirst(value: String?): Double? {
    if (value == null) return null
    val token = value.trim().split(Regex("[ ,]+"), limit = 2).firstOrNull() ?: return null
    return token.trimEnd(',').toDoubleOrNull()
}

private fun parseSvgNumberList(value: String?): List<Double> {
    if (value == null) return emptyList()
    return value
        .trim()
        .split(Regex("[ ,]+"))
        .mapNotNull { it.trimEnd(',').toDoubleOrNull() }
}

private fun resolveFont(node: SvgNode, fontSize: Double): platform.UIKit.UIFont {
    val weightAttr = node.attrs["font-weight"]?.lowercase()
    val isBold = weightAttr == "bold" || (weightAttr?.toIntOrNull()?.let { it >= 600 } == true)

    val families = node.attrs["font-family"]
        ?.split(",")
        ?.map { it.trim().trim('"', '\'') }
        ?: emptyList()

    for (family in families) {
        if (family.isBlank()) continue
        val lower = family.lowercase()
        if (lower.contains("system") || lower.startsWith(".sf")) {
            return if (isBold) {
                platform.UIKit.UIFont.boldSystemFontOfSize(fontSize)
            } else {
                platform.UIKit.UIFont.systemFontOfSize(fontSize)
            }
        }
        val font = platform.UIKit.UIFont.fontWithName(family, fontSize)
        if (font != null) return font
    }

    return if (isBold) {
        platform.UIKit.UIFont.boldSystemFontOfSize(fontSize)
    } else {
        platform.UIKit.UIFont.systemFontOfSize(fontSize)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)

    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

private fun countSvgElements(svg: String): String {
    val counts = mutableListOf<String>()

    val rectCount = "<rect".toRegex().findAll(svg).count()
    if (rectCount > 0) counts.add("rect: $rectCount")

    val pathCount = "<path".toRegex().findAll(svg).count()
    if (pathCount > 0) counts.add("path: $pathCount")

    val textCount = "<text".toRegex().findAll(svg).count()
    if (textCount > 0) counts.add("text: $textCount")

    val circleCount = "<circle".toRegex().findAll(svg).count()
    if (circleCount > 0) counts.add("circle: $circleCount")

    val lineCount = "<line".toRegex().findAll(svg).count()
    if (lineCount > 0) counts.add("line: $lineCount")

    val imageCount = "<image".toRegex().findAll(svg).count()
    if (imageCount > 0) counts.add("image: $imageCount")

    val gCount = "<g".toRegex().findAll(svg).count()
    if (gCount > 0) counts.add("g: $gCount")

    return counts.joinToString(", ")
}

private data class ViewBox(val minX: Double, val minY: Double, val width: Double, val height: Double)

@OptIn(ExperimentalForeignApi::class)
private fun applyViewBoxTransform(
    context: platform.CoreGraphics.CGContextRef,
    viewBox: ViewBox,
    widthPt: Float,
    heightPt: Float
) {
    val sx = widthPt.toDouble() / viewBox.width
    val sy = heightPt.toDouble() / viewBox.height
    platform.CoreGraphics.CGContextTranslateCTM(context, -viewBox.minX, -viewBox.minY)
    platform.CoreGraphics.CGContextScaleCTM(context, sx, sy)
}

@OptIn(ExperimentalForeignApi::class)
private fun applyTransformList(context: platform.CoreGraphics.CGContextRef, transform: String) {
    val regex = """(translate|scale|rotate|matrix)\s*\(([^)]*)\)""".toRegex()
    regex.findAll(transform).forEach { match ->
        val type = match.groupValues[1]
        val args = match.groupValues[2]
            .trim()
            .split(Regex("[ ,]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toDoubleOrNull() }

        when (type) {
            "translate" -> {
                val tx = args.getOrNull(0) ?: 0.0
                val ty = args.getOrNull(1) ?: 0.0
                platform.CoreGraphics.CGContextTranslateCTM(context, tx, ty)
            }
            "scale" -> {
                val sx = args.getOrNull(0) ?: 1.0
                val sy = args.getOrNull(1) ?: sx
                platform.CoreGraphics.CGContextScaleCTM(context, sx, sy)
            }
            "rotate" -> {
                val angle = args.getOrNull(0) ?: 0.0
                val radians = angle * kotlin.math.PI / 180.0
                platform.CoreGraphics.CGContextRotateCTM(context, radians)
            }
            "matrix" -> {
                if (args.size >= 6) {
                    val a = args[0]
                    val b = args[1]
                    val c = args[2]
                    val d = args[3]
                    val tx = args[4]
                    val ty = args[5]
                    val cgTransform = platform.CoreGraphics.CGAffineTransformMake(a, b, c, d, tx, ty)
                    platform.CoreGraphics.CGContextConcatCTM(context, cgTransform)
                }
            }
        }
    }
}

private data class SvgNode(
    val name: String,
    val attrs: Map<String, String>,
    val children: MutableList<SvgNode> = mutableListOf(),
    var text: String = ""
)

private fun parseSvgTree(svg: String): SvgNode? {
    var i = 0
    val stack = ArrayDeque<SvgNode>()
    var root: SvgNode? = null

    fun skipWhitespace() {
        while (i < svg.length && svg[i].isWhitespace()) i++
    }

    while (i < svg.length) {
        if (svg[i] == '<') {
            if (i + 3 < svg.length && svg.startsWith("<!--", i)) {
                val end = svg.indexOf("-->", i + 4)
                i = if (end == -1) svg.length else end + 3
                continue
            }
            if (i + 1 < svg.length && svg[i + 1] == '?') {
                val end = svg.indexOf("?>", i + 2)
                i = if (end == -1) svg.length else end + 2
                continue
            }
            if (i + 1 < svg.length && svg[i + 1] == '/') {
                i += 2
                skipWhitespace()
                while (i < svg.length && svg[i] != '>') i++
                if (i < svg.length) i++
                if (stack.isNotEmpty()) stack.removeLast()
                continue
            }

            i++
            skipWhitespace()
            val nameStart = i
            while (i < svg.length && !svg[i].isWhitespace() && svg[i] != '/' && svg[i] != '>') i++
            val name = svg.substring(nameStart, i)

            val attrs = mutableMapOf<String, String>()
            var selfClosing = false

            while (i < svg.length) {
                skipWhitespace()
                if (i >= svg.length) break
                val ch = svg[i]
                if (ch == '/') {
                    selfClosing = true
                    i++
                    if (i < svg.length && svg[i] == '>') i++
                    break
                }
                if (ch == '>') {
                    i++
                    break
                }

                val keyStart = i
                while (i < svg.length && svg[i] != '=' && !svg[i].isWhitespace()) i++
                val key = svg.substring(keyStart, i)
                skipWhitespace()
                if (i < svg.length && svg[i] == '=') i++
                skipWhitespace()
                if (i < svg.length) {
                    val quote = svg[i]
                    if (quote == '"' || quote == '\'') {
                        i++
                        val valueStart = i
                        while (i < svg.length && svg[i] != quote) i++
                        val value = svg.substring(valueStart, i)
                        attrs[key] = value
                        if (i < svg.length) i++
                    } else {
                        val valueStart = i
                        while (i < svg.length && !svg[i].isWhitespace() && svg[i] != '>') i++
                        attrs[key] = svg.substring(valueStart, i)
                    }
                }
            }

            val node = SvgNode(name = name, attrs = attrs)
            if (stack.isNotEmpty()) {
                stack.last().children.add(node)
            } else if (root == null) {
                root = node
            }

            if (!selfClosing) {
                stack.add(node)
            }
        } else {
            val textStart = i
            while (i < svg.length && svg[i] != '<') i++
            if (stack.isNotEmpty()) {
                val current = stack.last()
                val raw = svg.substring(textStart, i)
                val text = if (current.name == "text" || current.name == "tspan") raw else raw.trim()
                if (text.isNotEmpty()) {
                    if (current.text.isEmpty()) current.text = text else current.text += text
                }
            }
        }
    }

    return root
}

private fun extractViewBox(root: SvgNode): ViewBox? {
    val vb = root.attrs["viewBox"] ?: return null
    val parts = vb.trim().split(Regex("[ ,]+"))
    if (parts.size != 4) return null
    val minX = parts[0].toDoubleOrNull() ?: return null
    val minY = parts[1].toDoubleOrNull() ?: return null
    val width = parts[2].toDoubleOrNull() ?: return null
    val height = parts[3].toDoubleOrNull() ?: return null
    if (width <= 0.0 || height <= 0.0) return null
    return ViewBox(minX, minY, width, height)
}

@OptIn(ExperimentalForeignApi::class)
private fun renderSvgNodeTree(
    context: platform.CoreGraphics.CGContextRef,
    node: SvgNode,
    pageHeight: Double,
    defMap: MutableMap<String, SvgNode> = mutableMapOf()
): Int {
    var count = 0

    platform.CoreGraphics.CGContextSaveGState(context)

    node.attrs["transform"]?.let { applyTransformList(context, it) }

    when (node.name) {
        "defs" -> {
            collectDefs(node, defMap)
            platform.CoreGraphics.CGContextRestoreGState(context)
            return count
        }
        "rect" -> if (renderRectNode(context, node)) count++
        "path" -> if (renderPathNode(context, node)) count++
        "text" -> {
            if (renderTextNode(context, node)) count++
            platform.CoreGraphics.CGContextRestoreGState(context)
            return count
        }
        "line" -> if (renderLineNode(context, node)) count++
        "circle" -> if (renderCircleNode(context, node)) count++
        "image" -> if (renderImageNode(context, node, pageHeight = pageHeight)) count++
        "use" -> if (renderUseNode(context, node, pageHeight, defMap)) count++
        "tspan" -> {
        }
    }

    node.children.forEach { child ->
        count += renderSvgNodeTree(context, child, pageHeight, defMap)
    }

    platform.CoreGraphics.CGContextRestoreGState(context)

    return count
}

private fun collectDefs(defsNode: SvgNode, defMap: MutableMap<String, SvgNode>) {
    defsNode.children.forEach { child ->
        val id = child.attrs["id"]
        if (id != null) {
            defMap[id] = child
        }
        collectDefs(child, defMap)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun renderUseNode(
    context: platform.CoreGraphics.CGContextRef,
    node: SvgNode,
    pageHeight: Double,
    defMap: Map<String, SvgNode>
): Boolean {
    val href = node.attrs["href"] ?: node.attrs["xlink:href"] ?: return false

    if (!href.startsWith("#")) return false
    val refId = href.removePrefix("#")

    val referencedNode = defMap[refId] ?: return false

    platform.CoreGraphics.CGContextSaveGState(context)

    val useX = node.attrs["x"]?.toDoubleOrNull() ?: 0.0
    val useY = node.attrs["y"]?.toDoubleOrNull() ?: 0.0
    if (useX != 0.0 || useY != 0.0) {
        platform.CoreGraphics.CGContextTranslateCTM(context, useX, useY)
    }

    val success = when (referencedNode.name) {
        "image" -> renderImageNodeInUseContext(context, referencedNode)
        "rect" -> renderRectNode(context, referencedNode)
        "path" -> renderPathNode(context, referencedNode)
        "circle" -> renderCircleNode(context, referencedNode)
        "g" -> {
            var childCount = 0
            referencedNode.children.forEach { child ->
                childCount += renderSvgNodeTree(context, child, pageHeight, defMap.toMutableMap())
            }
            childCount > 0
        }
        else -> false
    }

    platform.CoreGraphics.CGContextRestoreGState(context)

    return success
}

@OptIn(ExperimentalForeignApi::class)
private fun renderImageNodeInUseContext(
    context: platform.CoreGraphics.CGContextRef,
    node: SvgNode
): Boolean {
    val nodeWidth = node.attrs["width"]?.toDoubleOrNull() ?: 0.0
    val nodeHeight = node.attrs["height"]?.toDoubleOrNull() ?: 0.0
    if (nodeWidth <= 0.0 || nodeHeight <= 0.0) return false

    val x = node.attrs["x"]?.toDoubleOrNull() ?: 0.0
    val y = node.attrs["y"]?.toDoubleOrNull() ?: 0.0

    val href = node.attrs["href"] ?: node.attrs["xlink:href"] ?: return false

    val uiImage = decodeSvgDataImageToUIImage(href) ?: return false
    val cgImage = uiImage.CGImage ?: return false

    val preserve = node.attrs["preserveAspectRatio"]?.trim()?.lowercase()
    val doPreserve = preserve == null || preserve.contains("meet")

    val (targetW, targetH, dx, dy) = if (!doPreserve) {
        listOf(nodeWidth, nodeHeight, 0.0, 0.0)
    } else {
        val imgW = CGImageGetWidth(cgImage).toDouble().coerceAtLeast(1.0)
        val imgH = CGImageGetHeight(cgImage).toDouble().coerceAtLeast(1.0)
        val scale = minOf(nodeWidth / imgW, nodeHeight / imgH)
        val tw = imgW * scale
        val th = imgH * scale
        listOf(tw, th, (nodeWidth - tw) / 2.0, (nodeHeight - th) / 2.0)
    }

    platform.CoreGraphics.CGContextSaveGState(context)

    platform.CoreGraphics.CGContextTranslateCTM(context, x + dx, y + dy)

    platform.CoreGraphics.CGContextTranslateCTM(context, 0.0, targetH)
    platform.CoreGraphics.CGContextScaleCTM(context, 1.0, -1.0)

    val drawRect = CGRectMake(0.0, 0.0, targetW, targetH)
    platform.CoreGraphics.CGContextDrawImage(context, drawRect, cgImage)

    platform.CoreGraphics.CGContextRestoreGState(context)

    return true
}

@OptIn(ExperimentalForeignApi::class)
private fun renderSvgNodeTree(
    context: platform.CoreGraphics.CGContextRef,
    node: SvgNode
): Int = renderSvgNodeTree(context, node, pageHeight = 0.0)

@OptIn(ExperimentalForeignApi::class)
private fun parseSvgLength(value: String?, reference: Double? = null): Double? {
    if (value == null) return null
    val v = value.trim()
    if (v.isEmpty()) return null

    return when {
        v.endsWith("%") -> {
            val pct = v.removeSuffix("%").toDoubleOrNull() ?: return null
            val ref = reference ?: return null
            (pct / 100.0) * ref
        }
        v.endsWith("px") -> v.removeSuffix("px").toDoubleOrNull()
        else -> v.toDoubleOrNull()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun renderImageNode(
    context: platform.CoreGraphics.CGContextRef,
    node: SvgNode,
    pageHeight: Double
): Boolean {
    val nodeWidth = node.attrs["width"]?.toDoubleOrNull() ?: 0.0
    val nodeHeight = node.attrs["height"]?.toDoubleOrNull() ?: 0.0
    if (nodeWidth <= 0.0 || nodeHeight <= 0.0) return false

    val x = node.attrs["x"]?.toDoubleOrNull() ?: 0.0
    val yTop = node.attrs["y"]?.toDoubleOrNull() ?: 0.0

    val href = node.attrs["href"] ?: node.attrs["xlink:href"] ?: return false

    val uiImage = decodeSvgDataImageToUIImage(href) ?: return false
    val cgImage = uiImage.CGImage ?: return false

    val preserve = node.attrs["preserveAspectRatio"]?.trim()?.lowercase()
    val doPreserve = preserve == null || preserve.contains("meet")

    val yBottom = pageHeight - yTop - nodeHeight

    val drawRect = if (!doPreserve) {
        CGRectMake(x, yBottom, nodeWidth, nodeHeight)
    } else {
        val imgW = CGImageGetWidth(cgImage).toDouble().coerceAtLeast(1.0)
        val imgH = CGImageGetHeight(cgImage).toDouble().coerceAtLeast(1.0)
        val scale = minOf(nodeWidth / imgW, nodeHeight / imgH)
        val targetW = imgW * scale
        val targetH = imgH * scale
        val dx = (nodeWidth - targetW) / 2.0
        val dy = (nodeHeight - targetH) / 2.0
        CGRectMake(x + dx, yBottom + dy, targetW, targetH)
    }

    platform.CoreGraphics.CGContextDrawImage(context, drawRect, cgImage)
    return true
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun decodeSvgDataImageToUIImage(href: String): UIImage? {
    val prefix = "data:"
    if (!href.startsWith(prefix)) return null

    val commaIndex = href.indexOf(',')
    if (commaIndex <= 0 || commaIndex >= href.length - 1) return null

    val meta = href.substring(5, commaIndex) // after "data:"
    val dataPart = href.substring(commaIndex + 1)

    if (!meta.contains("base64")) return null

    val nsData = NSData.create(
        base64EncodedString = dataPart,
        options = NSDataBase64DecodingIgnoreUnknownCharacters
    ) ?: return null

    return UIImage.imageWithData(nsData)
}

@OptIn(ExperimentalForeignApi::class)
private fun renderRectNode(
    context: platform.CoreGraphics.CGContextRef,
    node: SvgNode
): Boolean {
    val x = node.attrs["x"]?.toDoubleOrNull() ?: 0.0
    val y = node.attrs["y"]?.toDoubleOrNull() ?: 0.0
    val width = node.attrs["width"]?.toDoubleOrNull() ?: 0.0
    val height = node.attrs["height"]?.toDoubleOrNull() ?: 0.0
    if (width <= 0.0 || height <= 0.0) return false

    val fill = node.attrs["fill"]
    val stroke = node.attrs["stroke"]
    val strokeWidth = node.attrs["stroke-width"]?.toDoubleOrNull() ?: 1.0
    val opacity = node.attrs["opacity"]?.toDoubleOrNull() ?: 1.0
    val fillOpacity = node.attrs["fill-opacity"]?.toDoubleOrNull() ?: 1.0
    val strokeOpacity = node.attrs["stroke-opacity"]?.toDoubleOrNull() ?: 1.0
    val rect = CGRectMake(x, y, width, height)

    if (fill != null && fill != "none") {
        setFillColorWithOpacity(context, fill, opacity * fillOpacity)
        platform.CoreGraphics.CGContextFillRect(context, rect)
    }

    if (stroke != null && stroke != "none") {
        setStrokeColorWithOpacity(context, stroke, opacity * strokeOpacity)
        platform.CoreGraphics.CGContextSetLineWidth(context, strokeWidth)
        platform.CoreGraphics.CGContextStrokeRect(context, rect)
    }

    return true
}

@OptIn(ExperimentalForeignApi::class)
private fun renderPathNode(
    context: platform.CoreGraphics.CGContextRef,
    node: SvgNode
): Boolean {
    val d = node.attrs["d"] ?: return false
    val path = parseSvgPathData(d) ?: return false
    val fill = node.attrs["fill"]
    val stroke = node.attrs["stroke"]
    val strokeWidth = node.attrs["stroke-width"]?.toDoubleOrNull() ?: 1.0
    val opacity = node.attrs["opacity"]?.toDoubleOrNull() ?: 1.0
    val fillOpacity = node.attrs["fill-opacity"]?.toDoubleOrNull() ?: 1.0
    val strokeOpacity = node.attrs["stroke-opacity"]?.toDoubleOrNull() ?: 1.0

    if (fill != null && fill != "none") {
        setFillColorWithOpacity(context, fill, opacity * fillOpacity)
        platform.CoreGraphics.CGContextAddPath(context, path)
        platform.CoreGraphics.CGContextFillPath(context)
    }

    if (stroke != null && stroke != "none") {
        setStrokeColorWithOpacity(context, stroke, opacity * strokeOpacity)
        platform.CoreGraphics.CGContextSetLineWidth(context, strokeWidth)
        platform.CoreGraphics.CGContextAddPath(context, path)
        platform.CoreGraphics.CGContextStrokePath(context)
    }

    return true
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun renderTextNode(
    context: platform.CoreGraphics.CGContextRef,
    node: SvgNode
): Boolean {
    val baseX = parseSvgNumberListFirst(node.attrs["x"]) ?: 0.0
    val baseY = parseSvgNumberListFirst(node.attrs["y"]) ?: 0.0
    val fill = node.attrs["fill"] ?: "black"
    val fontSize = node.attrs["font-size"]?.replace("px", "")?.toDoubleOrNull() ?: 12.0
    val lineHeight = node.attrs["line-height"]?.replace("px", "")?.toDoubleOrNull() ?: (fontSize * 1.2)
    val preserveSpace = node.attrs["xml:space"] == "preserve"
    val opacity = node.attrs["opacity"]?.toDoubleOrNull() ?: 1.0
    val fillOpacity = node.attrs["fill-opacity"]?.toDoubleOrNull() ?: 1.0

    val (r, g, b, baseAlpha) = parseColor(fill)
    val finalAlpha = baseAlpha * opacity * fillOpacity
    val uiFont = resolveFont(node, fontSize)
    val uiColor = platform.UIKit.UIColor.colorWithRed(r, g, b, finalAlpha)

    val attributes = mapOf<Any?, Any?>(
        platform.UIKit.NSFontAttributeName to uiFont,
        platform.UIKit.NSForegroundColorAttributeName to uiColor
    )

    data class TextFragment(
        val text: String,
        val x: Double,
        val y: Double,
        val xPositions: List<Double>? = null
    )
    val fragments = mutableListOf<TextFragment>()

    fun normalizeSvgText(raw: String, preserve: Boolean): String {
        if (preserve) return raw
        return raw.replace(Regex("\\s+"), " ").trim()
    }

    val tspanNodes = node.children.filter { it.name == "tspan" }
    if (tspanNodes.isNotEmpty()) {
        var currentX = baseX
        var currentY = baseY
        tspanNodes.forEachIndexed { index, tspan ->
            val rawText = tspan.text
            if (rawText.isBlank()) return@forEachIndexed

            val hasExplicitY = tspan.attrs["y"] != null || tspan.attrs["dy"] != null
            val hasExplicitX = tspan.attrs["x"] != null || tspan.attrs["dx"] != null

            if (!hasExplicitY && index > 0) {
                currentY += lineHeight
            }

            parseSvgNumberListFirst(tspan.attrs["x"])?.let { currentX = it }
            parseSvgNumberListFirst(tspan.attrs["y"])?.let { currentY = it }
            tspan.attrs["dx"]?.toDoubleOrNull()?.let { currentX += it }
            tspan.attrs["dy"]?.toDoubleOrNull()?.let { currentY += it }

            if (!hasExplicitX && index > 0) {
                currentX = baseX
            }

            val preserve = preserveSpace || tspan.attrs["xml:space"] == "preserve"
            val rawLines = rawText.split('\n')
            rawLines.forEachIndexed { lineIndex, rawLine ->
                val text = normalizeSvgText(rawLine, preserve)
                if (text.isNotEmpty()) {
                    val xList = if (rawLines.size == 1) parseSvgNumberList(tspan.attrs["x"]) else emptyList()
                    val positions = if (xList.size >= text.length) xList else null
                    fragments.add(TextFragment(text, currentX, currentY, positions))
                }
                if (lineIndex < rawLines.size - 1) {
                    currentY += lineHeight
                }
            }
        }
    } else {
        val rawText = node.text
        if (rawText.isNotBlank()) {
            val text = normalizeSvgText(rawText, preserveSpace)
            val xList = parseSvgNumberList(node.attrs["x"])
            val positions = if (xList.size >= text.length) xList else null
            fragments.add(TextFragment(text, baseX, baseY, positions))
        }
    }

    if (fragments.isEmpty()) return false

    platform.CoreGraphics.CGContextSaveGState(context)

    fun drawTextAt(text: String, x: Double, y: Double) {
        val attributedString = platform.Foundation.NSAttributedString.create(
            string = text,
            attributes = attributes
        )

        @Suppress("UNCHECKED_CAST")
        val cfAttributedString = CFBridgingRetain(attributedString) as platform.CoreFoundation.CFAttributedStringRef

        val line = CTLineCreateWithAttributedString(cfAttributedString)
        CFRelease(cfAttributedString)

        platform.CoreGraphics.CGContextSetTextMatrix(
            context,
            platform.CoreGraphics.CGAffineTransformMake(1.0, 0.0, 0.0, -1.0, 0.0, 0.0)
        )
        platform.CoreGraphics.CGContextSetTextPosition(context, x, y)

        CTLineDraw(line, context)
        CFRelease(line)
    }

    fragments.forEach { fragment ->
        val positions = fragment.xPositions
        if (positions != null && positions.size >= fragment.text.length) {
            fragment.text.forEachIndexed { index, ch ->
                val xPos = positions[index]
                drawTextAt(ch.toString(), xPos, fragment.y)
            }
        } else {
            drawTextAt(fragment.text, fragment.x, fragment.y)
        }
    }

    platform.CoreGraphics.CGContextRestoreGState(context)

    return true
}

@OptIn(ExperimentalForeignApi::class)
private fun renderLineNode(
    context: platform.CoreGraphics.CGContextRef,
    node: SvgNode
): Boolean {
    val x1 = node.attrs["x1"]?.toDoubleOrNull() ?: 0.0
    val y1 = node.attrs["y1"]?.toDoubleOrNull() ?: 0.0
    val x2 = node.attrs["x2"]?.toDoubleOrNull() ?: 0.0
    val y2 = node.attrs["y2"]?.toDoubleOrNull() ?: 0.0
    val stroke = node.attrs["stroke"] ?: "black"
    val strokeWidth = node.attrs["stroke-width"]?.toDoubleOrNull() ?: 1.0
    val opacity = node.attrs["opacity"]?.toDoubleOrNull() ?: 1.0
    val strokeOpacity = node.attrs["stroke-opacity"]?.toDoubleOrNull() ?: 1.0

    setStrokeColorWithOpacity(context, stroke, opacity * strokeOpacity)
    platform.CoreGraphics.CGContextSetLineWidth(context, strokeWidth)
    platform.CoreGraphics.CGContextMoveToPoint(context, x1, y1)
    platform.CoreGraphics.CGContextAddLineToPoint(context, x2, y2)
    platform.CoreGraphics.CGContextStrokePath(context)

    return true
}

@OptIn(ExperimentalForeignApi::class)
private fun renderCircleNode(
    context: platform.CoreGraphics.CGContextRef,
    node: SvgNode
): Boolean {
    val cx = node.attrs["cx"]?.toDoubleOrNull() ?: 0.0
    val cy = node.attrs["cy"]?.toDoubleOrNull() ?: 0.0
    val r = node.attrs["r"]?.toDoubleOrNull() ?: 0.0
    if (r <= 0.0) return false

    val fill = node.attrs["fill"]
    val stroke = node.attrs["stroke"]
    val strokeWidth = node.attrs["stroke-width"]?.toDoubleOrNull() ?: 1.0
    val opacity = node.attrs["opacity"]?.toDoubleOrNull() ?: 1.0
    val fillOpacity = node.attrs["fill-opacity"]?.toDoubleOrNull() ?: 1.0
    val strokeOpacity = node.attrs["stroke-opacity"]?.toDoubleOrNull() ?: 1.0
    val rect = CGRectMake(cx - r, cy - r, r * 2, r * 2)

    if (fill != null && fill != "none") {
        setFillColorWithOpacity(context, fill, opacity * fillOpacity)
        platform.CoreGraphics.CGContextFillEllipseInRect(context, rect)
    }

    if (stroke != null && stroke != "none") {
        setStrokeColorWithOpacity(context, stroke, opacity * strokeOpacity)
        platform.CoreGraphics.CGContextSetLineWidth(context, strokeWidth)
        platform.CoreGraphics.CGContextStrokeEllipseInRect(context, rect)
    }

    return true
}


@OptIn(InternalComposeUiApi::class)
internal actual suspend fun measureContentHeight(
    context: PdfContext,
    widthPt: Float,
    content: @Composable () -> Unit
): Int = withContext(Dispatchers.Main) {
    val heightMeasured = CompletableDeferred<Int>()

    val maxMeasureHeight = PdfFormat.A4.heightPt.toInt()*100 // Large height to allow measuring content taller than screen

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

        val tempStream = DynamicMemoryWStream()
        val bounds = SkiaRect.makeWH(widthPt, maxMeasureHeight.toFloat())
        val tempCanvas = SVGCanvas.make(bounds, tempStream)
        scene.render(tempCanvas.asComposeCanvas(), nanoTime = 0L)
        tempCanvas.close()

        return@withContext withTimeout(10000) {
            heightMeasured.await()
        }
    } finally {
        scene.close()
    }
}



