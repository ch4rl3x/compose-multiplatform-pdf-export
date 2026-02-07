package de.charlex.compose.composetopdf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.charlex.compose.pdf.PdfContext
import de.charlex.compose.pdf.renderComposeToPdf
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.launch

@Composable
fun App(pdfContext: PdfContext) {
    val coroutineScope = rememberCoroutineScope()
    Scaffold {
        Row() {
            Button(
                modifier = Modifier.padding(it),
                onClick = {
                    coroutineScope.launch {
                        val composePdfByteArray = renderComposeToPdf(
                            context = pdfContext,
                            content = {
                                Column {
                                    Button(
                                        modifier = Modifier.padding(50.dp),
                                        onClick = {}
                                    ) {
                                        Text(
                                            "Render PDF",
                                            style = TextStyle(
                                                fontFeatureSettings = "kern, liga, tnum, lnum",
                                                letterSpacing = LocalTextStyle.current.letterSpacing.times(2)
                                            )
                                        )
                                    }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Row {
                                            Text(
                                                modifier = Modifier.weight(1f),
                                                text = "Start"
                                            )
                                            Text(
                                                modifier = Modifier.weight(1f),
                                                text = "End",
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.padding(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Row {
                                            Text(
                                                modifier = Modifier.weight(1f),
                                                text = "Start"
                                            )
                                            Text(
                                                modifier = Modifier.weight(1f),
                                                text = "End",
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }

                                    (0..250).forEach {
                                        Text(
                                            text = "$it"
                                        )
                                    }
                                }
                            }
                        )
                        val fileName = "Test.pdf"
                        val file = PlatformFile(FileKit.cacheDir, fileName)
                        file.delete(mustExist = false)
                        file.apply {
                            write(composePdfByteArray)
                        }
                        FileKit.openFileWithDefaultApplication(file)
                    }
                }
            ) {
                Text("Render Content")
            }

            Button(
                modifier = Modifier.padding(it),
                onClick = {
                    coroutineScope.launch {
                        val composePdfByteArray = renderComposeToPdf(
                            context = pdfContext,
                            scopedContent = {
                                item {
                                    Button(
                                        modifier = Modifier.padding(50.dp),
                                        onClick = {}
                                    ) {
                                        Text("Render PDF")
                                    }
                                }
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Row {
                                            Text(
                                                modifier = Modifier.weight(1f),
                                                text = "Start"
                                            )
                                            Text(
                                                modifier = Modifier.weight(1f),
                                                text = "End",
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                }
                                item {
                                    Card(
                                        modifier = Modifier.padding(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Row {
                                            Text(
                                                modifier = Modifier.weight(1f),
                                                text = "Start"
                                            )
                                            Text(
                                                modifier = Modifier.weight(1f),
                                                text = "End",
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }

                                    (0..250).forEach {
                                        Text(
                                            text = "$it"
                                        )
                                    }
                                }
                            }
                        )
                        val fileName = "Test2.pdf"
                        val file = PlatformFile(FileKit.cacheDir, fileName)
                        file.delete(mustExist = false)
                        file.apply {
                            write(composePdfByteArray)
                        }
                        FileKit.openFileWithDefaultApplication(file)
                    }
                }
            ) {
                Text("Render Scoped Content")
            }
        }

    }
}
