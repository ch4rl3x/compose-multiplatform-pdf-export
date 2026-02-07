package de.charlex.compose.pdf

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val PdfMainDispatcher: CoroutineDispatcher = Dispatchers.Default
