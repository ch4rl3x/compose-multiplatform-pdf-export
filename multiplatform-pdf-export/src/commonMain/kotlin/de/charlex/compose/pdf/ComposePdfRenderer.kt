package de.charlex.compose.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext
import kotlin.math.ceil


internal expect suspend fun renderComposeToPdfMultiPage(
    context: PdfContext,
    widthPt: Float,
    heightPt: Float,
    pages: List<@Composable () -> Unit>
): ByteArray

internal expect suspend fun measureContentHeight(
    context: PdfContext,
    widthPt: Float,
    content: @Composable () -> Unit
): Int

@OptIn(InternalComposeUiApi::class)
suspend fun renderComposeToPdf(
    context: PdfContext,
    format: PdfFormat = PdfFormat.A4,
    orientation: PdfOrientation = PdfOrientation.Portrait,
    content: @Composable () -> Unit
): ByteArray = withContext(PdfMainDispatcher) {

    val widthPt = format.effectiveWidth(orientation)
    val heightPt = format.effectiveHeight(orientation)

    val totalHeight = measureContentHeight(context, widthPt, content)

    val pageHeightPx = heightPt.toInt()

    val pageCount = ceil(totalHeight.toFloat() / pageHeightPx).toInt().coerceAtLeast(1)


    val pages = (0 until pageCount).map { pageIndex ->
        // Capture yOffset in a local val that the composable lambda can close over
        val yOffsetForPage = pageIndex * pageHeightPx

        @Composable {
            val density = LocalDensity.current
            with(density) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .clipToBounds(),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .requiredHeight(totalHeight.toDp())
                            .offset(x = 0.dp, y = with(density) {
                                -yOffsetForPage.toDp()
                            })
                    ) {
                        content()
                    }
                }
            }
        }
    }

    return@withContext renderComposeToPdfMultiPage(context, widthPt, heightPt, pages)
}

/**
 * Renders composable content to a PDF with automatic page breaks.
 * Items that don't fit on the current page are moved to the next page instead of being cut off.
 *
 * @param context The platform-specific PDF context
 * @param format The page format (default: A4). Use [PdfFormat.A4], [PdfFormat.Letter], or [PdfFormat.Custom] for custom sizes.
 * @param orientation The page orientation (default: Portrait)
 * @param content The content builder using [PdfContentScope]
 *
 * Usage:
 * ```
 * renderComposeToPdf(context, format = PdfFormat.A4, orientation = PdfOrientation.Landscape) {
 *     item { Header() }
 *     items(dataList) { data -> DataRow(data) }
 *     item { Footer() }
 * }
 * ```
 */
@OptIn(InternalComposeUiApi::class)
suspend fun renderComposeToPdf(
    context: PdfContext,
    format: PdfFormat = PdfFormat.A4,
    orientation: PdfOrientation = PdfOrientation.Portrait,
    scopedContent: PdfContentScope.() -> Unit
): ByteArray = withContext(PdfMainDispatcher) {
    val widthPt = format.effectiveWidth(orientation)
    val heightPt = format.effectiveHeight(orientation)

    val scope = PdfContentScope().apply(scopedContent)
    val items = scope.items

    if (items.isEmpty()) {
        return@withContext renderComposeToPdfMultiPage(context, widthPt, heightPt, emptyList())
    }

    // Measure all items
    val measuredItems = items.mapIndexed { index, itemContent ->
        val height = measureContentHeight(context, widthPt, itemContent)
        MeasuredItem(index, height, itemContent)
    }

    // Distribute items to pages
    val pageHeightPx = heightPt.toInt()
    val pages = mutableListOf<PageContent>()
    var currentPageItems = mutableListOf<@Composable () -> Unit>()
    var currentPageHeight = 0

    for (measuredItem in measuredItems) {
        val itemHeight = measuredItem.height

        // Check if item fits on current page
        if (currentPageHeight + itemHeight <= pageHeightPx) {
            currentPageItems.add(measuredItem.content)
            currentPageHeight += itemHeight
        } else {
            // Item doesn't fit - start new page
            if (currentPageItems.isNotEmpty()) {
                pages.add(PageContent(currentPageItems.toList()))
            }
            currentPageItems = mutableListOf(measuredItem.content)
            currentPageHeight = itemHeight

            // Handle items larger than a page
            if (itemHeight > pageHeightPx) {
                // Item is too large for a single page, add it anyway
                pages.add(PageContent(currentPageItems.toList()))
                currentPageItems = mutableListOf()
                currentPageHeight = 0
            }
        }
    }

    // Add remaining items as last page
    if (currentPageItems.isNotEmpty()) {
        pages.add(PageContent(currentPageItems.toList()))
    }

    // Convert PageContent to composable pages
    val composablePages = pages.map { pageContent ->
        @Composable {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                pageContent.items.forEach { itemComposable ->
                    itemComposable()
                }
            }
        }
    }

    return@withContext renderComposeToPdfMultiPage(context, widthPt, heightPt, composablePages)
}

@Composable
internal fun ProvidePdfDefaults(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDensity provides PdfDensity,
        LocalTextStyle provides LocalTextStyle.current.copy(
            fontFeatureSettings = "kern, liga",
        ),
        content = content
    )
}

@Composable
internal fun HeightMeasurmentLayout(completeDeferred: (Int) -> Unit, content: @Composable () -> Unit) {
    ProvidePdfDefaults {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val measuredHeight = coords.size.height
                    completeDeferred(measuredHeight.toInt())
                }
        ) {
            content()
        }
    }
}
