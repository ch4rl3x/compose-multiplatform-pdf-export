package de.charlex.compose.composetopdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.charlex.compose.pdf.PdfContext
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FileKit.init(this)

        enableEdgeToEdge()

        val pdfContext = PdfContext(this)
        setContent {
            App(pdfContext)
        }
    }
}