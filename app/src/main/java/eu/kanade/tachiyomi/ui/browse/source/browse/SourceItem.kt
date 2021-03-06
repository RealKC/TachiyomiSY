package eu.kanade.tachiyomi.ui.browse.source.browse

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.tfcporciuncula.flow.Preference
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferenceValues.DisplayMode
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.android.synthetic.main.source_compact_grid_item.view.card
import kotlinx.android.synthetic.main.source_compact_grid_item.view.gradient
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SourceItem(val manga: Manga, private val displayMode: Preference<DisplayMode> /* SY --> */, private val metadata: RaisedSearchMetadata? = null /* SY <-- */) :
    AbstractFlexibleItem<SourceHolder>() {
    // SY -->
    val preferences: PreferencesHelper by injectLazy()
    // SY <--

    override fun getLayoutRes(): Int {
        return /* SY --> */ if ((manga.source == EH_SOURCE_ID || manga.source == EXH_SOURCE_ID) && preferences.enhancedEHentaiView().get()) R.layout.source_enhanced_ehentai_list_item
        else /* SY <-- */ when (displayMode.get()) {
            DisplayMode.COMPACT_GRID -> R.layout.source_compact_grid_item
            DisplayMode.COMFORTABLE_GRID, /* SY --> */ DisplayMode.NO_TITLE_GRID /* SY <-- */ -> R.layout.source_comfortable_grid_item
            DisplayMode.LIST -> R.layout.source_list_item
        }
    }

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
    ): SourceHolder {
        return /* SY --> */ if ((manga.source == EH_SOURCE_ID || manga.source == EXH_SOURCE_ID) && preferences.enhancedEHentaiView().get()) {
            SourceEnhancedEHentaiListHolder(view, adapter)
        } else /* SY <-- */ when (displayMode.get()) {
            DisplayMode.COMPACT_GRID -> {
                val parent = adapter.recyclerView as AutofitRecyclerView
                val coverHeight = parent.itemWidth / 3 * 4
                view.apply {
                    card.layoutParams = FrameLayout.LayoutParams(
                        MATCH_PARENT,
                        coverHeight
                    )
                    gradient.layoutParams = FrameLayout.LayoutParams(
                        MATCH_PARENT,
                        coverHeight / 2,
                        Gravity.BOTTOM
                    )
                }
                SourceGridHolder(view, adapter)
            }
            DisplayMode.COMFORTABLE_GRID /* SY --> */, DisplayMode.NO_TITLE_GRID /* SY <-- */ -> {
                val parent = adapter.recyclerView as AutofitRecyclerView
                val coverHeight = parent.itemWidth / 3 * 4
                view.apply {
                    card.layoutParams = ConstraintLayout.LayoutParams(
                        MATCH_PARENT,
                        coverHeight
                    )
                }
                SourceComfortableGridHolder(view, adapter, displayMode.get() != DisplayMode.NO_TITLE_GRID)
            }
            DisplayMode.LIST -> {
                SourceListHolder(view, adapter)
            }
        }
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: SourceHolder,
        position: Int,
        payloads: List<Any?>?
    ) {
        holder.onSetValues(manga)
        // SY -->
        if (metadata != null) {
            holder.onSetMetadataValues(manga, metadata)
        }
        // SY <--
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SourceItem) {
            return manga.id!! == other.manga.id!!
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id!!.hashCode()
    }
}
