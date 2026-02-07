package de.charlex.compose.composetopdf

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.charlex.compose.pdf.PdfContext
import io.github.vinceglb.filekit.FileKit

fun main() = application {

    FileKit.init(appId = "MyApplication")

    Window(
        onCloseRequest = ::exitApplication,
        title = "Compose to PDF Sample"
    ) {
        MaterialTheme {
            App(PdfContext())
        }
    }
}
