package de.charlex.compose.composetopdf

import androidx.compose.ui.window.ComposeUIViewController
import de.charlex.compose.pdf.PdfContext

fun MainViewController() = ComposeUIViewController { App(PdfContext()) }