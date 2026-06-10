package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.SettingsStore
import com.example.ui.theme.UserSession
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class AnikuViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    val settingsStore = SettingsStore(appContext)
    val bookmarkManager = BookmarkManager(appContext)

    private val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVjenhhaXlpYm53Z3ljb2R0Y3ZtIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODA4MzYxNzAsImV4cCI6MjA5NjQxMjE3MH0.UUPfyZ4GJO6y8I5467p_piCxtyuyM5oYGX_-jPeiZRw"

    // Settings flows
    val isDark = settingsStore.isDarkFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val textSize = settingsStore.textSizeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "Sedang")
    val accentColorName = settingsStore.accentColorFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "Red")

    // Session flow
    val session = settingsStore.sessionFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserSession(null, null, null, null, null, false, false)
    )

    // ── SOURCE STATE ────────────────────────────────────────────────
    private val _activeSource = MutableStateFlow(AnimeSource.ANIMASU)
    val activeSource: StateFlow<AnimeSource> = _activeSource.asStateFlow()

    private val _sourcesStatus = MutableStateFlow(
        AnimeSource.values().map { AnimeSourceInfo(it, SourceStatus.CHECKING) }
    )
    val sourcesStatus: StateFlow<List<AnimeSourceInfo>> = _sourcesStatus.asStateFlow()

    // Bookmarks state (local)
    private val _bookmarks = MutableStateFlow<List<BookmarkedAnime>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkedAnime>> = _bookmarks.asStateFlow()

    // Blacklisted slugs state from Supabase
    private val _blacklistedSlugs = MutableStateFlow<Set<String>>(emptySet())
    val blacklistedSlugs: StateFlow<Set<String>> = _blacklistedSlugs.asStateFlow()

    // Home state
    private val _homeOngoing = MutableStateFlow<List<AnimeRaw>>(emptyList())
    val homeOngoing: StateFlow<List<AnimeRaw>> = _homeOngoing.asStateFlow()

    private val _homeRecent = MutableStateFlow<List<AnimeRaw>>(emptyList())
    val homeRecent: StateFlow<List<AnimeRaw>> = _homeRecent.asStateFlow()

    private val _homePopular = MutableStateFlow<List<AnimeRaw>>(emptyList())
    val homePopular: StateFlow<List<AnimeRaw>> = _homePopular.asStateFlow()

    private val _homeMovies = MutableStateFlow<List<AnimeRaw>>(emptyList())
    val homeMovies: StateFlow<List<AnimeRaw>> = _homeMovies.asStateFlow()

    private val _featuredSlides = MutableStateFlow<List<FeaturedAnimeDto>>(emptyList())
    val featuredSlides: StateFlow<List<FeaturedAnimeDto>> = _featuredSlides.asStateFlow()

    private val _activeAnnouncement = MutableStateFlow<AnnouncementDto?>(null)
    val activeAnnouncement: StateFlow<AnnouncementDto?> = _activeAnnouncement.asStateFlow()

    private val _isHomeLoading = MutableStateFlow(false)
    val isHomeLoading: StateFlow<Boolean> = _isHomeLoading.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError.asStateFlow()

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AnimeRaw>>(emptyList())
    val searchResults: StateFlow<List<AnimeRaw>> = _searchResults.asStateFlow()

    private val _searchPopular = MutableStateFlow<List<AnimeRaw>>(emptyList())
    val searchPopular: StateFlow<List<AnimeRaw>> = _searchPopular.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    // Explore state
    private val _exploreTab = MutableStateFlow("Ongoing")
    val exploreTab: StateFlow<String> = _exploreTab.asStateFlow()

    private val _selectedGenreSlug = MutableStateFlow<String?>(null)
    val selectedGenreSlug: StateFlow<String?> = _selectedGenreSlug.asStateFlow()

    private val _genres = MutableStateFlow<List<GenreRaw>>(emptyList())
    val genres: StateFlow<List<GenreRaw>> = _genres.asStateFlow()

    private val _exploreAnimes = MutableStateFlow<List<AnimeRaw>>(emptyList())
    val exploreAnimes: StateFlow<List<AnimeRaw>> = _exploreAnimes.asStateFlow()

    private val _isExploreLoading = MutableStateFlow(false)
    val isExploreLoading: StateFlow<Boolean> = _isExploreLoading.asStateFlow()

    private val _explorePage = MutableStateFlow(1)
    private val _exploreHasNext = MutableStateFlow(true)
    val exploreHasNext: StateFlow<Boolean> = _exploreHasNext.asStateFlow()

    // Schedule state
    private val _selectedDay = MutableStateFlow("Minggu")
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    private val _scheduleMap = MutableStateFlow<Map<String, List<AnimeRaw>>>(emptyMap())
    val scheduleMap: StateFlow<Map<String, List<AnimeRaw>>> = _scheduleMap.asStateFlow()

    private val _isScheduleLoading = MutableStateFlow(false)
    val isScheduleLoading: StateFlow<Boolean> = _isScheduleLoading.asStateFlow()

    // Detail state
    private val _animeDetail = MutableStateFlow<DetailData?>(null)
    val animeDetail: StateFlow<DetailData?> = _animeDetail.asStateFlow()

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading: StateFlow<Boolean> = _isDetailLoading.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    // Streaming state
    private val _streamEpisodeTitle = MutableStateFlow<String?>(null)
    val streamEpisodeTitle: StateFlow<String?> = _streamEpisodeTitle.asStateFlow()

    private val _streams = MutableStateFlow<List<StreamRaw>>(emptyList())
    val streams: StateFlow<List<StreamRaw>> = _streams.asStateFlow()

    private val _activeStreamUrl = MutableStateFlow<String?>(null)
    val activeStreamUrl: StateFlow<String?> = _activeStreamUrl.asStateFlow()

    private val _selectedStreamIndex = MutableStateFlow(0)
    val selectedStreamIndex: StateFlow<Int> = _selectedStreamIndex.asStateFlow()

    private val _isStreamLoading = MutableStateFlow(false)
    val isStreamLoading: StateFlow<Boolean> = _isStreamLoading.asStateFlow()

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError.asStateFlow()

    // Server type ("mp4" → ExoPlayer, lainnya → WebView)
    private val _activeServerType = MutableStateFlow("embed")
    val activeServerType: StateFlow<String> = _activeServerType.asStateFlow()

    private val _serverTypes = MutableStateFlow<List<String>>(emptyList())
    val serverTypes: StateFlow<List<String>> = _serverTypes.asStateFlow()

    // Prev/Next episode (Samehadaku)
    private val _prevEpisodeSlug = MutableStateFlow<String?>(null)
    val prevEpisodeSlug: StateFlow<String?> = _prevEpisodeSlug.asStateFlow()

    private val _nextEpisodeSlug = MutableStateFlow<String?>(null)
    val nextEpisodeSlug: StateFlow<String?> = _nextEpisodeSlug.asStateFlow()

    // Auth flows
    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isUploadingAvatar = MutableStateFlow(false)
    val isUploadingAvatar: StateFlow<Boolean> = _isUploadingAvatar.asStateFlow()

    // Admin management state
    private val _adminUsers = MutableStateFlow<List<ProfileDto>>(emptyList())
    val adminUsers: StateFlow<List<ProfileDto>> = _adminUsers.asStateFlow()

    private val _adminAnnouncements = MutableStateFlow<List<AnnouncementDto>>(emptyList())
    val adminAnnouncements: StateFlow<List<AnnouncementDto>> = _adminAnnouncements.asStateFlow()

    private val _adminFeatured = MutableStateFlow<List<FeaturedAnimeDto>>(emptyList())
    val adminFeatured: StateFlow<List<FeaturedAnimeDto>> = _adminFeatured.asStateFlow()

    private val _adminBlacklist = MutableStateFlow<List<BlacklistedAnimeDto>>(emptyList())
    val adminBlacklist: StateFlow<List<BlacklistedAnimeDto>> = _adminBlacklist.asStateFlow()

    private val _isAdminLoading = MutableStateFlow(false)
    val isAdminLoading: StateFlow<Boolean> = _isAdminLoading.asStateFlow()

    private val _banStatusMessage = MutableStateFlow<String?>(null)
    val banStatusMessage: StateFlow<String?> = _banStatusMessage.asStateFlow()

    init {
        refreshBookmarks()
        loadBlacklistSlugs()
        loadHomeData()
        loadGenres()
        loadSearchPopular()
    }

    private fun getAuthHeader(): String {
        val currentToken = session.value.token
        return if (!currentToken.isNullOrEmpty()) "Bearer $currentToken" else "Bearer $SUPABASE_ANON_KEY"
    }

    private suspend fun <T> retryIO(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 3000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.w("AnikuVM", "Network call failed (attempt ${attempt + 1}/$times). Retrying...", e)
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return block()
    }

    // ── SOURCE SWITCHING ────────────────────────────────────────────

    fun switchSource(source: AnimeSource) {
        if (_activeSource.value == source) return
        _activeSource.value = source
        _homeOngoing.value = emptyList()
        _homeRecent.value = emptyList()
        _homePopular.value = emptyList()
        _homeMovies.value = emptyList()
        _exploreAnimes.value = emptyList()
        _genres.value = emptyList()
        _searchResults.value = emptyList()
        loadHomeData()
        loadGenres()
        loadSearchPopular()
    }

    fun checkSourcesStatus() {
        _sourcesStatus.value = AnimeSource.values().map { AnimeSourceInfo(it, SourceStatus.ONLINE) }
    }

    // ── HOME DATA ───────────────────────────────────────────────────

    fun refreshBookmarks() {
        _bookmarks.value = bookmarkManager.getBookmarks()
    }

    private fun loadBlacklistSlugs() {
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.getBlacklistedAnime(
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
                _blacklistedSlugs.value = response.map { it.anime_slug }.toSet()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed to load blacklisted anime slugs", e)
            }
        }
    }

    fun loadHomeData() {
        _isHomeLoading.value = true
        _homeError.value = null
        viewModelScope.launch {
            try {
                when (_activeSource.value) {
                    AnimeSource.SAMEHADAKU -> loadHomeDataShk()
                    else -> loadHomeDataAnimasu()
                }
            } catch (e: Exception) {
                _isHomeLoading.value = false
                _homeError.value = "Gagal memuat data. Silakan coba lagi."
                Log.e("AnikuVM", "Error loading home screen data", e)
            }
        }
    }

    private suspend fun loadHomeDataAnimasu() {
        try {
            val blacklistedResponse = retryIO {
                NetworkClient.supabaseDbApi.getBlacklistedAnime(
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
            }
            val blacklist = blacklistedResponse.map { it.anime_slug }.toSet()
            _blacklistedSlugs.value = blacklist

            try {
                val featured = retryIO {
                    NetworkClient.supabaseDbApi.getFeaturedAnime(
                        authHeader = "Bearer $SUPABASE_ANON_KEY",
                        apiKey = SUPABASE_ANON_KEY
                    )
                }.filterNot { blacklist.contains(it.anime_slug) }
                _featuredSlides.value = featured
            } catch (fe: Exception) {
                Log.e("AnikuVM", "Failed to fetch featured slides", fe)
            }

            try {
                val anns = retryIO {
                    NetworkClient.supabaseDbApi.getAnnouncements(
                        authHeader = "Bearer $SUPABASE_ANON_KEY",
                        apiKey = SUPABASE_ANON_KEY
                    )
                }
                _activeAnnouncement.value = anns.firstOrNull { it.is_active == true }
            } catch (ae: Exception) {
                Log.e("AnikuVM", "Failed to load announcements", ae)
            }

            val homeRes = retryIO { NetworkClient.animeApi.getHome() }
            _homeOngoing.value = (homeRes.ongoing ?: emptyList()).filterNot { blacklist.contains(it.slug) }
            _homeRecent.value = (homeRes.recent ?: emptyList()).filterNot { blacklist.contains(it.slug) }

            try {
                val popularRes = retryIO { NetworkClient.animeApi.getPopular(page = 1) }
                _homePopular.value = (popularRes.animes ?: emptyList()).filterNot { blacklist.contains(it.slug) }
            } catch (pe: Exception) {
                Log.e("AnikuVM", "Failed to load home popular", pe)
            }

            try {
                val moviesRes = retryIO { NetworkClient.animeApi.getMovies(page = 1) }
                _homeMovies.value = (moviesRes.animes ?: emptyList()).filterNot { blacklist.contains(it.slug) }
            } catch (me: Exception) {
                Log.e("AnikuVM", "Failed to load home movies", me)
            }

            _isHomeLoading.value = false
        } catch (e: Exception) {
            _isHomeLoading.value = false
            _homeError.value = "Gagal memuat data. Silakan coba lagi."
            Log.e("AnikuVM", "Error loading Animasu home data", e)
        }
    }

    private suspend fun loadHomeDataShk() {
        try {
            val homeRes = retryIO { NetworkClient.samehadakuApi.getHome() }
            val animeList = homeRes.data?.animeList ?: emptyList()
            _homeOngoing.value = animeList.map { it.toAnimeRaw() }
            _homeRecent.value = animeList.map { it.toAnimeRaw() }

            try {
                val popularRes = retryIO { NetworkClient.samehadakuApi.getPopular(page = 1) }
                _homePopular.value = (popularRes.data?.animeList ?: emptyList()).map { it.toAnimeRaw() }
            } catch (pe: Exception) {
                Log.e("AnikuVM", "Failed SHK popular", pe)
            }

            try {
                val moviesRes = retryIO { NetworkClient.samehadakuApi.getMovies(page = 1) }
                _homeMovies.value = (moviesRes.data?.animeList ?: emptyList()).map { it.toAnimeRaw() }
            } catch (me: Exception) {
                Log.e("AnikuVM", "Failed SHK movies", me)
            }

            _isHomeLoading.value = false
        } catch (e: Exception) {
            _isHomeLoading.value = false
            _homeError.value = "Gagal memuat data Samehadaku."
            Log.e("AnikuVM", "Error loading SHK home", e)
        }
    }

    // ── SEARCH ──────────────────────────────────────────────────────

    private fun loadSearchPopular() {
        viewModelScope.launch {
            try {
                when (_activeSource.value) {
                    AnimeSource.SAMEHADAKU -> {
                        val res = retryIO { NetworkClient.samehadakuApi.getPopular(page = 1) }
                        _searchPopular.value = (res.data?.animeList ?: emptyList()).map { it.toAnimeRaw() }
                    }
                    else -> {
                        val res = retryIO { NetworkClient.animeApi.getPopular(page = 1) }
                        _searchPopular.value = (res.animes ?: emptyList())
                            .filterNot { _blacklistedSlugs.value.contains(it.slug) }
                    }
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed loading popular list for search background", e)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        _isSearchLoading.value = true
        viewModelScope.launch {
            try {
                when (_activeSource.value) {
                    AnimeSource.SAMEHADAKU -> {
                        val res = retryIO { NetworkClient.samehadakuApi.search(query) }
                        _searchResults.value = (res.data?.animeList ?: emptyList()).map { it.toAnimeRaw() }
                    }
                    else -> {
                        val res = retryIO { NetworkClient.animeApi.search(query) }
                        _searchResults.value = (res.animes ?: emptyList())
                            .filterNot { _blacklistedSlugs.value.contains(it.slug) }
                    }
                }
                _isSearchLoading.value = false
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _isSearchLoading.value = false
                Log.e("AnikuVM", "Failed searching keywords: $query", e)
            }
        }
    }

    // ── EXPLORE ─────────────────────────────────────────────────────

    fun loadGenres() {
        viewModelScope.launch {
            try {
                when (_activeSource.value) {
                    AnimeSource.SAMEHADAKU -> {
                        val res = retryIO { NetworkClient.samehadakuApi.getGenres() }
                        _genres.value = (res.data?.genreList ?: emptyList()).map {
                            GenreRaw(name = it.name, slug = it.genreId)
                        }
                    }
                    else -> {
                        val list = retryIO { NetworkClient.animeApi.getGenres() }
                        _genres.value = list.genres ?: emptyList()
                    }
                }
            } catch (e: Exception) {
                _genres.value = emptyList()
                Log.e("AnikuVM", "Failed loading genres list", e)
            }
        }
    }

    fun setExploreTab(tab: String) {
        if (_exploreTab.value == tab && _selectedGenreSlug.value == null) return
        _exploreTab.value = tab
        _selectedGenreSlug.value = null
        resetAndLoadExplore()
    }

    fun selectGenre(genreSlug: String?) {
        _selectedGenreSlug.value = genreSlug
        resetAndLoadExplore()
    }

    private fun resetAndLoadExplore() {
        _explorePage.value = 1
        _exploreHasNext.value = true
        _exploreAnimes.value = emptyList()
        loadExplorePage()
    }

    fun loadNextExplorePage() {
        if (_isExploreLoading.value || !_exploreHasNext.value) return
        _explorePage.value = _explorePage.value + 1
        loadExplorePage()
    }

    fun loadExplorePage() {
        _isExploreLoading.value = true
        viewModelScope.launch {
            try {
                val blacklist = _blacklistedSlugs.value
                val page = _explorePage.value

                when (_activeSource.value) {
                    AnimeSource.SAMEHADAKU -> {
                        val res = retryIO {
                            when (_exploreTab.value) {
                                "Ongoing" -> NetworkClient.samehadakuApi.getOngoing(page)
                                "Completed" -> NetworkClient.samehadakuApi.getCompleted(page)
                                "Movie" -> NetworkClient.samehadakuApi.getMovies(page)
                                else -> NetworkClient.samehadakuApi.getOngoing(page)
                            }
                        }
                        val items = (res.data?.animeList ?: emptyList()).map { it.toAnimeRaw() }
                        _exploreAnimes.value = _exploreAnimes.value + items
                        _exploreHasNext.value = res.data?.pagination?.hasNextPage ?: false
                    }
                    else -> {
                        val response = retryIO {
                            if (_selectedGenreSlug.value != null) {
                                NetworkClient.animeApi.getAnimeByGenre(slug = _selectedGenreSlug.value!!, page = page)
                            } else {
                                when (_exploreTab.value) {
                                    "Ongoing" -> NetworkClient.animeApi.getOngoing(page = page)
                                    "Completed" -> NetworkClient.animeApi.getCompleted(page = page)
                                    "Movie" -> NetworkClient.animeApi.getMovies(page = page)
                                    "Latest" -> NetworkClient.animeApi.getLatest(page = page)
                                    else -> NetworkClient.animeApi.getOngoing(page = page)
                                }
                            }
                        }
                        val netAnimes = (response.animes ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                        _exploreAnimes.value = _exploreAnimes.value + netAnimes
                        _exploreHasNext.value = response.pagination?.hasNext ?: (netAnimes.isNotEmpty())
                    }
                }
                _isExploreLoading.value = false
            } catch (e: Exception) {
                _exploreAnimes.value = emptyList()
                _exploreHasNext.value = false
                _isExploreLoading.value = false
                Log.e("AnikuVM", "Failed load explore page", e)
            }
        }
    }

    // ── SCHEDULE ────────────────────────────────────────────────────

    fun selectDay(day: String) {
        _selectedDay.value = day
    }

    fun fetchScheduleData() {
        _isScheduleLoading.value = true
        viewModelScope.launch {
            try {
                when (_activeSource.value) {
                    AnimeSource.SAMEHADAKU -> {
                        val res = retryIO { NetworkClient.samehadakuApi.getSchedule() }
                        val days = res.data?.days ?: emptyList()
                        val map = mutableMapOf<String, List<AnimeRaw>>()
                        days.forEach { day ->
                            map[day.day] = (day.animeList ?: emptyList()).map { anime ->
                                AnimeRaw(
                                    title = anime.title,
                                    slug = anime.animeId,
                                    poster = anime.poster ?: "",
                                    type = anime.type,
                                    status_or_day = anime.time
                                )
                            }
                        }
                        _scheduleMap.value = map
                        if (_scheduleMap.value.isNotEmpty() && !_scheduleMap.value.containsKey(_selectedDay.value)) {
                            _selectedDay.value = _scheduleMap.value.keys.first()
                        }
                    }
                    else -> {
                        val res = retryIO { NetworkClient.animeApi.getSchedule() }
                        val sched = res.schedule
                        if (sched != null) {
                            val blacklist = _blacklistedSlugs.value
                            val map = mutableMapOf<String, List<AnimeRaw>>()
                            map["Minggu"] = (sched.minggu ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            map["Senin"] = (sched.senin ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            map["Selasa"] = (sched.selasa ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            map["Rabu"] = (sched.rabu ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            map["Kamis"] = (sched.kamis ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            map["Jumat"] = ((sched.jumat ?: sched.jumatAlt) ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            map["Sabtu"] = (sched.sabtu ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            _scheduleMap.value = map
                        }
                    }
                }
                _isScheduleLoading.value = false
            } catch (e: Exception) {
                _scheduleMap.value = emptyMap()
                _isScheduleLoading.value = false
                Log.e("AnikuVM", "Failed reading schedules from server", e)
            }
        }
    }

    // ── DETAIL ──────────────────────────────────────────────────────

    fun loadAnimeDetail(slug: String) {
        _isDetailLoading.value = true
        _animeDetail.value = null
        _detailError.value = null
        viewModelScope.launch {
            try {
                when (_activeSource.value) {
                    AnimeSource.SAMEHADAKU -> {
                        val res = retryIO { NetworkClient.samehadakuApi.getDetail(slug) }
                        val d = res.data
                        if (d != null) {
                            _animeDetail.value = DetailData(
                                title = d.title,
                                synonym = null,
                                poster = d.poster,
                                rating = d.score,
                                synopsis = d.synopsis,
                                trailer = null,
                                genres = d.genres?.map { DetailGenreRaw(it.name, it.genreId) },
                                status = d.status,
                                aired = d.info?.released,
                                type = d.type,
                                duration = null,
                                author = null,
                                studio = d.studio,
                                season = null,
                                episodes = d.episodeList?.map { DetailEpisodeRaw(it.title, it.episodeId) },
                                characters = null
                            )
                        } else {
                            _detailError.value = "Detail anime tidak ditemukan."
                        }
                    }
                    else -> {
                        if (_blacklistedSlugs.value.contains(slug)) {
                            _isDetailLoading.value = false
                            _detailError.value = "Anime ini disembunyikan oleh Admin."
                            return@launch
                        }
                        val res = retryIO { NetworkClient.animeApi.getDetail(slug) }
                        _animeDetail.value = res.detail
                    }
                }
                _isDetailLoading.value = false
            } catch (e: Exception) {
                _isDetailLoading.value = false
                _detailError.value = "Gagal memuat detail anime."
                Log.e("AnikuVM", "Failed detail load for $slug", e)
            }
        }
    }

    // ── STREAMING ───────────────────────────────────────────────────

    fun loadEpisodeStream(slug: String) {
        _isStreamLoading.value = true
        _streams.value = emptyList()
        _activeStreamUrl.value = null
        _streamEpisodeTitle.value = null
        _streamError.value = null
        viewModelScope.launch {
            try {
                when (_activeSource.value) {
                    AnimeSource.SAMEHADAKU -> {
                        val res = retryIO { NetworkClient.samehadakuApi.getEpisode(slug) }
                        val epData = res.data
                        _streamEpisodeTitle.value = epData?.title ?: "Tonton Tayangan"

                        val servers = epData?.servers ?: emptyList()
                        val streamList = servers.map { server ->
                            StreamRaw(name = server.name, url = server.embedUrl)
                        }
                        _streams.value = streamList
                        _serverTypes.value = servers.map { it.type }

                        // Prioritaskan mp4 sebagai default
                        val mp4Index = servers.indexOfFirst { it.type == "mp4" }
                        val defaultIndex = if (mp4Index >= 0) mp4Index else 0

                        if (streamList.isNotEmpty()) {
                            _selectedStreamIndex.value = defaultIndex
                            _activeStreamUrl.value = streamList[defaultIndex].url
                            _activeServerType.value = servers.getOrNull(defaultIndex)?.type ?: "embed"
                        } else {
                            _streamError.value = "Tidak ada tautan streaming yang tersedia."
                        }

                        _prevEpisodeSlug.value = epData?.prevEpisode
                        _nextEpisodeSlug.value = epData?.nextEpisode
                    }
                    else -> {
                        val res = retryIO { NetworkClient.animeApi.getEpisode(slug) }
                        _streamEpisodeTitle.value = res.title ?: "Tonton Tayangan"
                        val streamList = res.streams ?: emptyList()
                        _streams.value = streamList
                        _activeServerType.value = "embed"
                        _serverTypes.value = streamList.map { "embed" }
                        _prevEpisodeSlug.value = null
                        _nextEpisodeSlug.value = null

                        if (streamList.isNotEmpty()) {
                            _selectedStreamIndex.value = 0
                            _activeStreamUrl.value = streamList[0].url
                        } else {
                            _streamError.value = "Tidak ada tautan streaming yang tersedia."
                        }
                    }
                }
                _isStreamLoading.value = false
            } catch (e: Exception) {
                _isStreamLoading.value = false
                _streamError.value = "Gagal memuat streaming player."
                Log.e("AnikuVM", "Failed stream load for $slug", e)
            }
        }
    }

    fun selectStreamQuality(index: Int) {
        val streamList = _streams.value
        if (index in streamList.indices) {
            _selectedStreamIndex.value = index
            _activeStreamUrl.value = streamList[index].url
            _activeServerType.value = _serverTypes.value.getOrElse(index) { "embed" }
        }
    }

    // ── BOOKMARKS ───────────────────────────────────────────────────

    fun toggleBookmark(slug: String, title: String, poster: String, type: String? = null, ep: String? = null) {
        val currentlyBookmarked = bookmarkManager.isBookmarked(slug)
        if (currentlyBookmarked) {
            bookmarkManager.removeBookmark(slug)
        } else {
            bookmarkManager.addBookmark(BookmarkedAnime(slug, title, poster, type, ep))
        }
        refreshBookmarks()
    }

    // ── AUTH ────────────────────────────────────────────────────────

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        _authLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            try {
                val res = NetworkClient.supabaseAuthApi.signIn(
                    request = SignInRequest(email, password),
                    apiKey = SUPABASE_ANON_KEY
                )
                val token = res.access_token
                if (token == null) {
                    _authError.value = "Login gagal: Sesi tidak ditemukan."
                    _authLoading.value = false
                    return@launch
                }
                val uId = res.user?.id ?: ""
                val profileList = NetworkClient.supabaseDbApi.getProfileByUserId(
                    idQuery = "eq.$uId",
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                val profile = profileList.firstOrNull()
                if (profile?.is_banned == true) {
                    _authError.value = "Akun Anda ditangguhkan (Banned) oleh Admin."
                    _authLoading.value = false
                    return@launch
                }
                val activeSession = UserSession(
                    token = token,
                    userId = uId,
                    email = email,
                    username = profile?.username ?: (res.user?.user_metadata?.get("username")?.toString() ?: email.substringBefore("@")),
                    avatarUrl = profile?.avatar_url,
                    isAdmin = profile?.is_admin ?: false,
                    isBanned = profile?.is_banned ?: false
                )
                settingsStore.saveSession(activeSession)
                _authLoading.value = false
                onSuccess()
            } catch (e: Exception) {
                _authLoading.value = false
                _authError.value = "${e.javaClass.simpleName}: ${e.message}"
                Log.e("AnikuVM", "Login Exception", e)
            }
        }
    }

    fun register(email: String, password: String, username: String, onSuccess: () -> Unit) {
        _authLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            try {
                val res = NetworkClient.supabaseAuthApi.signUp(
                    request = SignUpRequest(email, password, SignUpData(username)),
                    apiKey = SUPABASE_ANON_KEY
                )
                val token = res.access_token
                if (token == null) {
                    _authError.value = "Daftar gagal: Token sesi tidak valid."
                    _authLoading.value = false
                    return@launch
                }
                val uId = res.user?.id ?: ""
                kotlinx.coroutines.delay(1500)
                val profiles = NetworkClient.supabaseDbApi.getProfileByUserId(
                    idQuery = "eq.$uId",
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                val profile = profiles.firstOrNull()
                val activeSession = UserSession(
                    token = token,
                    userId = uId,
                    email = email,
                    username = profile?.username ?: username,
                    avatarUrl = profile?.avatar_url,
                    isAdmin = profile?.is_admin ?: false,
                    isBanned = profile?.is_banned ?: false
                )
                settingsStore.saveSession(activeSession)
                _authLoading.value = false
                onSuccess()
            } catch (e: Exception) {
                _authLoading.value = false
                _authError.value = "Daftar gagal. Sandi minimal 6 karakter atau email sudah terdaftar."
                Log.e("AnikuVM", "Register Exception", e)
            }
        }
    }

    fun updateProfileUsername(newUsername: String, onComplete: () -> Unit) {
        val sess = session.value
        val token = sess.token ?: return
        val uId = sess.userId ?: return
        viewModelScope.launch {
            try {
                val updateFields = mapOf("username" to newUsername)
                NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.$uId",
                    profile = updateFields,
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                val updatedSession = sess.copy(username = newUsername, avatarUrl = sess.avatarUrl)
                settingsStore.saveSession(updatedSession)
                onComplete()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed to update profile username", e)
            }
        }
    }

    fun uploadAvatar(uri: Uri, onProgress: (Boolean) -> Unit) {
        val sess = session.value
        val token = sess.token ?: return
        val uId = sess.userId ?: return
        _isUploadingAvatar.value = true
        onProgress(true)
        viewModelScope.launch {
            try {
                val contentResolver = appContext.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                val byteBuffer = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var len: Int
                if (inputStream != null) {
                    while (inputStream.read(buffer).also { len = it } != -1) {
                        byteBuffer.write(buffer, 0, len)
                    }
                    inputStream.close()
                }
                val fileBytes = byteBuffer.toByteArray()
                val requestFile = fileBytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, fileBytes.size)
                val body = MultipartBody.Part.createFormData("file", "avatar.jpg", requestFile)
                val presetBody = "aniku_avatar".toRequestBody("text/plain".toMediaTypeOrNull())
                val cloudinaryRes = NetworkClient.cloudinaryApi.uploadAvatar(body, presetBody)
                val secureUrl = cloudinaryRes.secure_url
                NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.$uId",
                    profile = mapOf("avatar_url" to secureUrl),
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                val updatedSession = sess.copy(avatarUrl = secureUrl)
                settingsStore.saveSession(updatedSession)
                _isUploadingAvatar.value = false
                onProgress(false)
            } catch (e: Exception) {
                _isUploadingAvatar.value = false
                onProgress(false)
                Log.e("AnikuVM", "Cloudinary upload failed", e)
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsStore.clearSession()
            onComplete()
        }
    }

    fun sendAuthRecovery(email: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                NetworkClient.supabaseAuthApi.recoverPassword(RecoverRequest(email), SUPABASE_ANON_KEY)
                onComplete(true)
            } catch (e: Exception) {
                Log.e("AnikuVM", "Supabase recovery send failure", e)
                onComplete(false)
            }
        }
    }

    // ── ADMIN ───────────────────────────────────────────────────────

    fun loadAdminDetails() {
        if (!session.value.isAdmin) return
        val authHeader = getAuthHeader()
        _isAdminLoading.value = true
        viewModelScope.launch {
            try {
                _adminUsers.value = NetworkClient.supabaseDbApi.getProfiles(authHeader, SUPABASE_ANON_KEY)
                _adminAnnouncements.value = NetworkClient.supabaseDbApi.getAllAnnouncements(authHeader, SUPABASE_ANON_KEY)
                _adminFeatured.value = NetworkClient.supabaseDbApi.getFeaturedAnime("*", "order_index.asc", authHeader, SUPABASE_ANON_KEY)
                _adminBlacklist.value = NetworkClient.supabaseDbApi.getBlacklistedAnime(authHeader, SUPABASE_ANON_KEY)
                _isAdminLoading.value = false
            } catch (e: Exception) {
                _isAdminLoading.value = false
                Log.e("AnikuVM", "Failed to retrieve full admin properties", e)
            }
        }
    }

    fun toggleUserBanStatus(profile: ProfileDto) {
        val authHeader = getAuthHeader()
        val userIdToModify = profile.id
        val newBanStatus = !(profile.is_banned ?: false)
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.$userIdToModify",
                    profile = mapOf("is_banned" to newBanStatus),
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                if (response.isSuccessful || response.code() == 204) {
                    _banStatusMessage.value = if (newBanStatus) "User berhasil dibanned" else "User berhasil diaktifkan"
                    loadAdminDetails()
                } else {
                    val errBody = response.errorBody()?.string() ?: "Unknown error"
                    _banStatusMessage.value = "Gagal: ${response.code()} - $errBody"
                }
            } catch (e: Exception) {
                _banStatusMessage.value = "Error: ${e.message}"
                Log.e("AnikuVM", "Failed updating ban for $userIdToModify", e)
            }
        }
    }

    fun saveAnnouncement(annId: String?, title: String, message: String, active: Boolean, downloadUrl: String? = null) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val inputBody = mapOf<String, @JvmSuppressWildcards Any?>(
                    "title" to title, "message" to message,
                    "is_active" to active, "download_url" to downloadUrl
                )
                if (annId.isNullOrEmpty()) {
                    NetworkClient.supabaseDbApi.insertAnnouncement(inputBody, authHeader, SUPABASE_ANON_KEY)
                } else {
                    NetworkClient.supabaseDbApi.updateAnnouncement("eq.$annId", inputBody, authHeader, SUPABASE_ANON_KEY)
                }
                loadAdminDetails()
                loadHomeData()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed saving announcement", e)
            }
        }
    }

    fun deleteAnnouncement(annId: String) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deleteAnnouncement("eq.$annId", authHeader, SUPABASE_ANON_KEY)
                loadAdminDetails()
                loadHomeData()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed removing announcement", e)
            }
        }
    }

    fun saveFeaturedAnime(slug: String, title: String?, poster: String?, orderIndex: Int) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val inputBody = mapOf<String, Any?>(
                    "anime_slug" to slug, "anime_title" to title,
                    "anime_poster" to poster, "order_index" to orderIndex
                )
                NetworkClient.supabaseDbApi.insertFeaturedAnime(inputBody, authHeader, SUPABASE_ANON_KEY)
                loadAdminDetails()
                loadHomeData()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed insert featured anime", e)
            }
        }
    }

    fun deleteFeaturedAnime(featuredId: String) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deleteFeaturedAnime("eq.$featuredId", authHeader, SUPABASE_ANON_KEY)
                loadAdminDetails()
                loadHomeData()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed delete featured anime", e)
            }
        }
    }

    fun saveBlacklistAnime(slug: String, title: String?, reason: String?) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val inputBody = mapOf<String, Any?>(
                    "anime_slug" to slug, "anime_title" to title, "reason" to reason
                )
                NetworkClient.supabaseDbApi.insertBlacklistedAnime(inputBody, authHeader, SUPABASE_ANON_KEY)
                loadAdminDetails()
                loadBlacklistSlugs()
                loadHomeData()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed insert blacklisted", e)
            }
        }
    }

    fun deleteBlacklistAnime(id: String) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deleteBlacklistedAnime("eq.$id", authHeader, SUPABASE_ANON_KEY)
                loadAdminDetails()
                loadBlacklistSlugs()
                loadHomeData()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed deleting blacklist", e)
            }
        }
    }

    // ── SETTINGS ────────────────────────────────────────────────────

    fun toggleDarkMode(dark: Boolean) {
        viewModelScope.launch { settingsStore.setTheme(dark) }
    }

    fun changeTextSize(size: String) {
        viewModelScope.launch { settingsStore.setTextSize(size) }
    }

    fun changeAccentColor(colorName: String) {
        viewModelScope.launch { settingsStore.setAccentColor(colorName) }
    }
}

// Extension function: ShkAnimeItem → AnimeRaw
fun ShkAnimeItem.toAnimeRaw() = AnimeRaw(
    title = this.title,
    slug = this.animeId,
    poster = this.poster,
    type = this.type,
    status_or_day = this.score
)
