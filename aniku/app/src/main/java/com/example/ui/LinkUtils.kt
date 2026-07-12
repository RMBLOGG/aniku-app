package com.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

// Mendeteksi URL (http://, https://, atau www.) di dalam teks
private val urlPattern = Regex(
    "((https?://|www\\.)[\\w\\-]+(\\.[\\w\\-]+)+[\\w\\-._~:/?#\\[\\]@!\$&'()*+,;=%]*)",
    RegexOption.IGNORE_CASE
)

/**
 * Mengubah teks biasa menjadi AnnotatedString di mana setiap URL
 * (diawali http://, https://, atau www.) menjadi link yang bisa diklik
 * dan akan membuka browser saat ditekan.
 */
@Composable
fun linkifyText(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary

    return buildAnnotatedString {
        var lastIndex = 0

        for (match in urlPattern.findAll(text)) {
            // Tambahkan teks sebelum link
            if (match.range.first > lastIndex) {
                append(text.substring(lastIndex, match.range.first))
            }

            var url = match.value
            // Hilangkan tanda baca penutup yang mungkin ikut tertangkap, mis. titik/koma di akhir kalimat
            var trailing = ""
            while (url.isNotEmpty() && url.last() in ".,!?;:)]}\u2019\u201d") {
                trailing = url.last() + trailing
                url = url.dropLast(1)
            }

            val fullUrl = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url

            withLink(
                LinkAnnotation.Url(
                    url = fullUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
            ) {
                append(url)
            }

            if (trailing.isNotEmpty()) {
                append(trailing)
            }

            lastIndex = match.range.last + 1
        }

        // Tambahkan sisa teks setelah link terakhir
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
