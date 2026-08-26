package cz.teply.sheetset

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

internal object IncomingPdfIntent {
    fun uris(intent: Intent): List<Uri> {
        if (intent.type != PDF_MIME_TYPE || intent.action !in ACCEPTED_ACTIONS) return emptyList()

        return try {
            buildList {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let(::add)
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let(::addAll)
                intent.data?.let(::add)
                intent.clipData?.let { clipData ->
                    repeat(clipData.itemCount) { index ->
                        clipData.getItemAt(index).uri?.let(::add)
                    }
                }
            }.filter { it.scheme == ContentResolver.SCHEME_CONTENT }.distinct()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    private const val PDF_MIME_TYPE = "application/pdf"
    private val ACCEPTED_ACTIONS = setOf(
        Intent.ACTION_SEND,
        Intent.ACTION_SEND_MULTIPLE,
        Intent.ACTION_VIEW,
    )
}
