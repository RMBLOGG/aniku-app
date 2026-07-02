package com.example.network

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.SettingsStore
import com.example.ui.theme.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class AnikuViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    val settingsStore = SettingsStore(appContext)
    val bookmarkManager = BookmarkManager(appContext)
    val watchHistoryManager = WatchHistoryManager(appContext)
    val remoteConfigManager = RemoteConfigManager()

    init {
        remoteConfigManager.fetchAndApply()
    }

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
    val themePreset = settingsStore.themePresetFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "Default")
    val cardStyle = settingsStore.cardStyleFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "Rounded")
    val navStyle = settingsStore.navStyleFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "IconLabel")
    val dataSource = settingsStore.dataSourceFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "Dayynime-v1")

    // Session flow
    val session = settingsStore.sessionFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserSession(null, null, null, null, null, null, false, false, false)
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

    private val _homeCompleted = MutableStateFlow<List<AnimeRaw>>(emptyList())
    val homeCompleted: StateFlow<List<AnimeRaw>> = _homeCompleted.asStateFlow()

    private val _homeTodaySchedule = MutableStateFlow<List<AnimeRaw>>(emptyList())
    val homeTodaySchedule: StateFlow<List<AnimeRaw>> = _homeTodaySchedule.asStateFlow()

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

    private val _isDirectStream = MutableStateFlow(false)
    val isDirectStream: StateFlow<Boolean> = _isDirectStream.asStateFlow()

    private val _resolvedHeaders = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedHeaders: StateFlow<Map<String, String>> = _resolvedHeaders.asStateFlow()

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

    // Popup ban real-time (deteksi via polling) — tampil + force logout begitu kedeteksi banned
    private val _showBannedDialog = MutableStateFlow(false)
    val showBannedDialog: StateFlow<Boolean> = _showBannedDialog.asStateFlow()

    private var banWatcherJob: kotlinx.coroutines.Job? = null

    /**
     * Mulai cek status ban tiap 15 detik selama session aktif.
     * Dipanggil sekali waktu app start / setelah login sukses (lihat init block & login()).
     */
    fun startBanWatcher() {
        banWatcherJob?.cancel()
        banWatcherJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000L)
                val sess = session.value
                val uid = sess.userId
                val token = sess.token
                if (uid == null || token == null) continue
                try {
                    val profileList = NetworkClient.supabaseDbApi.getProfileByUserId(
                        idQuery = "eq.$uid",
                        authHeader = "Bearer $token",
                        apiKey = SUPABASE_ANON_KEY
                    )
                    val profile = profileList.firstOrNull()
                    if (profile?.is_banned == true) {
                        _showBannedDialog.value = true
                        settingsStore.clearSession()
                        stopBanWatcher()
                    }
                } catch (e: Exception) {
                    // Network error sementara — jangan logout, coba lagi di siklus berikutnya
                    Log.e("AnikuVM", "Ban watcher check failed: ${e.message}")
                }
            }
        }
    }

    fun stopBanWatcher() {
        banWatcherJob?.cancel()
        banWatcherJob = null
    }

    fun dismissBannedDialog() {
        _showBannedDialog.value = false
    }

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
        // Pantau status ban tiap 15 detik selama ada user login.
        // Otomatis start saat userId muncul, stop saat logout (userId null).
        viewModelScope.launch {
            session.map { it.userId }.distinctUntilChanged().collect { userId ->
                if (userId != null) startBanWatcher() else stopBanWatcher()
            }
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
                    val appVersion = try { com.example.BuildConfig.VERSION_NAME.trimStart('v') } catch (e: Exception) { "1.3.5" }

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

    // Helper: Jalankan block dengan token valid, auto-refresh + retry kalau 401
    private suspend fun <T> withValidToken(block: suspend (token: String) -> T): T {
        val token = session.value.token
            ?: throw Exception("Belum login")
        return try {
            block(token)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) {
                refreshSession()
                val newToken = session.value.token
                    ?: throw Exception("Sesi habis, silakan login ulang")
                block(newToken)
            } else throw e
        }
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
                // 1. Blacklist duluan (sequential) karena semua section lain butuh ini buat filter
                val blacklistedResponse = retryIO {
                    NetworkClient.supabaseDbApi.getBlacklistedAnime(
                        authHeader = "Bearer $SUPABASE_ANON_KEY",
                        apiKey = SUPABASE_ANON_KEY
                    )
                }
                val blacklist = blacklistedResponse.map { it.anime_slug }.toSet()
                _blacklistedSlugs.value = blacklist

                // 2. Semua section lain di-fetch PARALEL (bukan satu-satu berurutan).
                // Dulu: total waktu = jumlah semua request. Sekarang: total waktu = request TERLAMA aja.
                // Ini juga bikin section-section Home muncul nyaris bareng, bukan netes satu-satu
                // sambil user udah keburu scroll (itu penyebab lag pas baru buka app).
                coroutineScope {
                    launch {
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
                    }

                    launch {
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
                    }

                    val isSamehadaku = dataSource.value == "Dayynime-v2"
                    if (isSamehadaku) {
                        launch {
                            try {
                                val ongoingRes = retryIO { samehadakuApi.getOngoing(page = 1) }
                                _homeOngoing.value = (ongoingRes.data?.animeList ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (oe: Exception) { Log.e("AnikuVM", "Failed samehadaku ongoing", oe) }
                        }
                        launch {
                            try {
                                val recentRes = retryIO { samehadakuApi.getRecent(page = 1) }
                                _homeRecent.value = (recentRes.data?.animeList ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (re: Exception) { Log.e("AnikuVM", "Failed samehadaku recent", re) }
                        }
                        launch {
                            try {
                                val popularRes = retryIO { samehadakuApi.getPopular(page = 1) }
                                _homePopular.value = (popularRes.data?.animeList ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (pe: Exception) { Log.e("AnikuVM", "Failed samehadaku home popular", pe) }
                        }
                        launch {
                            try {
                                val moviesRes = retryIO { samehadakuApi.getMovies(page = 1) }
                                _homeMovies.value = (moviesRes.data?.animeList ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (me: Exception) { Log.e("AnikuVM", "Failed samehadaku home movies", me) }
                        }
                        launch {
                            try {
                                val completedRes = retryIO { samehadakuApi.getCompleted(page = 1) }
                                _homeCompleted.value = (completedRes.data?.animeList ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (ce: Exception) { Log.e("AnikuVM", "Failed samehadaku home completed", ce) }
                        }
                        launch {
                            try {
                                val schedRes = retryIO { samehadakuApi.getSchedule() }
                                val todayDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
                                // Samehadaku pakai nama hari Inggris
                                val engDayNames = listOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
                                val todayEngName = engDayNames[todayDay - 1]
                                val todayList = schedRes.data?.days
                                    ?.firstOrNull { it.day.equals(todayEngName, ignoreCase = true) }
                                    ?.animeList?.map { it.toAnimeRaw() } ?: emptyList()
                                _homeTodaySchedule.value = todayList.filterNot { blacklist.contains(it.slug) }
                            } catch (se: Exception) { Log.e("AnikuVM", "Failed samehadaku home schedule", se) }
                        }
                    } else {
                        launch {
                            try {
                                val homeRes = retryIO { animeApi.getHome() }
                                _homeOngoing.value = (homeRes.ongoing ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                                _homeRecent.value = (homeRes.recent ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            } catch (he: Exception) {
                                Log.e("AnikuVM", "Failed to load home base (ongoing/recent)", he)
                            }
                        }

                        // 5. Load Popular for Section
                        launch {
                            try {
                                val popularRes = retryIO { animeApi.getPopular(page = 1) }
                                _homePopular.value = (popularRes.animes ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            } catch (pe: Exception) {
                                Log.e("AnikuVM", "Failed to load home popular", pe)
                            }
                        }

                        // 6. Load Movies for Section
                        launch {
                            try {
                                val moviesRes = retryIO { animeApi.getMovies(page = 1) }
                                _homeMovies.value = (moviesRes.animes ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            } catch (me: Exception) {
                                Log.e("AnikuVM", "Failed to load home movies", me)
                            }
                        }

                        // 7. Load Completed for Section
                        launch {
                            try {
                                val completedRes = retryIO { animeApi.getCompleted(page = 1) }
                                _homeCompleted.value = (completedRes.animes ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                            } catch (ce: Exception) {
                                Log.e("AnikuVM", "Failed to load home completed", ce)
                            }
                        }

                        // 8. Load Today Schedule
                        launch {
                            try {
                                val schedRes = retryIO { animeApi.getSchedule() }
                                val todayDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
                                val sched = schedRes.schedule
                                val todayList = when (todayDay) {
                                    1 -> sched?.minggu
                                    2 -> sched?.senin
                                    3 -> sched?.selasa
                                    4 -> sched?.rabu
                                    5 -> sched?.kamis
                                    6 -> sched?.jumat ?: sched?.jumatAlt
                                    7 -> sched?.sabtu
                                    else -> null
                                } ?: emptyList()
                                _homeTodaySchedule.value = todayList.filterNot { blacklist.contains(it.slug) }
                            } catch (se: Exception) {
                                Log.e("AnikuVM", "Failed to load home schedule", se)
                            }
                        }
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
        _isDirectStream.value = false
        _resolvedHeaders.value = emptyMap()
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
                                val resolvedUrl = linkRes.data?.url ?: firstUrl
                                if (isDirectUrl(resolvedUrl)) {
                                    _activeStreamUrl.value = resolvedUrl
                                    _resolvedHeaders.value = mapOf(
                                        "Referer" to "https://v2.samehadaku.how/",
                                        "Origin" to "https://v2.samehadaku.how"
                                    )
                                    _isDirectStream.value = true
                                } else {
                                    val extracted = withContext(Dispatchers.IO) {
                                        VideoExtractor.resolve(resolvedUrl, "https://v2.samehadaku.how/", appContext)
                                    }
                                    if (extracted != null) {
                                        _activeStreamUrl.value = extracted.url
                                        _resolvedHeaders.value = extracted.headers
                                        _isDirectStream.value = true
                                    } else {
                                        _activeStreamUrl.value = resolvedUrl
                                        _resolvedHeaders.value = emptyMap()
                                        _isDirectStream.value = false
                                    }
                                }
                            } catch (e: Exception) {
                                _activeStreamUrl.value = firstUrl
                                _resolvedHeaders.value = emptyMap()
                                _isDirectStream.value = false
                            }
                        } else {
                            _activeStreamUrl.value = firstUrl
                            _isDirectStream.value = isDirectUrl(firstUrl)
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
                        val firstUrl = streamList[0].url
                        val resolved = withContext(Dispatchers.IO) {
                            VideoExtractor.resolve(firstUrl, null, appContext)
                        }
                        if (resolved != null) {
                            _activeStreamUrl.value = resolved.url
                            _resolvedHeaders.value = resolved.headers
                            _isDirectStream.value = true
                        } else {
                            _activeStreamUrl.value = firstUrl
                            _resolvedHeaders.value = emptyMap()
                            _isDirectStream.value = isDirectUrl(firstUrl)
                        }
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
            if (rawUrl.startsWith("samehadaku_server:")) {
                val serverId = rawUrl.removePrefix("samehadaku_server:")
                _isStreamLoading.value = true
                _activeStreamUrl.value = null
                _isDirectStream.value = false
                viewModelScope.launch {
                    try {
                        val linkRes = retryIO { samehadakuApi.getServerLink(serverId) }
                        val resolvedUrl = linkRes.data?.url
                        if (!resolvedUrl.isNullOrEmpty()) {
                            if (isDirectUrl(resolvedUrl)) {
                                _activeStreamUrl.value = resolvedUrl
                                _resolvedHeaders.value = mapOf(
                                    "Referer" to "https://v2.samehadaku.how/",
                                    "Origin" to "https://v2.samehadaku.how"
                                )
                                _isDirectStream.value = true
                            } else {
                                val extracted = withContext(Dispatchers.IO) {
                                    VideoExtractor.resolve(resolvedUrl, "https://v2.samehadaku.how/", appContext)
                                }
                                if (extracted != null) {
                                    _activeStreamUrl.value = extracted.url
                                    _resolvedHeaders.value = extracted.headers
                                    _isDirectStream.value = true
                                } else {
                                    _activeStreamUrl.value = resolvedUrl
                                    _resolvedHeaders.value = emptyMap()
                                    _isDirectStream.value = false
                                }
                            }
                        } else {
                            Log.w("AnikuVM", "Server $serverId returned empty url, using raw")
                            _activeStreamUrl.value = rawUrl
                            _resolvedHeaders.value = emptyMap()
                            _isDirectStream.value = false
                        }
                    } catch (e: Exception) {
                        Log.e("AnikuVM", "Failed resolve server $serverId: ${e.message}", e)
                        _activeStreamUrl.value = rawUrl
                        _resolvedHeaders.value = emptyMap()
                        _isDirectStream.value = false
                    } finally {
                        _isStreamLoading.value = false
                    }
                }
            } else {
                _isStreamLoading.value = true
                _activeStreamUrl.value = null
                _isDirectStream.value = false
                viewModelScope.launch {
                    // Blogger butuh Main thread (WebView), host lain pakai IO
                    val isBlogger = rawUrl.contains("blogger.com") || rawUrl.contains("blogspot.com")
                    val resolved = if (isBlogger) {
                        VideoExtractor.resolve(rawUrl, null, appContext)
                    } else {
                        withContext(Dispatchers.IO) {
                            VideoExtractor.resolve(rawUrl, null, appContext)
                        }
                    }
                    if (resolved != null) {
                        _activeStreamUrl.value = resolved.url
                        _resolvedHeaders.value = resolved.headers
                        _isDirectStream.value = true
                    } else {
                        _activeStreamUrl.value = rawUrl
                        _resolvedHeaders.value = emptyMap()
                        _isDirectStream.value = isDirectUrl(rawUrl)
                    }
                    _isStreamLoading.value = false
                }
            }
        }
    }

    fun switchToDirectStream(url: String, headers: Map<String, String> = emptyMap()) {
        _activeStreamUrl.value = url
        _resolvedHeaders.value = headers
        _isDirectStream.value = true
    }

    private fun isDirectUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".mkv")
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
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401 || e.code() == 400) {
                Log.e("AnikuVM", "Refresh token invalid (${e.code()}), clearing session")
                settingsStore.clearSession()
            } else {
                Log.e("AnikuVM", "Token refresh failed with HTTP ${e.code()}, keeping session")
            }
        } catch (e: Exception) {
            // Network error (timeout, no internet) - jangan logout user
            Log.e("AnikuVM", "Token refresh network error: ${e.message}, keeping session")
            return
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
                    isAdmin = profile?.isAdmin() ?: false,
                    isModerator = profile?.isModerator() ?: false,
                    isBanned = profile?.is_banned ?: false,
                    userNumber = profile?.user_number
                )

                settingsStore.saveSession(activeSession)
                _authLoading.value = false
                onSuccess()
            } catch (e: retrofit2.HttpException) {
                _authLoading.value = false
                val errBody = e.response()?.errorBody()?.string() ?: ""
                _authError.value = if (errBody.contains("email_not_confirmed", ignoreCase = true)) {
                    "Email kamu belum diverifikasi. Cek inbox (atau folder spam) dan klik link konfirmasinya dulu ya."
                } else {
                    "Login gagal (HTTP ${e.code()}): $errBody"
                }
                Log.e("AnikuVM", "Login HttpException: ${e.code()} - $errBody")
            } catch (e: Exception) {
                _authLoading.value = false
                _authError.value = "${e.javaClass.simpleName}: ${e.message}"
                Log.e("AnikuVM", "Login Exception", e)
            }
        }
    }

    // Login pakai Google ID Token (dipanggil setelah Credential Manager berhasil ambil idToken)
    fun loginWithGoogle(idToken: String, onSuccess: () -> Unit) {
        _authLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            try {
                val res = NetworkClient.supabaseAuthApi.signInWithIdToken(
                    request = IdTokenSignInRequest(id_token = idToken),
                    apiKey = SUPABASE_ANON_KEY
                )
                val token = res.access_token
                if (token == null) {
                    _authError.value = "Login Google gagal: Sesi tidak ditemukan."
                    _authLoading.value = false
                    return@launch
                }
                val uId = res.user?.id ?: ""
                val emailFromRes = res.user?.email ?: ""

                // Kasih waktu trigger handle_new_user jalan kalau ini user baru
                kotlinx.coroutines.delay(800)

                val profileList = try {
                    NetworkClient.supabaseDbApi.getProfileByUserId(
                        idQuery = "eq.$uId",
                        authHeader = "Bearer $token",
                        apiKey = SUPABASE_ANON_KEY
                    )
                } catch (e: Exception) {
                    emptyList()
                }

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
                    email = emailFromRes,
                    username = profile?.username
                        ?: (res.user?.user_metadata?.get("full_name")?.toString()
                            ?: res.user?.user_metadata?.get("name")?.toString()
                            ?: emailFromRes.substringBefore("@")),
                    avatarUrl = profile?.avatar_url
                        ?: res.user?.user_metadata?.get("avatar_url")?.toString(),
                    isAdmin = profile?.isAdmin() ?: false,
                    isModerator = profile?.isModerator() ?: false,
                    isBanned = profile?.is_banned ?: false,
                    userNumber = profile?.user_number
                )

                settingsStore.saveSession(activeSession)
                _authLoading.value = false
                onSuccess()
            } catch (e: retrofit2.HttpException) {
                _authLoading.value = false
                val errBody = e.response()?.errorBody()?.string() ?: "no body"
                _authError.value = "Login Google gagal (HTTP ${e.code()}): $errBody"
                Log.e("AnikuVM", "Google Login HttpException: ${e.code()} - $errBody")
            } catch (e: Exception) {
                _authLoading.value = false
                _authError.value = "Login Google gagal: ${e.javaClass.simpleName} - ${e.message}"
                Log.e("AnikuVM", "Google Login Exception", e)
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
                    // Email confirmation aktif: user berhasil didaftarkan tapi belum ada session
                    // sampai mereka klik link konfirmasi di email.
                    _authError.value = "Pendaftaran berhasil! Cek email kamu (termasuk folder spam) untuk verifikasi akun sebelum login ya."
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
                    isAdmin = profile?.isAdmin() ?: false,
                    isModerator = profile?.isModerator() ?: false,
                    isBanned = profile?.is_banned ?: false,
                    userNumber = profile?.user_number
                )

                settingsStore.saveSession(activeSession)
                _authLoading.value = false
                onSuccess()
            } catch (e: retrofit2.HttpException) {
                _authLoading.value = false
                val errBody = e.response()?.errorBody()?.string() ?: "no body"
                _authError.value = "Daftar gagal (HTTP ${e.code()}): $errBody"
                Log.e("AnikuVM", "Register HttpException: ${e.code()} - $errBody")
            } catch (e: Exception) {
                _authLoading.value = false
                _authError.value = "Daftar gagal: ${e.javaClass.simpleName} - ${e.message}"
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

    fun uploadBanner(uri: Uri, onProgress: (Boolean) -> Unit) {
        val sess = session.value
        val token = sess.token ?: return
        val uId = sess.userId ?: return
        _isUploadingBanner.value = true
        onProgress(true)
        viewModelScope.launch {
            try {
                // 1. Baca bytes dari URI (bisa jpg/png/gif, semua ditangani sama sebagai bytes mentah)
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

                // 2. Siapkan multipart request, deteksi GIF dari signature file biar Cloudinary
                // tidak strip animasinya (nama file & content-type disesuaikan)
                val isGif = fileBytes.size > 3 &&
                    fileBytes[0] == 'G'.code.toByte() &&
                    fileBytes[1] == 'I'.code.toByte() &&
                    fileBytes[2] == 'F'.code.toByte()
                val mimeType = if (isGif) "image/gif" else "image/*"
                val fileName = if (isGif) "banner.gif" else "banner.jpg"
                val requestFile = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull(), 0, fileBytes.size)
                val body = MultipartBody.Part.createFormData("file", fileName, requestFile)
                val presetBody = "aniku_banner".toRequestBody("text/plain".toMediaTypeOrNull())

                // 3. Upload ke Cloudinary preset khusus banner (unsigned)
                val cloudinaryRes = NetworkClient.cloudinaryApi.uploadAvatar(body, presetBody)
                val secureUrl = cloudinaryRes.secure_url

                // 4. Simpan url ke kolom banner_url di Supabase
                NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.$uId",
                    profile = mapOf("banner_url" to secureUrl),
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )

                _ownBannerUrl.value = secureUrl
                _isUploadingBanner.value = false
                onProgress(false)
            } catch (e: java.lang.Exception) {
                _isUploadingBanner.value = false
                onProgress(false)
                Log.e("AnikuVM", "Cloudinary banner upload failed", e)
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsStore.clearSession()
            onComplete()
        }
    }

    // --- USER LIST / DIREKTORI PENGGUNA (publik, beda dari adminUsers yang khusus admin panel) ---
    private val _userDirectory = MutableStateFlow<List<ProfileDto>>(emptyList())
    val userDirectory: StateFlow<List<ProfileDto>> = _userDirectory.asStateFlow()

    private val _isUserDirectoryLoading = MutableStateFlow(false)
    val isUserDirectoryLoading: StateFlow<Boolean> = _isUserDirectoryLoading.asStateFlow()

    fun loadUserDirectory() {
        val authHeader = getAuthHeader()
        _isUserDirectoryLoading.value = true
        viewModelScope.launch {
            try {
                _userDirectory.value = NetworkClient.supabaseDbApi.getProfiles(authHeader, SUPABASE_ANON_KEY)
                    .sortedBy { it.user_number ?: Int.MAX_VALUE }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal load user directory", e)
            } finally {
                _isUserDirectoryLoading.value = false
            }
        }
    }

    // Admin Panel Database Operations
    fun loadAdminDetails() {
        if (!session.value.isAdmin && !session.value.isModerator) return
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
                // Pakai RPC khusus (security definer) biar moderator bisa ban
                // tanpa punya akses UPDATE langsung ke tabel profiles (gak bisa ubah role/id).
                val response = NetworkClient.supabaseDbApi.toggleUserBan(
                    body = mapOf(
                        "target_user_id" to userIdToModify,
                        "new_ban_status" to newBanStatus
                    ),
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

    fun swapUserNumber(profileA: ProfileDto, profileB: ProfileDto) {
        if (!session.value.isAdmin) return
        val authHeader = getAuthHeader()
        val numA = profileA.user_number ?: return
        val numB = profileB.user_number ?: return
        viewModelScope.launch {
            try {
                // Temp set A to -1 to avoid unique conflict
                NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.${profileA.id}",
                    profile = mapOf("user_number" to -1),
                    authHeader = authHeader, apiKey = SUPABASE_ANON_KEY
                )
                // Set B to A's number
                NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.${profileB.id}",
                    profile = mapOf("user_number" to numA),
                    authHeader = authHeader, apiKey = SUPABASE_ANON_KEY
                )
                // Set A to B's number
                NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.${profileA.id}",
                    profile = mapOf("user_number" to numB),
                    authHeader = authHeader, apiKey = SUPABASE_ANON_KEY
                )
                _banStatusMessage.value = "ID #$numA ↔ #$numB berhasil ditukar"
                loadAdminDetails()
            } catch (e: Exception) {
                _banStatusMessage.value = "Gagal tukar ID: ${e.message}"
            }
        }
    }

    fun updateUserRole(profile: ProfileDto, newRole: String) {
        if (!session.value.isAdmin) return
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.${profile.id}",
                    profile = mapOf(
                        "role" to newRole,
                        "is_admin" to (newRole == "admin")
                    ),
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                if (response.isSuccessful || response.code() == 204) {
                    _banStatusMessage.value = "Role ${profile.username} diubah ke $newRole"
                    loadAdminDetails()
                } else {
                    val errBody = response.errorBody()?.string() ?: "Unknown error"
                    _banStatusMessage.value = "Gagal: ${response.code()} - $errBody"
                }
            } catch (e: Exception) {
                _banStatusMessage.value = "Error: ${e.message}"
                Log.e("AnikuVM", "Failed updating role for ${profile.id}", e)
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

    fun updatePasswordWithToken(accessToken: String, newPassword: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseAuthApi.updateUserPassword(
                    UpdatePasswordRequest(newPassword),
                    SUPABASE_ANON_KEY,
                    "Bearer $accessToken"
                )
                if (response.isSuccessful) {
                    onComplete(true, null)
                } else {
                    val errBody = response.errorBody()?.string()
                    Log.e("AnikuVM", "Update password failed: ${response.code()} - $errBody")
                    onComplete(false, "Gagal update password (${response.code()})")
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Update password exception", e)
                onComplete(false, e.message)
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

    fun changeThemePreset(preset: String) {
        viewModelScope.launch { settingsStore.setThemePreset(preset) }
    }

    fun changeCardStyle(style: String) {
        viewModelScope.launch { settingsStore.setCardStyle(style) }
    }

    fun changeNavStyle(style: String) {
        viewModelScope.launch { settingsStore.setNavStyle(style) }
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

    // Toggle notifikasi chat (subscribe/unsubscribe topic FCM "chat_updates")
    val chatNotifEnabled: StateFlow<Boolean> = settingsStore.chatNotifEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun toggleChatNotif() {
        val newValue = !chatNotifEnabled.value
        viewModelScope.launch { settingsStore.setChatNotifEnabled(newValue) }
        val fcm = com.google.firebase.messaging.FirebaseMessaging.getInstance()
        if (newValue) {
            fcm.subscribeToTopic("chat_updates")
        } else {
            fcm.unsubscribeFromTopic("chat_updates")
        }
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
                val messagesDeferred = NetworkClient.supabaseDbApi.getChatMessages(
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
                // Fetch semua profiles sekali untuk manual-join user_number
                // (Supabase auto-join via PostgREST schema cache tidak reliable)
                val profilesMap = try {
                    NetworkClient.supabaseDbApi.getProfiles(
                        authHeader = getAuthHeader(),
                        apiKey = SUPABASE_ANON_KEY
                    ).associateBy { it.id }
                } catch (e: Exception) {
                    Log.e("AnikuVM", "Failed fetching profiles for chat join", e)
                    emptyMap()
                }
                val messages = messagesDeferred.map { msg ->
                    msg.copy(
                        user_number = profilesMap[msg.user_id]?.user_number,
                        season_level = profilesMap[msg.user_id]?.season_level
                    )
                }
                // API mengembalikan urutan terbaru dulu (desc) agar limit menangkap
                // 100 pesan TERBARU, lalu di-reverse di sini jadi kronologis (lama -> baru)
                // supaya tampilan chat tetap normal dari atas ke bawah.
                _chatMessages.value = messages.reversed()
            } catch (e: retrofit2.HttpException) {
                val errBody = e.response()?.errorBody()?.string() ?: "no body"
                _chatError.value = "HTTP ${e.code()}: $errBody"
                Log.e("AnikuVM", "loadChatMessages failed: HTTP ${e.code()} - $errBody")
            } catch (e: Exception) {
                _chatError.value = "Gagal memuat pesan: ${e.message}"
                Log.e("AnikuVM", "loadChatMessages failed", e)
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
                withValidToken { token ->
                    NetworkClient.supabaseDbApi.insertChatMessage(
                        data = ChatMessageRequest(
                            user_id = currentSession.userId ?: "",
                            username = currentSession.username ?: currentSession.email?.substringBefore("@") ?: "Anonymous",
                            avatar_url = currentSession.avatarUrl,
                            role = when { currentSession.isAdmin -> "admin"; currentSession.isModerator -> "moderator"; else -> "user" },
                            is_admin = currentSession.isAdmin,
                            user_number = currentSession.userNumber,
                            message = trimmed,
                            reply_to_id = replyToId,
                            reply_to_username = replyToUsername,
                            reply_to_message = replyToMessage,
                            image_url = imageUrl
                        ),
                        authHeader = "Bearer $token",
                        apiKey = SUPABASE_ANON_KEY
                    )
                }
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

    // ─────────────── SEASON XP / LEVEL ───────────────
    private val _seasonXp = MutableStateFlow(0)
    val seasonXp: StateFlow<Int> = _seasonXp.asStateFlow()

    private val _seasonLevel = MutableStateFlow(1)
    val seasonLevel: StateFlow<Int> = _seasonLevel.asStateFlow()

    private val _ownBannerUrl = MutableStateFlow<String?>(null)
    val ownBannerUrl: StateFlow<String?> = _ownBannerUrl.asStateFlow()

    private val _isUploadingBanner = MutableStateFlow(false)
    val isUploadingBanner: StateFlow<Boolean> = _isUploadingBanner.asStateFlow()

    // Ambil XP/level/banner musim ini dari profile sendiri (dipanggil saat ProfileScreen dibuka)
    fun loadSeasonProgress() {
        viewModelScope.launch {
            val uid = session.value.userId ?: return@launch
            try {
                val result = withValidToken { token ->
                    NetworkClient.supabaseDbApi.getProfileByUserId(
                        idQuery = "eq.$uid",
                        authHeader = "Bearer $token",
                        apiKey = SUPABASE_ANON_KEY
                    )
                }
                val profile = result.firstOrNull()
                _seasonXp.value = profile?.season_xp ?: 0
                _seasonLevel.value = profile?.season_level ?: 1
                _ownBannerUrl.value = profile?.banner_url
            } catch (e: Exception) {
                Log.e("AnikuVM", "loadSeasonProgress error: ${e.message}")
            }
        }
    }

    // Dipanggil saat user udah nonton episode min 80% durasi.
    // Dedup di-handle server-side (unique constraint + ignore-duplicates),
    // jadi aman dipanggil berkali-kali untuk episode yang sama.
    fun reportWatchEvent(animeSlug: String, episodeSlug: String) {
        val uid = session.value.userId ?: return
        viewModelScope.launch {
            try {
                withValidToken { token ->
                    NetworkClient.supabaseDbApi.insertWatchEvent(
                        data = WatchEventRequest(
                            user_id = uid,
                            anime_slug = animeSlug,
                            episode_slug = episodeSlug
                        ),
                        authHeader = "Bearer $token",
                        apiKey = SUPABASE_ANON_KEY
                    )
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "reportWatchEvent error: ${e.message}")
            }
        }
    }

    // ─────────────── PUBLIC USER PROFILE (lihat profil orang lain) ───────────────
    private val _viewedProfile = MutableStateFlow<ProfileDto?>(null)
    val viewedProfile: StateFlow<ProfileDto?> = _viewedProfile.asStateFlow()

    private val _viewedProfileDonationTotal = MutableStateFlow(0)
    val viewedProfileDonationTotal: StateFlow<Int> = _viewedProfileDonationTotal.asStateFlow()

    private val _viewedProfileChatCount = MutableStateFlow(0)
    val viewedProfileChatCount: StateFlow<Int> = _viewedProfileChatCount.asStateFlow()

    private val _isViewedProfileLoading = MutableStateFlow(false)
    val isViewedProfileLoading: StateFlow<Boolean> = _isViewedProfileLoading.asStateFlow()

    fun loadPublicUserProfile(userId: String) {
        viewModelScope.launch {
            _isViewedProfileLoading.value = true
            _viewedProfile.value = null
            _viewedProfileDonationTotal.value = 0
            _viewedProfileChatCount.value = 0
            val authHeader = getAuthHeader() // fallback anon key kalau belum login (guest tetap bisa liat)
            try {
                val profileList = NetworkClient.supabaseDbApi.getProfileByUserId(
                    idQuery = "eq.$userId",
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                val profile = profileList.firstOrNull()
                _viewedProfile.value = profile

                if (profile?.username != null) {
                    try {
                        val donations = NetworkClient.supabaseDbApi.getDonationsBySupporter(
                            supporterNameQuery = "eq.${profile.username}",
                            authHeader = authHeader,
                            apiKey = SUPABASE_ANON_KEY
                        )
                        _viewedProfileDonationTotal.value = donations.sumOf { it.total_amount ?: 0 }
                    } catch (e: Exception) {
                        Log.e("AnikuVM", "loadPublicUserProfile donations error: ${e.message}")
                    }
                }

                try {
                    val ids = NetworkClient.supabaseDbApi.getChatMessageIds(
                        userIdQuery = "eq.$userId",
                        authHeader = authHeader,
                        apiKey = SUPABASE_ANON_KEY
                    )
                    _viewedProfileChatCount.value = ids.size
                } catch (e: Exception) {
                    Log.e("AnikuVM", "loadPublicUserProfile chat count error: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "loadPublicUserProfile error: ${e.message}")
            } finally {
                _isViewedProfileLoading.value = false
            }
        }
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
                withValidToken { token ->
                    NetworkClient.supabaseDbApi.insertPost(
                        data = PostRequest(
                            user_id = currentSession.userId ?: "",
                            username = currentSession.username ?: currentSession.email?.substringBefore("@") ?: "Anonymous",
                            avatar_url = currentSession.avatarUrl,
                            role = when { currentSession.isAdmin -> "admin"; currentSession.isModerator -> "moderator"; else -> "user" },
                            is_admin = currentSession.isAdmin,
                            caption = caption?.trim(),
                            image_url = imageUrl,
                            anime_slug = sharedAnime?.slug,
                            anime_title = sharedAnime?.title,
                            anime_poster = sharedAnime?.poster,
                            anime_type = sharedAnime?.type
                        ),
                        authHeader = "Bearer $token",
                        apiKey = SUPABASE_ANON_KEY
                    )
                }
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

    // ================================================================
    // NOBAR (Watch Party) — sinkronisasi realtime via Firebase Realtime DB
    // ================================================================

    private val _nobarRoom = MutableStateFlow<NobarManager.RoomState?>(null)
    val nobarRoom: StateFlow<NobarManager.RoomState?> = _nobarRoom.asStateFlow()

    private val _nobarError = MutableStateFlow<String?>(null)
    val nobarError: StateFlow<String?> = _nobarError.asStateFlow()

    private val _isNobarLoading = MutableStateFlow(false)
    val isNobarLoading: StateFlow<Boolean> = _isNobarLoading.asStateFlow()

    private var nobarObserveJob: kotlinx.coroutines.Job? = null

    private val _activeNobarRooms = MutableStateFlow<List<NobarManager.ActiveRoomSummary>>(emptyList())
    val activeNobarRooms: StateFlow<List<NobarManager.ActiveRoomSummary>> = _activeNobarRooms.asStateFlow()
    private var activeRoomsObserveJob: kotlinx.coroutines.Job? = null

    /** Dipanggil saat halaman daftar Nobar dibuka. Aman dipanggil berkali-kali. */
    fun startObservingActiveNobarRooms() {
        if (activeRoomsObserveJob?.isActive == true) return
        activeRoomsObserveJob = viewModelScope.launch {
            NobarManager.observeActiveRooms().collect { rooms ->
                _activeNobarRooms.value = rooms
            }
        }
    }

    /** Dipanggil saat halaman daftar Nobar ditutup, supaya listener tidak nyangkut terus. */
    fun stopObservingActiveNobarRooms() {
        activeRoomsObserveJob?.cancel()
        activeRoomsObserveJob = null
    }

    /** Apakah user saat ini adalah host dari room yang sedang diikuti. */
    val isNobarHost: Boolean
        get() = _nobarRoom.value?.hostUid == session.value.userId && _nobarRoom.value != null

    fun createNobarRoom(
        animeSlug: String,
        animeTitle: String,
        animePoster: String,
        episodeSlug: String,
        episodeTitle: String,
        onResult: (roomCode: String?) -> Unit
    ) {
        val userId = session.value.userId
        val username = session.value.username ?: "Host"
        if (userId == null) {
            _nobarError.value = "Kamu harus login untuk membuat room Nobar."
            onResult(null)
            return
        }
        _isNobarLoading.value = true
        _nobarError.value = null
        viewModelScope.launch {
            val code = NobarManager.createRoom(
                hostUid = userId,
                hostUsername = username,
                animeSlug = animeSlug,
                animeTitle = animeTitle,
                animePoster = animePoster,
                episodeSlug = episodeSlug,
                episodeTitle = episodeTitle,
                dataSource = dataSource.value
            )
            _isNobarLoading.value = false
            if (code != null) {
                startObservingNobarRoom(code)
            } else {
                _nobarError.value = "Gagal membuat room Nobar, coba lagi."
            }
            onResult(code)
        }
    }

    fun joinNobarRoom(roomCode: String, onResult: (success: Boolean) -> Unit) {
        val userId = session.value.userId
        val username = session.value.username ?: "Guest"
        if (userId == null) {
            _nobarError.value = "Kamu harus login untuk join room Nobar."
            onResult(false)
            return
        }
        _isNobarLoading.value = true
        _nobarError.value = null
        viewModelScope.launch {
            val state = NobarManager.joinRoom(roomCode, userId, username)
            _isNobarLoading.value = false
            if (state != null) {
                startObservingNobarRoom(state.roomCode)
                onResult(true)
            } else {
                _nobarError.value = "Room tidak ditemukan. Cek lagi kode room-nya."
                onResult(false)
            }
        }
    }

    private fun startObservingNobarRoom(roomCode: String) {
        nobarObserveJob?.cancel()
        nobarObserveJob = viewModelScope.launch {
            NobarManager.observeRoom(roomCode).collect { state ->
                _nobarRoom.value = state
                if (state == null) {
                    // Room sudah dihapus (host close room)
                    _nobarError.value = "Room Nobar telah ditutup oleh host."
                }
            }
        }
    }

    /**
     * Dipanggil oleh host setiap kali user melakukan play/pause/seek di ExoPlayer.
     * Hanya akan terkirim kalau user saat ini memang host room tersebut —
     * mencegah member non-host mengirim event kontrol yang mempengaruhi semua orang.
     */
    fun nobarUpdatePlayback(isPlaying: Boolean, positionMs: Long) {
        val room = _nobarRoom.value ?: return
        if (!isNobarHost) return
        viewModelScope.launch {
            NobarManager.updatePlaybackState(room.roomCode, isPlaying, positionMs)
        }
    }

    fun leaveNobarRoom() {
        val room = _nobarRoom.value
        val userId = session.value.userId
        nobarObserveJob?.cancel()
        nobarObserveJob = null
        _nobarRoom.value = null
        _nobarError.value = null
        if (room != null && userId != null) {
            viewModelScope.launch {
                withContext(NonCancellable) {
                    if (room.hostUid == userId) {
                        NobarManager.closeRoom(room.roomCode)
                    } else {
                        NobarManager.leaveRoom(room.roomCode, userId)
                    }
                }
            }
        }
    }

    fun clearNobarError() {
        _nobarError.value = null
    }

}
