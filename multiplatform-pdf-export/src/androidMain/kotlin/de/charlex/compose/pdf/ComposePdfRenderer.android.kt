package de.charlex.compose.pdf

import android.app.Activity
import android.graphics.pdf.PdfDocument
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream


internal actual suspend fun renderComposeToPdfMultiPage(
    context: PdfContext,
    widthPt: Float,
    heightPt: Float,
    pages: List<@Composable () -> Unit>
): ByteArray = withContext(Dispatchers.IO) {
    val outputStream = ByteArrayOutputStream()
    val pdfDocument = PdfDocument()

    try {
        pages.forEachIndexed { index, pageContent ->
            val pageInfo = PdfDocument.PageInfo.Builder(
                widthPt.toInt(),
                heightPt.toInt(),
                index + 1
            ).create()

            val page = pdfDocument.startPage(pageInfo)

            try {
                renderPageDirectToCanvas(
                    activity = context.activity,
                    pdfCanvas = page.canvas,
                    widthPx = widthPt.toInt(),
                    heightPx = heightPt.toInt(),
                    content = pageContent
                )
            } finally {
                pdfDocument.finishPage(page)
            }
        }

        pdfDocument.writeTo(outputStream)
        outputStream.flush()

        return@withContext outputStream.toByteArray()
    } finally {
        pdfDocument.close()
    }
}

private suspend fun renderPageDirectToCanvas(
    activity: Activity,
    pdfCanvas: android.graphics.Canvas,
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit
) = withContext(Dispatchers.Main) {
    val contentReady = CompletableDeferred<Unit>()
    val rootLayout: ViewGroup = activity.window.decorView.findViewById(android.R.id.content)

    val container = FrameLayout(activity).apply {
        fitsSystemWindows = false
        clipToPadding = false
        clipChildren = false
        setPadding(0, 0, 0, 0)
    }

    val composeView = ComposeView(activity).apply {
        fitsSystemWindows = false
    }

    var viewAttached = false

    try {
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(composeView) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ProvidePdfDefaults {
                    with(LocalDensity.current) {
                        Box(
                            modifier = Modifier
                                .size(widthPx.toDp(), heightPx.toDp())
                                .onGloballyPositioned { coords ->
                                    if (!contentReady.isCompleted) {
                                        contentReady.complete(Unit)
                                    }
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                content()
                            }
                        }
                    }
                }
            }
        }

        container.addView(
            composeView,
            FrameLayout.LayoutParams(widthPx, heightPx).apply {
                setMargins(0, 0, 0, 0)
            }
        )

        container.alpha = 0f
        rootLayout.addView(
            container,
            ViewGroup.LayoutParams(widthPx, heightPx)
        )
        container.translationX = 0f
        container.translationY = 0f
        viewAttached = true

        withTimeout(10000) {
            contentReady.await()
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)

        container.measure(widthSpec, heightSpec)
        container.layout(0, 0, widthPx, heightPx)

        composeView.measure(widthSpec, heightSpec)
        composeView.layout(0, 0, widthPx, heightPx)

        composeView.draw(pdfCanvas)
    } finally {
        if (viewAttached) {
            rootLayout.removeView(container)
        }
    }
}

internal actual suspend fun measureContentHeight(
    context: PdfContext,
    widthPt: Float,
    content: @Composable () -> Unit
): Int = withContext(Dispatchers.Main) {
    val widthPx = widthPt.toInt()
    val maxMeasureHeight = PdfFormat.A4.heightPt.toInt()*100 // Large height to allow measuring content taller than screen
    val heightMeasured = CompletableDeferred<Int>()
    val rootLayout: ViewGroup = context.activity.window.decorView.findViewById(android.R.id.content)

    val container = FrameLayout(context.activity).apply {
        fitsSystemWindows = false
        clipToPadding = false
        clipChildren = false
        setPadding(0, 0, 0, 0)
    }

    val composeView = ComposeView(context.activity).apply {
        fitsSystemWindows = false
    }

    var viewAttached = false

    try {
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(composeView) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                HeightMeasurmentLayout(
                    completeDeferred = {
                        heightMeasured.complete(it)
                    },
                    content = content
                )
            }
        }

        container.addView(
            composeView,
            FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 0)
            }
        )

        container.alpha = 0f
        rootLayout.addView(
            container,
            ViewGroup.LayoutParams(widthPx, maxMeasureHeight)
        )
        container.translationX = 0f
        container.translationY = 0f
        viewAttached = true

        return@withContext withTimeout(10000) {
            heightMeasured.await()
        }
    } finally {
        if (viewAttached) {
            rootLayout.removeView(container)
        }
    }
}
