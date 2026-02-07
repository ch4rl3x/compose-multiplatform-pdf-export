package de.charlex.compose.pdf

import androidx.compose.ui.unit.Density

/**
 * Represents the orientation of a PDF page.
 */
enum class PdfOrientation {
    Portrait,
    Landscape
}

val PdfDensity: Density = Density(density = 1f, fontScale = 1f)

val PpfDensityTransform = 72f / 160f

/**
 * Represents a PDF page format with width and height in points (pt).
 * Standard PDF uses 72 points per inch.
 *
 * @property widthPt The width in points (portrait orientation)
 * @property heightPt The height in points (portrait orientation)
 */
sealed class PdfFormat(
    val widthPt: Float,
    val heightPt: Float
) {
    /**
     * Returns the effective width based on orientation.
     */
    fun effectiveWidth(orientation: PdfOrientation = PdfOrientation.Portrait): Float {
        return when (orientation) {
            PdfOrientation.Portrait -> widthPt / PpfDensityTransform
            PdfOrientation.Landscape -> heightPt / PpfDensityTransform
        }
    }

    /**
     * Returns the effective height based on orientation.
     */
    fun effectiveHeight(orientation: PdfOrientation = PdfOrientation.Portrait): Float {
        return when (orientation) {
            PdfOrientation.Portrait -> heightPt / PpfDensityTransform
            PdfOrientation.Landscape -> widthPt / PpfDensityTransform
        }
    }

    // ISO 216 A-Series (most common)
    data object A0 : PdfFormat(2384f, 3370f)
    data object A1 : PdfFormat(1684f, 2384f)
    data object A2 : PdfFormat(1191f, 1684f)
    data object A3 : PdfFormat(842f, 1191f)
    data object A4 : PdfFormat(595f, 842f)
    data object A5 : PdfFormat(420f, 595f)
    data object A6 : PdfFormat(298f, 420f)
    data object A7 : PdfFormat(210f, 298f)
    data object A8 : PdfFormat(148f, 210f)

    // ISO 216 B-Series
    data object B0 : PdfFormat(2835f, 4008f)
    data object B1 : PdfFormat(2004f, 2835f)
    data object B2 : PdfFormat(1417f, 2004f)
    data object B3 : PdfFormat(1001f, 1417f)
    data object B4 : PdfFormat(709f, 1001f)
    data object B5 : PdfFormat(499f, 709f)

    // North American sizes
    data object Letter : PdfFormat(612f, 792f)
    data object Legal : PdfFormat(612f, 1008f)
    data object Tabloid : PdfFormat(792f, 1224f)
    data object Ledger : PdfFormat(1224f, 792f)
    data object Executive : PdfFormat(522f, 756f)

    /**
     * Custom page format with user-defined dimensions.
     *
     * @param widthPt Width in points
     * @param heightPt Height in points
     */
    data class Custom(
        private val width: Float,
        private val height: Float
    ) : PdfFormat(width, height)

    companion object {
        /**
         * Creates a custom format from dimensions in millimeters.
         * @param widthMm Width in millimeters
         * @param heightMm Height in millimeters
         */
        fun fromMillimeters(widthMm: Float, heightMm: Float): Custom {
            // 1 inch = 25.4 mm, 1 inch = 72 pt
            val widthPt = widthMm * 72f / 25.4f
            val heightPt = heightMm * 72f / 25.4f
            return Custom(widthPt, heightPt)
        }

        /**
         * Creates a custom format from dimensions in inches.
         * @param widthInches Width in inches
         * @param heightInches Height in inches
         */
        fun fromInches(widthInches: Float, heightInches: Float): Custom {
            // 1 inch = 72 pt
            val widthPt = widthInches * 72f
            val heightPt = heightInches * 72f
            return Custom(widthPt, heightPt)
        }
    }
}
