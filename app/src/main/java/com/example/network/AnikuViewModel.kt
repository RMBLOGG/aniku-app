package com.example.network

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.SettingsStore
import com.example.ui.theme.UserSession
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class AnikuViewModel(context: Context) : ViewModel() {
    companion object {
        private const val SAMEHADAKU_REFERER = "https://v2.samehadaku.how/"
        private const val ANIMASU_REFERER = "https://animasu.cc/"
    }

    private val appContext = context.applicationContext
    val settingsStore = SettingsStore(appContext)
    val bookmarkManager = BookmarkManager(appContext)
    val watchHistoryManager = WatchHistoryManager(appContext)

    // Anime API dengan OkHttp Cache (50MB, 1 jam online / 7 hari offline)
    private val animeApi: AnimeApi by lazy { NetworkClient.animeApi(appContext) }
    private val samehadakuApi: SamehadakuApi by lazy { NetworkClient.samehadakuApi(appContext) }

    // Watch history state
    private val _watchHistory = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val watchHistory: StateFlow<List<WatchHistoryItem>> = _watchHistory.asStateFlow()

    fun refreshWatchHistory() {
        _watchHistory.value = watchHistoryManager.getHistory()
    }

    fun addToWatchHistory(
        animeSlug: String,
        animeTitle: String,
        animePoster: String,
        episodeSlug: String,
        episodeTitle: String
    ) {
        val item = WatchHistoryItem(
            animeSlug = animeSlug,
            animeTitle = animeTitle,
            animePoster = animePoster,
            episodeSlug = episodeSlug,
            episodeTitle = episodeTitle
        )
        watchHistoryManager.addHistory(item)
        refreshWatchHistory()
    }

    fun clearWatchHistory() {
        watchHistoryManager.clearHistory()
        refreshWatchHistory()
    }

    private val SUPABASE_ANON_KEY = com.example.network.SUPABASE_ANON_KEY

    // Settings flows
    val isDark = settingsStore.isDarkFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val textSize = settingsStore.textSizeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "Sedang")
    val accentColorName = settingsStore.accentColorFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "Red")
    val gridLayout = settingsStore.gridLayoutFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "2")
    val dataSource = settingsStore.dataSourceFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "Dayynime-v1")

    // Session flow
    val session = settingsStore.sessionFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserSession(null, null, null, null, null, null, false, false)
    )

    // Update check state
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val _latestVersion = MutableStateFlow("")
    val latestVersion: StateFlow<String> = _latestVersion.asStateFlow()

    private val _downloadUrl = MutableStateFlow("")
    val downloadUrl: StateFlow<String> = _downloadUrl.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateCheckMessage = MutableStateFlow("")
    val updateCheckMessage: StateFlow<String> = _updateCheckMessage.asStateFlow()

    private val _releaseBody = MutableStateFlow("")
    val releaseBody: StateFlow<String> = _releaseBody.asStateFlow()

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
    private val _exploreTab = MutableStateFlow("Ongoing") // Ongoing | Completed | Movie | Latest
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
    private val _selectedDay = MutableStateFlow(run {
        val dayMap = mapOf(
            java.util.Calendar.SUNDAY to "Minggu",
            java.util.Calendar.MONDAY to "Senin",
            java.util.Calendar.TUESDAY to "Selasa",
            java.util.Calendar.WEDNESDAY to "Rabu",
            java.util.Calendar.THURSDAY to "Kamis",
            java.util.Calendar.FRIDAY to "Jumat",
            java.util.Calendar.SATURDAY to "Sabtu"
        )
        dayMap[java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)] ?: "Minggu"
    })
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    private val _scheduleMap = MutableStateFlow<Map<String, List<AnimeRaw>>>(emptyMap())
    val scheduleMap: StateFlow<Map<String, List<AnimeRaw>>> = _scheduleMap.asStateFlow()

    private val _isScheduleLoading = MutableStateFlow(false)
    val isScheduleLoading: StateFlow<Boolean> = _isScheduleLoading.asStateFlow()

    // Detail state
    private val _animeDetail = MutableStateFlow<DetailData?>(null)
    val animeDetail: StateFlow<DetailData?> = _animeDetail.asStateFlow()

    private val _currentAnimeSlug = MutableStateFlow("")
    val currentAnimeSlug: StateFlow<String> = _currentAnimeSlug.asStateFlow()

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

    // Hasil ekstraksi embed -> direct link buat ExoPlayer. Null artinya host belum
    // didukung extractor (atau gagal di-parse) -> UI fallback ke WebView lama.
    private val _resolvedStream = MutableStateFlow<ResolvedStream?>(null)
    val resolvedStream: StateFlow<ResolvedStream?> = _resolvedStream.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

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
        // Load initial state
        refreshBookmarks()
        refreshWatchHistory()
        loadBlacklistSlugs()
        loadHomeData()
        loadGenres()
        loadSearchPopular()
        checkForUpdate()
        loadDonations()
        // Auto-refresh token saat app dibuka
        viewModelScope.launch {
            refreshSession()
        }
    }

    fun checkForUpdate() {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateCheckMessage.value = ""
            try {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val request = okhttp3.Request.Builder()
                        .url("https://api.github.com/repos/RMBLOGG/aniku-app/releases/latest")
                        .header("Accept", "application/vnd.github+json")
                        .build()
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    client.newCall(request).execute()
                }
                if (result.isSuccessful) {
                    val body = result.body?.string() ?: ""
                    val json = org.json.JSONObject(body)
                    val tagName = json.optString("tag_name", "")
                    val assets = json.optJSONArray("assets")
                    val dlUrl = if (assets != null && assets.length() > 0) {
                        assets.getJSONObject(0).optString("browser_download_url", "")
                    } else ""
                    _latestVersion.value = tagName
                    _downloadUrl.value = dlUrl
                    _releaseBody.value = json.optString("body", "")
                    val latestClean = tagName.trimStart('v')
                    val appVersion = try { com.example.BuildConfig.VERSION_NAME.trimStart('v') } catch (e: Exception) { "1.3.4" }

                    fun parseVersion(v: String): List<Int> =
                        v.split(".").map { it.toIntOrNull() ?: 0 }

                    val latestParts = parseVersion(latestClean)
                    val appParts = parseVersion(appVersion)

                    // Bandingkan tiap segment, berhenti di perbedaan pertama
                    val maxLen = maxOf(latestParts.size, appParts.size)
                    var isNewer = false
                    for (i in 0 until maxLen) {
                        val l = latestParts.getOrElse(i) { 0 }
                        val a = appParts.getOrElse(i) { 0 }
                        if (l > a) { isNewer = true; break }
                        if (l < a) { isNewer = false; break }
                    }

                    if (latestClean.isNotEmpty() && isNewer) {
                        _updateAvailable.value = true
                        _updateCheckMessage.value = "Update tersedia: $tagName"
                    } else {
                        _updateAvailable.value = false
                        _updateCheckMessage.value = "Aplikasi sudah versi terbaru ✓"
                    }
                } else {
                    _updateCheckMessage.value = "Gagal cek update (${result.code})"
                    Log.w("AnikuVM", "checkForUpdate HTTP ${result.code}")
                }
            } catch (e: Exception) {
                _updateCheckMessage.value = "Gagal cek update: periksa koneksi"
                Log.w("AnikuVM", "checkForUpdate failed: ${e.message}")
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    // Download APK dengan DownloadManager
    fun downloadUpdate(url: String, version: String) {
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val uri = Uri.parse(url)
        val request = android.app.DownloadManager.Request(uri).apply {
            setTitle("Aniku $version")
            setDescription("Mengunduh update aplikasi...")
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "Aniku-${version}.apk")
            setMimeType("application/vnd.android.package-archive")
            addRequestHeader("Accept", "application/octet-stream")
        }
        dm.enqueue(request)
    }

    // Helper: Header selection for Supabase DB
    private fun getAuthHeader(): String {
        val currentToken = session.value.token
        return if (!currentToken.isNullOrEmpty()) "Bearer $currentToken" else "Bearer $SUPABASE_ANON_KEY"
    }

    fun refreshBookmarks() {
        _bookmarks.value = bookmarkManager.getBookmarks()
    }

    private fun loadBlacklistSlugs() {
        viewModelScope.launch {
            try {
                // Anyone can read blacklisted anime slugs using anon auth
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
                // 1. Fetch Blacklist again to maintain sync
                val blacklistedResponse = retryIO {
                    NetworkClient.supabaseDbApi.getBlacklistedAnime(
                        authHeader = "Bearer $SUPABASE_ANON_KEY",
                        apiKey = SUPABASE_ANON_KEY
                    )
                }
                val blacklist = blacklistedResponse.map { it.anime_slug }.toSet()
                _blacklistedSlugs.value = blacklist

                // 2. Load featured slides from Supabase
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

                // 3. Load active announcements from Supabase
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

                // 4. Load Anime Home API
                val isSamehadaku = dataSource.value == "Dayynime-v2"
                if (isSamehadaku) {
                    // Sedang Tayang → /ongoing
                    try {
                        val ongoingRes = retryIO { samehadakuApi.getOngoing(page = 1) }
                        _homeOngoing.value = (ongoingRes.data?.animeList ?: emptyList())
                            .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                    } catch (oe: Exception) { Log.e("AnikuVM", "Failed samehadaku ongoing", oe) }

                    // Terbaru → /recent
                    try {
                        val recentRes = retryIO { samehadakuApi.getRecent(page = 1) }
                        _homeRecent.value = (recentRes.data?.animeList ?: emptyList())
                            .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                    } catch (re: Exception) { Log.e("AnikuVM", "Failed samehadaku recent", re) }

                    try {
                        val popularRes = retryIO { samehadakuApi.getPopular(page = 1) }
                        _homePopular.value = (popularRes.data?.animeList ?: emptyList())
                            .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                    } catch (pe: Exception) { Log.e("AnikuVM", "Failed samehadaku home popular", pe) }

                    try {
                        val moviesRes = retryIO { samehadakuApi.getMovies(page = 1) }
                        _homeMovies.value = (moviesRes.data?.animeList ?: emptyList())
                            .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                    } catch (me: Exception) { Log.e("AnikuVM", "Failed samehadaku home movies", me) }
                } else {
                    val homeRes = retryIO { animeApi.getHome() }
                    _homeOngoing.value = (homeRes.ongoing ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                    _homeRecent.value = (homeRes.recent ?: emptyList()).filterNot { blacklist.contains(it.slug) }

                    // 5. Load Popular for Section
                    try {
                        val popularRes = retryIO { animeApi.getPopular(page = 1) }
                        _homePopular.value = (popularRes.animes ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                    } catch (pe: Exception) {
                        Log.e("AnikuVM", "Failed to load home popular", pe)
                    }

                    // 6. Load Movies for Section
                    try {
                        val moviesRes = retryIO { animeApi.getMovies(page = 1) }
                        _homeMovies.value = (moviesRes.animes ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                    } catch (me: Exception) {
                        Log.e("AnikuVM", "Failed to load home movies", me)
                    }
                }

                _isHomeLoading.value = false
            } catch (e: Exception) {
                _isHomeLoading.value = false
                _homeError.value = "Gagal memuat data. Silakan coba lagi."
                Log.e("AnikuVM", "Error loading home screen data", e)
            }
        }
    }

    private fun loadSearchPopular() {
        viewModelScope.launch {
            try {
                if (dataSource.value == "Dayynime-v2") {
                    val res = retryIO { samehadakuApi.getPopular(page = 1) }
                    _searchPopular.value = (res.data?.animeList ?: emptyList())
                        .map { it.toAnimeRaw() }.filterNot { _blacklistedSlugs.value.contains(it.slug) }
                } else {
                    val res = retryIO { animeApi.getPopular(page = 1) }
                    _searchPopular.value = (res.animes ?: emptyList()).filterNot { _blacklistedSlugs.value.contains(it.slug) }
                }
            } catch (e: java.lang.Exception) {
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
        if (query.length >= 3) {
            com.example.AnikuAnalytics.trackSearch(query)
        }
        viewModelScope.launch {
            try {
                if (dataSource.value == "Dayynime-v2") {
                    val res = retryIO { samehadakuApi.search(query) }
                    _searchResults.value = (res.data?.animeList ?: emptyList())
                        .map { it.toAnimeRaw() }.filterNot { _blacklistedSlugs.value.contains(it.slug) }
                } else {
                    val res = retryIO { animeApi.search(query) }
                    _searchResults.value = (res.animes ?: emptyList()).filterNot { _blacklistedSlugs.value.contains(it.slug) }
                }
                _isSearchLoading.value = false
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _isSearchLoading.value = false
                Log.e("AnikuVM", "Failed searching keywords: $query", e)
            }
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            try {
                if (dataSource.value == "Dayynime-v2") {
                    val res = retryIO { samehadakuApi.getGenres() }
                    _genres.value = (res.data?.genreList ?: emptyList()).map { it.toGenreRaw() }
                } else {
                    val list = retryIO { animeApi.getGenres() }
                    _genres.value = list.genres ?: emptyList()
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

                // If a genre is selected, retrieve genre anime list
                val response = retryIO {
                    if (dataSource.value == "Dayynime-v2") {
                        val sRes = if (_selectedGenreSlug.value != null) {
                            samehadakuApi.getAnimeByGenre(genreId = _selectedGenreSlug.value!!, page = page)
                        } else {
                            when (_exploreTab.value) {
                                "Ongoing" -> samehadakuApi.getOngoing(page = page)
                                "Completed" -> samehadakuApi.getCompleted(page = page)
                                "Movie" -> samehadakuApi.getMovies(page = page)
                                "Latest" -> samehadakuApi.getRecent(page = page)
                                else -> samehadakuApi.getOngoing(page = page)
                            }
                        }
                        val items = (sRes.data?.animeList ?: emptyList()).map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                        val hasNext = sRes.pagination?.hasNextPage ?: items.isNotEmpty()
                        Pair(items, hasNext)
                    } else {
                        val aRes = if (_selectedGenreSlug.value != null) {
                            animeApi.getAnimeByGenre(slug = _selectedGenreSlug.value!!, page = page)
                        } else {
                            when (_exploreTab.value) {
                                "Ongoing" -> animeApi.getOngoing(page = page)
                                "Completed" -> animeApi.getCompleted(page = page)
                                "Movie" -> animeApi.getMovies(page = page)
                                "Latest" -> animeApi.getLatest(page = page)
                                else -> animeApi.getOngoing(page = page)
                            }
                        }
                        val items = (aRes.animes ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                        val hasNext = aRes.pagination?.hasNext ?: items.isNotEmpty()
                        Pair(items, hasNext)
                    }
                }

                _exploreAnimes.value = _exploreAnimes.value + response.first
                _exploreHasNext.value = response.second
                _isExploreLoading.value = false
            } catch (e: Exception) {
                _exploreAnimes.value = emptyList()
                _exploreHasNext.value = false
                _isExploreLoading.value = false
                Log.e("AnikuVM", "Failed load explore page", e)
            }
        }
    }

    fun selectDay(day: String) {
        _selectedDay.value = day
    }

    fun loadSchedule() {
        _isScheduleLoading.value = true
        viewModelScope.launch {
            try {
                val res = animeApi.getSchedule()
                // Fetch schedule data properly
                _isScheduleLoading.value = false
            } catch (e: Exception) {
                _isScheduleLoading.value = false
                Log.e("AnikuVM", "Failed schedule retrieve", e)
            }
        }
    }
    
    fun clearScheduleCache() {
        _scheduleMap.value = emptyMap()
    }

    fun fetchScheduleData() {
        _isScheduleLoading.value = true
        viewModelScope.launch {
            try {
                val blacklist = _blacklistedSlugs.value
                if (dataSource.value == "Dayynime-v2") {
                    val res = retryIO { samehadakuApi.getSchedule() }
                    val days = res.data?.days ?: emptyList()
                    val dayNameMap = mapOf(
                        "Sunday" to "Minggu", "Monday" to "Senin", "Tuesday" to "Selasa",
                        "Wednesday" to "Rabu", "Thursday" to "Kamis", "Friday" to "Jumat", "Saturday" to "Sabtu"
                    )
                    val map = mutableMapOf<String, List<AnimeRaw>>()
                    for (day in days) {
                        val key = dayNameMap[day.day] ?: day.day
                        map[key] = (day.animeList ?: emptyList())
                            .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                    }
                    _scheduleMap.value = map
                } else {
                    val res = retryIO { animeApi.getSchedule() }
                    val sched = res.schedule
                    if (sched != null) {
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
                _isScheduleLoading.value = false
            } catch (e: Exception) {
                _scheduleMap.value = emptyMap()
                _isScheduleLoading.value = false
                Log.e("AnikuVM", "Failed reading schedules from server", e)
            }
        }
    }

    fun loadAnimeDetail(slug: String) {
        _isDetailLoading.value = true
        _animeDetail.value = null
        _detailError.value = null
        _currentAnimeSlug.value = slug
        viewModelScope.launch {
            try {
                // Sync blacklist check
                if (_blacklistedSlugs.value.contains(slug)) {
                    _isDetailLoading.value = false
                    _detailError.value = "Anime ini disembunyikan oleh Admin."
                    return@launch
                }
                if (dataSource.value == "Dayynime-v2") {
                    val res = retryIO { samehadakuApi.getDetail(slug) }
                    _animeDetail.value = res.data?.toDetailData()
                } else {
                    val res = retryIO { animeApi.getDetail(slug) }
                    _animeDetail.value = res.detail
                }
                _isDetailLoading.value = false
            } catch (e: Exception) {
                _isDetailLoading.value = false
                _detailError.value = "Gagal memuat detail anime."
                Log.e("AnikuVM", "Failed detail load for $slug", e)
            }
        }
    }

    fun clearStreamState() {
        _streams.value = emptyList()
        _activeStreamUrl.value = null
        _streamEpisodeTitle.value = null
        _streamError.value = null
        _isStreamLoading.value = false
        _resolvedStream.value = null
        _isResolving.value = false
    }

    // Set embed URL aktif lalu coba ekstrak jadi direct link di background.
    private fun setActiveStream(url: String, referer: String? = null) {
        _activeStreamUrl.value = url
        _resolvedStream.value = null
        viewModelScope.launch {
            _isResolving.value = true
            _resolvedStream.value = try {
                VideoExtractor.resolve(url, referer)
            } catch (e: Exception) {
                Log.e("AnikuVM", "Extractor error: ${e.message}", e)
                null
            }
            _isResolving.value = false
        }
    }

    fun loadEpisodeStream(slug: String) {
        _isStreamLoading.value = true
        _streams.value = emptyList()
        _activeStreamUrl.value = null
        _streamEpisodeTitle.value = null
        _streamError.value = null
        viewModelScope.launch {
            try {
                if (dataSource.value == "Dayynime-v2") {
                    val res = retryIO { samehadakuApi.getEpisode(slug) }
                    val epData = res.data
                    _streamEpisodeTitle.value = epData?.title ?: "Tonton Tayangan"

                    // Build stream list from Samehadaku server qualities
                    val streamList = mutableListOf<StreamRaw>()
                    val qualities = epData?.server?.qualities ?: emptyList()
                    for (quality in qualities) {
                        val qTitle = quality.title
                        for (server in quality.serverList ?: emptyList()) {
                            streamList.add(StreamRaw(
                                name = "${server.title} ($qTitle)",
                                url = "samehadaku_server:${server.serverId}"
                            ))
                        }
                    }
                    // Fallback: defaultStreamingUrl
                    if (streamList.isEmpty() && !epData?.defaultStreamingUrl.isNullOrEmpty()) {
                        streamList.add(StreamRaw(name = "Default", url = epData!!.defaultStreamingUrl!!))
                    }

                    _streams.value = streamList
                    if (streamList.isNotEmpty()) {
                        _selectedStreamIndex.value = 0
                        // Resolve first server URL
                        val firstUrl = streamList[0].url
                        if (firstUrl.startsWith("samehadaku_server:")) {
                            val serverId = firstUrl.removePrefix("samehadaku_server:")
                            try {
                                val linkRes = retryIO { samehadakuApi.getServerLink(serverId) }
                                setActiveStream(linkRes.data?.url ?: firstUrl, SAMEHADAKU_REFERER)
                            } catch (e: Exception) {
                                setActiveStream(firstUrl, SAMEHADAKU_REFERER)
                            }
                        } else {
                            setActiveStream(firstUrl, SAMEHADAKU_REFERER)
                        }
                    } else {
                        _streamError.value = "Tidak ada tautan streaming yang tersedia."
                    }
                } else {
                    val res = retryIO { animeApi.getEpisode(slug) }
                    _streamEpisodeTitle.value = res.title ?: "Tonton Tayangan"
                    val streamList = res.streams ?: emptyList()
                    _streams.value = streamList
                    if (streamList.isNotEmpty()) {
                        _selectedStreamIndex.value = 0
                        setActiveStream(streamList[0].url, ANIMASU_REFERER)
                    } else {
                        _streamError.value = "Tidak ada tautan streaming yang tersedia."
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
            val rawUrl = streamList[index].url
            val referer = if (dataSource.value == "Dayynime-v2") SAMEHADAKU_REFERER else ANIMASU_REFERER
            if (rawUrl.startsWith("samehadaku_server:")) {
                val serverId = rawUrl.removePrefix("samehadaku_server:")
                _isStreamLoading.value = true
                _activeStreamUrl.value = null
                _resolvedStream.value = null
                viewModelScope.launch {
                    try {
                        val linkRes = retryIO { samehadakuApi.getServerLink(serverId) }
                        val resolvedUrl = linkRes.data?.url
                        if (!resolvedUrl.isNullOrEmpty()) {
                            setActiveStream(resolvedUrl, referer)
                        } else {
                            Log.w("AnikuVM", "Server $serverId returned empty url, using raw")
                            setActiveStream(rawUrl, referer)
                        }
                    } catch (e: Exception) {
                        Log.e("AnikuVM", "Failed resolve server $serverId: ${e.message}", e)
                        setActiveStream(rawUrl, referer)
                    } finally {
                        _isStreamLoading.value = false
                    }
                }
            } else {
                setActiveStream(rawUrl, referer)
            }
        }
    }

    // Toggle Bookmarks locally
    fun toggleBookmark(slug: String, title: String, poster: String, type: String? = null, ep: String? = null) {
        val currentlyBookmarked = bookmarkManager.isBookmarked(slug)
        if (currentlyBookmarked) {
            bookmarkManager.removeBookmark(slug)
        } else {
            bookmarkManager.addBookmark(BookmarkedAnime(slug, title, poster, type, ep))
        }
        refreshBookmarks()
    }

    // Auth Actions: Login / Register / Get Profile
    suspend fun refreshSession() {
        val currentSession = session.value
        val refreshToken = currentSession.refreshToken
        if (refreshToken.isNullOrEmpty()) return
        try {
            val res = NetworkClient.supabaseAuthApi.refreshToken(
                request = RefreshTokenRequest(refresh_token = refreshToken),
                apiKey = SUPABASE_ANON_KEY
            )
            val newToken = res.access_token ?: return
            val updatedSession = currentSession.copy(
                token = newToken,
                refreshToken = res.refresh_token ?: refreshToken
            )
            settingsStore.saveSession(updatedSession)
        } catch (e: Exception) {
            Log.e("AnikuVM", "Token refresh failed: ${e.message}")
            // Token sudah tidak bisa di-refresh, clear session → user perlu login ulang
            settingsStore.clearSession()
        }
    }

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
                
                // Fetch profile to get real admin/banned status
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
                    refreshToken = res.refresh_token,
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

                // Sleep briefly to let handle_new_user trigger execute
                kotlinx.coroutines.delay(1500)

                // Read own profile
                val profiles = NetworkClient.supabaseDbApi.getProfileByUserId(
                    idQuery = "eq.$uId",
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )

                val profile = profiles.firstOrNull()

                val activeSession = UserSession(
                    token = token,
                    refreshToken = res.refresh_token,
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
                _authError.value = "Daftar gagal. Sandi minimal 6 karakter atau email sudah terdafar."
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
                val updatedSession = sess.copy(
                    username = newUsername,
                    avatarUrl = sess.avatarUrl
                )
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
                // 1. Read bytes from URI
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
                
                // 2. Prepare multipart request body
                val requestFile = fileBytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, fileBytes.size)
                val body = MultipartBody.Part.createFormData("file", "avatar.jpg", requestFile)
                val presetBody = "aniku_avatar".toRequestBody("text/plain".toMediaTypeOrNull())

                // 3. Upload to Cloudinary unsigned preset
                val cloudinaryRes = NetworkClient.cloudinaryApi.uploadAvatar(body, presetBody)
                val secureUrl = cloudinaryRes.secure_url

                // 4. Update url in Supabase profiles
                NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.$uId",
                    profile = mapOf("avatar_url" to secureUrl),
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                
                val updatedSession = sess.copy(
                    avatarUrl = secureUrl
                )
                settingsStore.saveSession(updatedSession)
                _isUploadingAvatar.value = false
                onProgress(false)
            } catch (e: java.lang.Exception) {
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

    // Admin Panel Database Operations
    fun loadAdminDetails() {
        if (!session.value.isAdmin) return
        val authHeader = getAuthHeader()
        _isAdminLoading.value = true
        viewModelScope.launch {
            try {
                // 1. Load users list
                _adminUsers.value = NetworkClient.supabaseDbApi.getProfiles(authHeader, SUPABASE_ANON_KEY)
                
                // 2. Load announcements list (all)
                _adminAnnouncements.value = NetworkClient.supabaseDbApi.getAllAnnouncements(authHeader, SUPABASE_ANON_KEY)
                
                // 3. Load featured slides list
                _adminFeatured.value = NetworkClient.supabaseDbApi.getFeaturedAnime("*", "order_index.asc", authHeader, SUPABASE_ANON_KEY)
                
                // 4. Load blacklisted items list
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
        Log.d("AnikuVM", "Trying ban - token: ${session.value.token?.take(20)} userId: $userIdToModify")
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
                    Log.e("AnikuVM", "Ban failed: ${response.code()} $errBody")
                }
            } catch (e: java.lang.Exception) {
                _banStatusMessage.value = "Error: ${e.message}"
                Log.e("AnikuVM", "Failed updating ban for $userIdToModify", e)
            }
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

    // Announcements management
    fun saveAnnouncement(annId: String?, title: String, message: String, active: Boolean, downloadUrl: String? = null) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val inputBody = mapOf<String, @JvmSuppressWildcards Any?>(
                    "title" to title,
                    "message" to message,
                    "is_active" to active,
                    "download_url" to downloadUrl
                )
                if (annId.isNullOrEmpty()) {
                    NetworkClient.supabaseDbApi.insertAnnouncement(inputBody, authHeader, SUPABASE_ANON_KEY)
                } else {
                    NetworkClient.supabaseDbApi.updateAnnouncement("eq.$annId", inputBody, authHeader, SUPABASE_ANON_KEY)
                }
                loadAdminDetails()
                loadHomeData() // Reload home notifications
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

    // Featured management
    fun saveFeaturedAnime(slug: String, title: String?, poster: String?, orderIndex: Int) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val inputBody = mapOf<String, Any?>(
                    "anime_slug" to slug,
                    "anime_title" to title,
                    "anime_poster" to poster,
                    "order_index" to orderIndex
                )
                NetworkClient.supabaseDbApi.insertFeaturedAnime(inputBody, authHeader, SUPABASE_ANON_KEY)
                loadAdminDetails()
                loadHomeData() // Reload home slider
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

    // Blacklist management
    fun saveBlacklistAnime(slug: String, title: String?, reason: String?) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val inputBody = mapOf<String, Any?>(
                    "anime_slug" to slug,
                    "anime_title" to title,
                    "reason" to reason
                )
                NetworkClient.supabaseDbApi.insertBlacklistedAnime(inputBody, authHeader, SUPABASE_ANON_KEY)
                loadAdminDetails()
                loadBlacklistSlugs() // Reload local sets
                loadHomeData() // Reload home sections to filter
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

    // Edit Settings Details on DataStore
    fun toggleDarkMode(dark: Boolean) {
        viewModelScope.launch { settingsStore.setTheme(dark) }
    }

    fun changeTextSize(size: String) {
        viewModelScope.launch { settingsStore.setTextSize(size) }
    }

    fun changeAccentColor(colorName: String) {
        viewModelScope.launch { settingsStore.setAccentColor(colorName) }
    }

    fun changeGridLayout(layout: String) {
        viewModelScope.launch { settingsStore.setGridLayout(layout) }
    }

    fun changeDataSource(source: String) {
        viewModelScope.launch { settingsStore.setDataSource(source) }
    }

    // --- CHAT ROOM ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Unread chat badge
    val hasUnreadChat: StateFlow<Boolean> = combine(
        _chatMessages,
        settingsStore.lastChatReadFlow
    ) { messages, lastRead ->
        if (messages.isEmpty()) return@combine false
        val latest = messages.maxByOrNull { it.created_at }?.created_at ?: return@combine false
        lastRead.isEmpty() || latest > lastRead
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markChatRead() {
        val latest = _chatMessages.value.maxByOrNull { it.created_at }?.created_at ?: return
        viewModelScope.launch { settingsStore.saveLastChatRead(latest) }
    }

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    fun clearChatError() { _chatError.value = null }

    // ── Watch Live Chat ──────────────────────────────────────────
    private val _watchChatMessages = MutableStateFlow<List<WatchChatMessage>>(emptyList())
    val watchChatMessages: StateFlow<List<WatchChatMessage>> = _watchChatMessages.asStateFlow()

    private val _isWatchChatLoading = MutableStateFlow(false)
    val isWatchChatLoading: StateFlow<Boolean> = _isWatchChatLoading.asStateFlow()

    private var watchChatPollingJob: kotlinx.coroutines.Job? = null

    fun startWatchChatPolling(episodeSlug: String) {
        watchChatPollingJob?.cancel()
        _watchChatMessages.value = emptyList()
        watchChatPollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val messages = NetworkClient.supabaseDbApi.getWatchChatMessages(
                        episodeSlug = "eq.$episodeSlug",
                        authHeader = "Bearer $SUPABASE_ANON_KEY",
                        apiKey = SUPABASE_ANON_KEY
                    )
                    _watchChatMessages.value = messages
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(5000L)
            }
        }
    }

    fun stopWatchChatPolling() {
        watchChatPollingJob?.cancel()
        watchChatPollingJob = null
    }

    // ── Active Viewers ───────────────────────────────────────────
    private val _viewerCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val viewerCounts: StateFlow<Map<String, Int>> = _viewerCounts.asStateFlow()

    private var viewerPollingJob: kotlinx.coroutines.Job? = null
    private var currentViewingSlug: String? = null

    fun joinAsViewer(animeSlug: String) {
        val userId = session.value.userId ?: return
        val token = session.value.token ?: return
        currentViewingSlug = animeSlug
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.upsertViewer(
                    data = mapOf(
                        "anime_slug" to animeSlug,
                        "user_id" to userId,
                        "last_seen" to java.time.Instant.now().toString()
                    ),
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (_: Exception) {}
        }
        // Keep alive setiap 30 detik
        viewerPollingJob?.cancel()
        viewerPollingJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                try {
                    NetworkClient.supabaseDbApi.upsertViewer(
                        data = mapOf(
                            "anime_slug" to animeSlug,
                            "user_id" to userId,
                            "last_seen" to java.time.Instant.now().toString()
                        ),
                        authHeader = "Bearer $token",
                        apiKey = SUPABASE_ANON_KEY
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun leaveAsViewer() {
        val userId = session.value.userId ?: return
        val token = session.value.token ?: return
        val slug = currentViewingSlug ?: return
        viewerPollingJob?.cancel()
        viewerPollingJob = null
        currentViewingSlug = null
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.removeViewer(
                    animeSlug = "eq.$slug",
                    userId = "eq.$userId",
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (_: Exception) {}
        }
    }

    fun startViewerCountPolling(animeSlugs: List<String>) {
        if (animeSlugs.isEmpty()) return
        viewModelScope.launch {
            while (true) {
                try {
                    // Ambil semua viewers aktif (last_seen < 2 menit)
                    val cutoff = java.time.Instant.now().minusSeconds(120).toString()
                    val rows = NetworkClient.supabaseDbApi.getAllViewerCounts(
                        authHeader = "Bearer $SUPABASE_ANON_KEY",
                        apiKey = SUPABASE_ANON_KEY
                    )
                    // Count per anime_slug
                    val counts = rows.groupBy { it["anime_slug"] ?: "" }
                        .mapValues { it.value.size }
                        .filterKeys { it.isNotEmpty() }
                    _viewerCounts.value = counts
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(30_000L)
            }
        }
    }

    fun sendWatchChatMessage(episodeSlug: String, message: String) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) return
        val trimmed = message.trim()
        if (trimmed.isEmpty() || trimmed.length > 200) return
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.insertWatchChatMessage(
                    data = WatchChatRequest(
                        episode_slug = episodeSlug,
                        user_id = currentSession.userId ?: "",
                        username = currentSession.username ?: currentSession.email?.substringBefore("@") ?: "User",
                        avatar_url = currentSession.avatarUrl,
                        message = trimmed
                    ),
                    authHeader = "Bearer ${currentSession.token}",
                    apiKey = SUPABASE_ANON_KEY
                )
                // Immediately reload
                val messages = NetworkClient.supabaseDbApi.getWatchChatMessages(
                    episodeSlug = "eq.$episodeSlug",
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
                _watchChatMessages.value = messages
            } catch (_: Exception) {}
        }
    }

    fun loadChatMessages() {
        viewModelScope.launch {
            _isChatLoading.value = true
            try {
                val messages = NetworkClient.supabaseDbApi.getChatMessages(
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
                _chatMessages.value = messages
            } catch (e: Exception) {
                _chatError.value = "Gagal memuat pesan: ${e.message}"
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    private val _isSendingImage = MutableStateFlow(false)
    val isSendingImage: StateFlow<Boolean> = _isSendingImage.asStateFlow()

    fun sendChatMessage(
        message: String,
        replyToId: String? = null,
        replyToUsername: String? = null,
        replyToMessage: String? = null,
        imageUrl: String? = null
    ) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) {
            _chatError.value = "Kamu harus login untuk mengirim pesan"
            return
        }
        if (currentSession.isBanned) {
            _chatError.value = "Akunmu dibanned dari chat"
            return
        }
        val trimmed = message.trim()
        if (trimmed.isEmpty() && imageUrl == null) return
        if (trimmed.length > 300) return

        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.insertChatMessage(
                    data = ChatMessageRequest(
                        user_id = currentSession.userId ?: "",
                        username = currentSession.username ?: currentSession.email?.substringBefore("@") ?: "Anonymous",
                        avatar_url = currentSession.avatarUrl,
                        is_admin = currentSession.isAdmin,
                        message = trimmed,
                        reply_to_id = replyToId,
                        reply_to_username = replyToUsername,
                        reply_to_message = replyToMessage,
                        image_url = imageUrl
                    ),
                    authHeader = "Bearer ${currentSession.token}",
                    apiKey = SUPABASE_ANON_KEY
                )
                loadChatMessages()
            } catch (e: Exception) {
                _chatError.value = "Gagal kirim pesan: ${e.message}"
            }
        }
    }

    fun uploadChatImage(context: Context, uri: Uri, onDone: (String?) -> Unit) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) {
            _chatError.value = "Kamu harus login untuk mengirim foto"
            onDone(null)
            return
        }
        viewModelScope.launch {
            _isSendingImage.value = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val byteBuffer = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var len: Int
                if (inputStream != null) {
                    while (inputStream.read(buffer).also { len = it } != -1) {
                        byteBuffer.write(buffer, 0, len)
                    }
                    inputStream.close()
                }
                val fileBytes = byteBuffer.toByteArray()
                val requestFile = fileBytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, fileBytes.size)
                val body = MultipartBody.Part.createFormData("file", "chat_image.jpg", requestFile)
                val presetBody = "aniku_avatar".toRequestBody("text/plain".toMediaTypeOrNull())
                val res = NetworkClient.cloudinaryApi.uploadAvatar(body, presetBody)
                onDone(res.secure_url)
            } catch (e: Exception) {
                _chatError.value = "Gagal upload foto: ${e.message}"
                onDone(null)
            } finally {
                _isSendingImage.value = false
            }
        }
    }

    fun deleteChatMessage(messageId: String) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) return

        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deleteChatMessage(
                    idQuery = "eq.$messageId",
                    authHeader = "Bearer ${currentSession.token}",
                    apiKey = SUPABASE_ANON_KEY
                )
                _chatMessages.value = _chatMessages.value.filter { it.id != messageId }
            } catch (e: Exception) {
                _chatError.value = "Gagal hapus pesan: ${e.message}"
            }
        }
    }

    // ─────────────── FEED ───────────────
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _isFeedLoading = MutableStateFlow(false)
    val isFeedLoading: StateFlow<Boolean> = _isFeedLoading.asStateFlow()

    private val _feedError = MutableStateFlow<String?>(null)
    val feedError: StateFlow<String?> = _feedError.asStateFlow()

    private val _postLikes = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val postLikes: StateFlow<Map<String, List<String>>> = _postLikes.asStateFlow()

    private val _postComments = MutableStateFlow<Map<String, List<PostComment>>>(emptyMap())
    val postComments: StateFlow<Map<String, List<PostComment>>> = _postComments.asStateFlow()

    private val _isCreatingPost = MutableStateFlow(false)
    val isCreatingPost: StateFlow<Boolean> = _isCreatingPost.asStateFlow()

    // Anime yang sedang dibagikan ke feed (diisi dari AnimeDetailScreen, dipakai di CreatePostScreen)
    private val _pendingSharedAnime = MutableStateFlow<SharedAnimeRef?>(null)
    val pendingSharedAnime: StateFlow<SharedAnimeRef?> = _pendingSharedAnime.asStateFlow()

    fun setPendingSharedAnime(ref: SharedAnimeRef) {
        _pendingSharedAnime.value = ref
    }

    fun clearPendingSharedAnime() {
        _pendingSharedAnime.value = null
    }

    fun clearFeedError() { _feedError.value = null }

    // ─────────────── DONATIONS (TRAKTEER) ───────────────
    private val _donations = MutableStateFlow<List<Donation>>(emptyList())
    val donations: StateFlow<List<Donation>> = _donations.asStateFlow()

    private val _latestDonation = MutableStateFlow<Donation?>(null)
    val latestDonation: StateFlow<Donation?> = _latestDonation.asStateFlow()

    private var _lastSeenDonationId = MutableStateFlow<String?>(null)

    // Notifikasi banner — true kalau ada donasi baru yang belum dilihat
    val hasNewDonation: StateFlow<Boolean> = combine(_donations, _lastSeenDonationId) { list, lastSeen ->
        list.isNotEmpty() && list.first().id != lastSeen
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun loadDonations() {
        viewModelScope.launch {
            try {
                val result = NetworkClient.supabaseDbApi.getDonations(
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
                _donations.value = result
                if (result.isNotEmpty()) _latestDonation.value = result.first()
            } catch (e: Exception) {
                Log.e("AnikuVM", "loadDonations error: ${e.message}")
            }
        }
    }

    fun markDonationSeen() {
        _lastSeenDonationId.value = _donations.value.firstOrNull()?.id
    }

    // ─────────────── SECURITY ───────────────
    val appLockEnabled: StateFlow<Boolean> = settingsStore.appLockEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val appLockType: StateFlow<String> = settingsStore.appLockTypeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "pin")
    val appPin: StateFlow<String> = settingsStore.appPinFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun saveAppLock(enabled: Boolean, type: String, pin: String) {
        viewModelScope.launch { settingsStore.saveAppLock(enabled, type, pin) }
    }

    fun loadFeed() {
        viewModelScope.launch {
            _isFeedLoading.value = true
            try {
                val fetchedPosts = NetworkClient.supabaseDbApi.getPosts(
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
                _posts.value = fetchedPosts
                val likesMap = mutableMapOf<String, List<String>>()
                fetchedPosts.forEach { post ->
                    val likes = NetworkClient.supabaseDbApi.getLikes(
                        postIdQuery = "eq.${post.id}",
                        authHeader = "Bearer $SUPABASE_ANON_KEY",
                        apiKey = SUPABASE_ANON_KEY
                    )
                    likesMap[post.id] = likes.map { it.user_id }
                }
                _postLikes.value = likesMap
            } catch (e: Exception) {
                _feedError.value = "Gagal memuat feed: ${e.message}"
            } finally {
                _isFeedLoading.value = false
            }
        }
    }

    fun createPost(caption: String?, imageUrl: String?, sharedAnime: SharedAnimeRef? = null) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) {
            _feedError.value = "Kamu harus login untuk posting"
            return
        }
        if (caption.isNullOrBlank() && imageUrl == null && sharedAnime == null) {
            _feedError.value = "Post harus ada caption atau foto"
            return
        }
        viewModelScope.launch {
            _isCreatingPost.value = true
            try {
                NetworkClient.supabaseDbApi.insertPost(
                    data = PostRequest(
                        user_id = currentSession.userId ?: "",
                        username = currentSession.username ?: currentSession.email?.substringBefore("@") ?: "Anonymous",
                        avatar_url = currentSession.avatarUrl,
                        is_admin = currentSession.isAdmin,
                        caption = caption?.trim(),
                        image_url = imageUrl,
                        anime_slug = sharedAnime?.slug,
                        anime_title = sharedAnime?.title,
                        anime_poster = sharedAnime?.poster,
                        anime_type = sharedAnime?.type
                    ),
                    authHeader = "Bearer ${currentSession.token}",
                    apiKey = SUPABASE_ANON_KEY
                )
                _pendingSharedAnime.value = null
                loadFeed()
            } catch (e: Exception) {
                _feedError.value = "Gagal membuat post: ${e.message}"
            } finally {
                _isCreatingPost.value = false
            }
        }
    }

    fun deletePost(postId: String) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) return
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deletePost(
                    idQuery = "eq.$postId",
                    authHeader = "Bearer ${currentSession.token}",
                    apiKey = SUPABASE_ANON_KEY
                )
                _posts.value = _posts.value.filter { it.id != postId }
            } catch (e: Exception) {
                _feedError.value = "Gagal hapus post: ${e.message}"
            }
        }
    }

    fun toggleLike(postId: String) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) {
            _feedError.value = "Login dulu untuk like"
            return
        }
        val userId = currentSession.userId ?: return
        val currentLikes = _postLikes.value[postId] ?: emptyList()
        val alreadyLiked = userId in currentLikes

        _postLikes.value = _postLikes.value.toMutableMap().apply {
            put(postId, if (alreadyLiked) currentLikes - userId else currentLikes + userId)
        }

        viewModelScope.launch {
            try {
                if (alreadyLiked) {
                    NetworkClient.supabaseDbApi.deleteLike(
                        postIdQuery = "eq.$postId",
                        userIdQuery = "eq.$userId",
                        authHeader = "Bearer ${currentSession.token}",
                        apiKey = SUPABASE_ANON_KEY
                    )
                } else {
                    NetworkClient.supabaseDbApi.insertLike(
                        data = PostLikeRequest(post_id = postId, user_id = userId),
                        authHeader = "Bearer ${currentSession.token}",
                        apiKey = SUPABASE_ANON_KEY
                    )
                }
            } catch (e: Exception) {
                _postLikes.value = _postLikes.value.toMutableMap().apply {
                    put(postId, currentLikes)
                }
                _feedError.value = "Gagal like: ${e.message}"
            }
        }
    }

    fun loadComments(postId: String) {
        viewModelScope.launch {
            try {
                val comments = NetworkClient.supabaseDbApi.getComments(
                    postIdQuery = "eq.$postId",
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
                _postComments.value = _postComments.value.toMutableMap().apply {
                    put(postId, comments)
                }
            } catch (e: Exception) {
                _feedError.value = "Gagal memuat komentar: ${e.message}"
            }
        }
    }

    fun addComment(
        postId: String,
        message: String,
        replyToId: String? = null,
        replyToUsername: String? = null
    ) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) {
            _feedError.value = "Login dulu untuk komentar"
            return
        }
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.insertComment(
                    data = PostCommentRequest(
                        post_id = postId,
                        user_id = currentSession.userId ?: "",
                        username = currentSession.username ?: currentSession.email?.substringBefore("@") ?: "Anonymous",
                        avatar_url = currentSession.avatarUrl,
                        message = trimmed,
                        reply_to_id = replyToId,
                        reply_to_username = replyToUsername
                    ),
                    authHeader = "Bearer ${currentSession.token}",
                    apiKey = SUPABASE_ANON_KEY
                )
                loadComments(postId)
            } catch (e: Exception) {
                _feedError.value = "Gagal kirim komentar: ${e.message}"
            }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) return
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deleteComment(
                    idQuery = "eq.$commentId",
                    authHeader = "Bearer ${currentSession.token}",
                    apiKey = SUPABASE_ANON_KEY
                )
                loadComments(postId)
            } catch (e: Exception) {
                _feedError.value = "Gagal hapus komentar: ${e.message}"
            }
        }
    }

    fun uploadPostImage(context: Context, uri: Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _isCreatingPost.value = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val byteBuffer = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var len: Int
                if (inputStream != null) {
                    while (inputStream.read(buffer).also { len = it } != -1) {
                        byteBuffer.write(buffer, 0, len)
                    }
                    inputStream.close()
                }
                val fileBytes = byteBuffer.toByteArray()
                val requestFile = fileBytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, fileBytes.size)
                val body = MultipartBody.Part.createFormData("file", "post_image.jpg", requestFile)
                val presetBody = "aniku_avatar".toRequestBody("text/plain".toMediaTypeOrNull())
                val res = NetworkClient.cloudinaryApi.uploadAvatar(body, presetBody)
                onDone(res.secure_url)
            } catch (e: Exception) {
                _feedError.value = "Gagal upload foto: ${e.message}"
                onDone(null)
            } finally {
                _isCreatingPost.value = false
            }
        }
    }

}
