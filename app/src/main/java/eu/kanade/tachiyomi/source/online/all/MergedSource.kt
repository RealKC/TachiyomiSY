package eu.kanade.tachiyomi.source.online.all

import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.SuspendHttpSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import exh.MERGED_SOURCE_ID
import exh.merged.sql.models.MergedMangaReference
import exh.util.asFlow
import exh.util.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.withContext
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class MergedSource : SuspendHttpSource() {
    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val downloadManager: DownloadManager by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    override val id: Long = MERGED_SOURCE_ID

    override val baseUrl = ""

    override suspend fun popularMangaRequestSuspended(page: Int) = throw UnsupportedOperationException()
    override suspend fun popularMangaParseSuspended(response: Response) = throw UnsupportedOperationException()
    override suspend fun searchMangaRequestSuspended(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override suspend fun searchMangaParseSuspended(response: Response) = throw UnsupportedOperationException()
    override suspend fun latestUpdatesRequestSuspended(page: Int) = throw UnsupportedOperationException()
    override suspend fun latestUpdatesParseSuspended(response: Response) = throw UnsupportedOperationException()
    override suspend fun mangaDetailsParseSuspended(response: Response) = throw UnsupportedOperationException()
    override suspend fun chapterListParseSuspended(response: Response) = throw UnsupportedOperationException()
    override suspend fun pageListParseSuspended(response: Response) = throw UnsupportedOperationException()
    override suspend fun imageUrlParseSuspended(response: Response) = throw UnsupportedOperationException()
    override fun fetchChapterListFlow(manga: SManga) = throw UnsupportedOperationException()
    override fun fetchImageFlow(page: Page) = throw UnsupportedOperationException()
    override fun fetchImageUrlFlow(page: Page) = throw UnsupportedOperationException()
    override fun fetchPageListFlow(chapter: SChapter) = throw UnsupportedOperationException()
    override fun fetchLatestUpdatesFlow(page: Int) = throw UnsupportedOperationException()
    override fun fetchPopularMangaFlow(page: Int) = throw UnsupportedOperationException()

    override fun fetchMangaDetailsFlow(manga: SManga): Flow<SManga> {
        return flow {
            val mergedManga = db.getManga(manga.url, id).await() ?: throw Exception("merged manga not in db")
            val mangaReferences = mergedManga.id?.let { withContext(Dispatchers.IO) { db.getMergedMangaReferences(it).await() } } ?: throw Exception("merged manga id is null")
            if (mangaReferences.isEmpty()) throw IllegalArgumentException("Manga references are empty, info unavailable, merge is likely corrupted")
            if (mangaReferences.size == 1 || {
                val mangaReference = mangaReferences.firstOrNull()
                mangaReference == null || (mangaReference.mangaSourceId == MERGED_SOURCE_ID)
            }()
            ) throw IllegalArgumentException("Manga references contain only the merged reference, merge is likely corrupted")

            emit(
                SManga.create().apply {
                    val mangaInfoReference = mangaReferences.firstOrNull { it.isInfoManga } ?: mangaReferences.firstOrNull { it.mangaId != it.mergeId }
                    val dbManga = mangaInfoReference?.let { withContext(Dispatchers.IO) { db.getManga(it.mangaUrl, it.mangaSourceId).await() } }
                    this.copyFrom(dbManga ?: mergedManga)
                    url = manga.url
                }
            )
        }
    }

    fun getChaptersFromDB(manga: Manga, editScanlators: Boolean = false, dedupe: Boolean = true): Flow<List<Chapter>> {
        // TODO more chapter dedupe
        return db.getChaptersByMergedMangaId(manga.id!!).asRxObservable()
            .asFlow()
            .map { chapterList ->
                val mangaReferences = withContext(Dispatchers.IO) { db.getMergedMangaReferences(manga.id!!).await() }
                val sources = mangaReferences.map { sourceManager.getOrStub(it.mangaSourceId) to it.mangaId }
                if (editScanlators) {
                    chapterList.onEach { chapter ->
                        val source = sources.firstOrNull { chapter.manga_id == it.second }?.first
                        if (source != null) {
                            chapter.scanlator = if (chapter.scanlator.isNullOrBlank()) source.name
                            else "$source: ${chapter.scanlator}"
                        }
                    }
                }
                if (dedupe) dedupeChapterList(mangaReferences, chapterList) else chapterList
            }
    }

    private fun dedupeChapterList(mangaReferences: List<MergedMangaReference>, chapterList: List<Chapter>): List<Chapter> {
        return when (mangaReferences.firstOrNull { it.mangaSourceId == MERGED_SOURCE_ID }?.chapterSortMode) {
            MergedMangaReference.CHAPTER_SORT_NO_DEDUPE, MergedMangaReference.CHAPTER_SORT_NONE -> chapterList
            MergedMangaReference.CHAPTER_SORT_PRIORITY -> chapterList
            MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS -> {
                findSourceWithMostChapters(chapterList)?.let { mangaId ->
                    chapterList.filter { it.manga_id == mangaId }
                } ?: chapterList
            }
            MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER -> {
                findSourceWithHighestChapterNumber(chapterList)?.let { mangaId ->
                    chapterList.filter { it.manga_id == mangaId }
                } ?: chapterList
            }
            else -> chapterList
        }
    }

    private fun findSourceWithMostChapters(chapterList: List<Chapter>): Long? {
        return chapterList.groupBy { it.manga_id }.maxByOrNull { it.value.size }?.key
    }

    private fun findSourceWithHighestChapterNumber(chapterList: List<Chapter>): Long? {
        return chapterList.maxByOrNull { it.chapter_number }?.manga_id
    }

    fun fetchChaptersForMergedManga(manga: Manga, downloadChapters: Boolean = true, editScanlators: Boolean = false, dedupe: Boolean = true): Flow<List<Chapter>> {
        return flow {
            withContext(Dispatchers.IO) {
                fetchChaptersAndSync(manga, downloadChapters).collect()
            }
            emit(
                getChaptersFromDB(manga, editScanlators, dedupe).singleOrNull() ?: emptyList<Chapter>()
            )
        }
    }

    suspend fun fetchChaptersAndSync(manga: Manga, downloadChapters: Boolean = true): Flow<Pair<List<Chapter>, List<Chapter>>> {
        val mangaReferences = db.getMergedMangaReferences(manga.id!!).await()
        if (mangaReferences.isEmpty()) throw IllegalArgumentException("Manga references are empty, chapters unavailable, merge is likely corrupted")

        val ifDownloadNewChapters = downloadChapters && manga.shouldDownloadNewChapters(db, preferences)
        return mangaReferences.filter { it.mangaSourceId != MERGED_SOURCE_ID }.asFlow().map {
            load(db, sourceManager, it)
        }.buffer().flatMapMerge { loadedManga ->
            withContext(Dispatchers.IO) {
                if (loadedManga.manga != null && loadedManga.reference.getChapterUpdates) {
                    loadedManga.source.fetchChapterList(loadedManga.manga).asFlow()
                        .map { syncChaptersWithSource(db, it, loadedManga.manga, loadedManga.source) }
                        .onEach {
                            if (ifDownloadNewChapters && loadedManga.reference.downloadChapters) {
                                downloadManager.downloadChapters(loadedManga.manga, it.first)
                            }
                        }
                } else {
                    emptyList<Pair<List<Chapter>, List<Chapter>>>().asFlow()
                }
            }
        }.buffer()
    }

    suspend fun load(db: DatabaseHelper, sourceManager: SourceManager, reference: MergedMangaReference): LoadedMangaSource {
        var manga = db.getManga(reference.mangaUrl, reference.mangaSourceId).await()
        val source = sourceManager.getOrStub(manga?.source ?: reference.mangaSourceId)
        if (manga == null) {
            manga = Manga.create(reference.mangaSourceId).apply {
                url = reference.mangaUrl
            }
            manga.copyFrom(source.fetchMangaDetails(manga).asFlow().single())
            try {
                manga.id = db.insertManga(manga).await().insertedId()
                reference.mangaId = manga.id
                db.insertNewMergedMangaId(reference).await()
            } catch (e: Exception) {
                XLog.st(e.stackTrace.contentToString(), 5)
            }
        }
        return LoadedMangaSource(source, manga, reference)
    }

    data class LoadedMangaSource(val source: Source, val manga: Manga?, val reference: MergedMangaReference)

    override val lang = "all"
    override val supportsLatest = false
    override val name = "MergedSource"
}
