package exh.util

import android.content.Context
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.TAG_TYPE_LIGHT
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.TAG_TYPE_NORMAL
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.TAG_TYPE_WEAK

/**
 * Replaces chips in a ChipGroup.
 *
 * @param items List of strings that are shown as individual chips.
 * @param onClick Optional on click listener for each chip.
 * @param onLongClick Optional long click listener for each chip.
 * @param sourceId Optional source check to determine if we need special search functions for each chip.
 */
fun ChipGroup.setChipsExtended(items: List<String>?, onClick: (item: String) -> Unit = {}, onLongClick: (item: String) -> Unit = {}, sourceId: Long = 0L) {
    removeAllViews()

    items?.forEach { item ->
        val chip = makeSearchChip(item, onClick, onLongClick, sourceId, context)
        addView(chip)
    }
}

fun makeSearchChip(item: String, onClick: (item: String) -> Unit = {}, onLongClick: (item: String) -> Unit = {}, sourceId: Long, context: Context, namespace: String? = null, type: Int? = null): Chip {
    return Chip(context).apply {
        text = item
        val search = (if (namespace != null) SourceTagsUtil().getWrappedTag(sourceId, namespace = namespace, tag = item) else SourceTagsUtil().getWrappedTag(sourceId, fullTag = item)) ?: item
        setOnClickListener { onClick(search) }
        setOnLongClickListener {
            onLongClick(search)
            false
        }
        if (sourceId == EXH_SOURCE_ID || sourceId == EH_SOURCE_ID) {
            chipStrokeWidth = when (type) {
                TAG_TYPE_NORMAL -> 5F
                TAG_TYPE_LIGHT -> 3F
                TAG_TYPE_WEAK -> 0F
                else -> chipStrokeWidth
            }
        }
    }
}
