# Compose Multiplatform PDF Export
<a href="https://github.com/ch4rl3x/compose-multiplatform-pdf-export/actions?query=workflow%3ABuild"><img src="https://github.com/ch4rl3x/compose-multiplatform-pdf-export/actions/workflows/build.yml/badge.svg" alt="Build"></a>
[![Maven Central](https://img.shields.io/maven-central/v/de.charlex.compose/compose-multiplatform-pdf-export)](https://central.sonatype.com/artifact/de.charlex.compose/compose-multiplatform-pdf-export)

A Compose Multiplatform library for exporting UI content to **vector** PDFs across Android, iOS, and JVM/desktop.

## Features
- Render any `@Composable` to a multi‑page PDF (vector output).
- Automatic pagination based on measured height.
- Optional **item‑based** pagination to keep blocks together.
- Custom page formats (`A4`, `Letter`, `Custom`, etc.) and orientation.
- Consistent output with Compose layouts and styling.

## Supported Targets
- Android (minSdk 26)
- iOS (iosX64, iosArm64, iosSimulatorArm64)
- JVM (desktop)

## Installation

### Gradle (Kotlin DSL)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("de.charlex.compose:multiplatform-pdf-export:<version>")
        }
    }
}
```

> Use the version shown in the Maven Central badge above.

## Usage

### 1) Render a single composable (auto page breaks)

```kotlin
val pdfBytes = renderComposeToPdf(
    context = PdfContext(),
    format = PdfFormat.A4,
    orientation = PdfOrientation.Portrait
) {
    Column {
        Text("Hello PDF")
        // ... long content
    }
}
```

### 2) Item‑based pagination (keep blocks together)

```kotlin
val pdfBytes = renderComposeToPdf(
    context = PdfContext(),
    format = PdfFormat.A4,
    orientation = PdfOrientation.Portrait
) {
    item { Header() }
    items(data) { row -> RowItem(row) }
    item { Footer() }
}
```

### Platform‑specific `PdfContext`

```kotlin
// Android
val pdfBytes = renderComposeToPdf(
    context = PdfContext(activity),
    content = { /* ... */ }
)

// iOS / JVM
val pdfBytes = renderComposeToPdf(
    context = PdfContext(),
    content = { /* ... */ }
)
```

## How It Works (High Level)
- **Android**: renders directly to `android.graphics.pdf.PdfDocument`.
- **iOS**: renders to SVG and draws into CoreGraphics PDF context.
- **JVM**: renders to SVG and converts to PDF (Batik/FOP), then merges pages (PDFBox).

## Notes on Fonts
PDF output uses the Compose rendering stack. If you need **identical typography across platforms**, consider providing explicit fonts in your Compose theme (instead of relying on system defaults).

## Sample Apps
See `sample/` for Android, iOS and JVM examples.

## License
MIT — see `LICENSE`.
