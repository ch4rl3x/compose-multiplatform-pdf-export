package de.charlex.compose.pdf

import androidx.compose.runtime.Composable

/**
 * Scope for building PDF content with automatic page breaks.
 * Each item added via [item] will be kept together on a single page.
 * If an item doesn't fit on the current page, it will be moved to the next page.
 */
class PdfContentScope {
    internal val items = mutableListOf<@Composable () -> Unit>()

    /**
     * Adds a composable block that should be kept together on a single page.
     * If this block doesn't fit on the current page, it will be placed on the next page.
     */
    fun item(content: @Composable () -> Unit) {
        items.add(content)
    }

    /**
     * Adds multiple items from a list.
     */
    fun <T> items(list: List<T>, itemContent: @Composable (T) -> Unit) {
        list.forEach { item ->
            items.add { itemContent(item) }
        }
    }

    /**
     * Adds multiple items with index from a list.
     */
    fun <T> itemsIndexed(list: List<T>, itemContent: @Composable (index: Int, item: T) -> Unit) {
        list.forEachIndexed { index, item ->
            items.add { itemContent(index, item) }
        }
    }
}

/**
 * Data class representing a measured item with its height.
 */
internal data class MeasuredItem(
    val index: Int,
    val height: Int,
    val content: @Composable () -> Unit
)

/**
 * Data class representing a page with its items.
 */
internal data class PageContent(
    val items: List<@Composable () -> Unit>
)
