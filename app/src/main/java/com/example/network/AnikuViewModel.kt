package com.example.network

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.SettingsStore
import com.example.util.orDefault
import com.example.util.nullIfBlank
import com.example.ui.theme.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class AnikuViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    val settingsStore = SettingsStore(appContext)
    val bookmarkManager = BookmarkManager(appContext)
    val watchHistoryManager = WatchHistoryManager(appContext)
    val downloadsManager = DownloadsManager(appContext)
    val remoteConfigManager = RemoteConfigManager()

    init {
        remoteConfigManager.fetchAndApply()
    }

    // ── Presence: total user online di seluruh aplikasi (bukan cuma di chat room) ──
    private val _onlineCount = MutableStateFlow(0)
    val onlineCount: StateFlow<Int> = _onlineCount.asStateFlow()

    // Kirim "kabar hidup" tiap 45 detik selama app kebuka & user login,
    // ga peduli lagi di layar mana. Dianggap offline kalau ga heartbeat >90 detik.
    private fun startAppPresenceHeartbeat() {
        viewModelScope.launch {
            while (true) {
                val currentSession = session.value
                val userId = currentSession.userId
                val token = currentSession.token
                if (!userId.isNullOrBlank() && !token.isNullOrBlank()) {
                    try {
                        val username = currentSession.username.nullIfBlank()
                            ?: currentSession.email?.substringBefore("@") ?: "Anonymous"
                        NetworkClient.supabaseDbApi.upsertPresence(
                            data = mapOf(
                                "user_id" to userId,
                                "username" to username,
                                "avatar_url" to currentSession.avatarUrl,
                                "last_seen" to java.time.Instant.now().toString()
                            ),
                            authHeader = "Bearer $token",
                            apiKey = SUPABASE_ANON_KEY
                        )
                    } catch (_: Exception) {}
                }
                kotlinx.coroutines.delay(45_000L)
            }
        }
    }

    private fun startOnlineCountPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    val cutoff = java.time.Instant.now().minusSeconds(90).toString()
                    val rows = NetworkClient.supabaseDbApi.getOnlinePresence(
                        lastSeenFilter = "gte.$cutoff",
                        authHeader = "Bearer $SUPABASE_ANON_KEY",
                        apiKey = SUPABASE_ANON_KEY
                    )
                    _onlineCount.value = rows.size
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(20_000L)
            }
        }
    }

    // Anime API dengan OkHttp Cache (50MB, 1 jam online / 7 hari offline)
    private val animeApi: AnimeApi by lazy { NetworkClient.animeApi(appContext) }
    private val samehadakuApi: SamehadakuApi by lazy { NetworkClient.samehadakuApi(appContext) }
    private val animekompiApi: AnimekompiApi by lazy { NetworkClient.animekompiApi(appContext) }
    private val donghuaApi: DonghuaApi by lazy { NetworkClient.donghuaApi(appContext) }

    // Nama hari Indonesia sesuai urutan Calendar.DAY_OF_WEEK (1=Minggu ... 7=Sabtu),
    // dipakai buat mapping jadwal tayang Animekompi (Dayynime-v3) yang sudah pakai nama hari Indonesia.
    private val indoDayNames = listOf("minggu", "senin", "selasa", "rabu", "kamis", "jumat", "sabtu")

    // Watch history state
    private val _watchHistory = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val watchHistory: StateFlow<List<WatchHistoryItem>> = _watchHistory.asStateFlow()

    fun refreshWatchHistory() {
        _watchHistory.value = watchHistoryManager.getHistory()
    }

    // ── Downloads (offline, per-user) ──────────────────────────────────
    private val _downloads = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val downloads: StateFlow<List<DownloadRecord>> = _downloads.asStateFlow()

    /** Refresh list download milik user yang lagi login. Kalau belum login, list dikosongin. */
    fun refreshDownloads() {
        val userId = session.value.userId
        if (userId.isNullOrBlank()) {
            _downloads.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            downloadsManager.refreshPendingStatuses()
            val list = downloadsManager.getForUser(userId)
            _downloads.value = list
        }
    }

    /**
     * Mulai download episode yang lagi diputer via ExoPlayer (direct stream hasil resolve).
     * Cuma jalan kalau user udah login — dipanggil dari WatchScreen yang udah nge-gate
     * lewat isLoggedIn/onLoginRequired sebelum manggil ini.
     */
    fun startEpisodeDownload(
        url: String,
        headers: Map<String, String>,
        animeSlug: String,
        animeTitle: String,
        animePoster: String,
        episodeSlug: String,
        episodeTitle: String
    ): Boolean {
        val userId = session.value.userId ?: return false
        val fileName = VideoDownloadManager.buildFileName(animeTitle, episodeTitle, url)
        val downloadId = VideoDownloadManager.enqueueDownload(
            context = appContext,
            url = url,
            headers = headers,
            fileName = fileName
        ) ?: return false
        downloadsManager.addRecord(
            DownloadRecord(
                downloadId = downloadId,
                userId = userId,
                animeSlug = animeSlug,
                animeTitle = animeTitle,
                animePoster = animePoster,
                episodeSlug = episodeSlug,
                episodeTitle = episodeTitle,
                fileName = fileName,
                status = DownloadStatus.PENDING.name
            )
        )
        refreshDownloads()
        return true
    }

    fun deleteDownload(record: DownloadRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadsManager.removeRecord(record)
            withContext(Dispatchers.Main) { refreshDownloads() }
        }
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

        // Sinkron ke Supabase juga (fire-and-forget) biar keliatan di tab Histori profil publik.
        val userId = session.value.userId ?: return
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.upsertUserWatchHistory(
                    data = UserWatchHistoryRequest(
                        user_id = userId,
                        anime_slug = animeSlug,
                        anime_title = animeTitle,
                        anime_poster = animePoster,
                        episode_slug = episodeSlug,
                        episode_title = episodeTitle
                    ),
                    authHeader = getAuthHeader(),
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal sinkron riwayat tontonan ke Supabase: ${e.message}")
            }
        }
    }

    fun clearWatchHistory() {
        watchHistoryManager.clearHistory()
        refreshWatchHistory()

        val userId = session.value.userId ?: return
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deleteAllUserWatchHistory(
                    userIdQuery = "eq.$userId",
                    authHeader = getAuthHeader(),
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal hapus riwayat tontonan di Supabase: ${e.message}")
            }
        }
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
        UserSession(null, null, null, null, null, null, false, false, false, null, false)
    )

    init {
        // Begitu userId muncul (login/restore session), langsung sync FCM token
        // yang tersimpan di SharedPreferences (diisi MainActivity/FCM service).
        viewModelScope.launch {
            session.collect { s ->
                if (!s.userId.isNullOrBlank()) {
                    val prefs = appContext.getSharedPreferences("aniku_fcm", Context.MODE_PRIVATE)
                    val token = prefs.getString("fcm_token", null)
                    if (!token.isNullOrBlank()) syncPushToken(token)
                }
            }
        }
        // Dengerin daftar chat pribadi secara global begitu login/logout,
        // biar badge unread di home nyala real-time gak cuma pas FriendsScreen dibuka.
        viewModelScope.launch {
            session.map { it.userId }.distinctUntilChanged().collect { userId ->
                if (!userId.isNullOrBlank()) {
                    startListeningUserChats()
                } else {
                    stopListeningUserChats()
                }
            }
        }
    }

    /** Upsert FCM token ke Supabase, dipakai buat targeted notif private chat. */
    fun syncPushToken(token: String) {
        val myId = session.value.userId ?: return
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.upsertPushToken(
                    data = PushTokenUpsertRequest(user_id = myId, fcm_token = token),
                    authHeader = getAuthHeader(),
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (e: Exception) {
                Log.e("AnikuVM", "syncPushToken gagal: ${e.message}")
            }
        }
    }

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

    // Blacklisted genre slugs — dipakai buat nyembunyiin genre dari daftar pilihan di Eksplor
    private val _blacklistedGenreSlugs = MutableStateFlow<Set<String>>(emptySet())
    val blacklistedGenreSlugs: StateFlow<Set<String>> = _blacklistedGenreSlugs.asStateFlow()

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

    private val _isSearchLoadingMore = MutableStateFlow(false)
    val isSearchLoadingMore: StateFlow<Boolean> = _isSearchLoadingMore.asStateFlow()

    private val _searchPage = MutableStateFlow(1)
    private val _searchHasNext = MutableStateFlow(false)
    val searchHasNext: StateFlow<Boolean> = _searchHasNext.asStateFlow()

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

    // Dipanggil dari listener ExoPlayer (Screens.kt) waktu playback gagal
    // (403/timeout/manifest invalid/dll) - sebelumnya error ini gak pernah
    // sampai ke sini sama sekali, jadi UI cuma stuck hitam tanpa pesan.
    fun setStreamError(message: String) {
        _streamError.value = message
    }

    private val _isDirectStream = MutableStateFlow(false)
    val isDirectStream: StateFlow<Boolean> = _isDirectStream.asStateFlow()

    private val _resolvedHeaders = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedHeaders: StateFlow<Map<String, String>> = _resolvedHeaders.asStateFlow()

    // Debug info dari percobaan ekstraksi video terakhir yang GAGAL (jatuh ke WebView
    // fallback) - isinya VideoExtractor.lastDebugSnippet. Tujuannya buat yang build via
    // GitHub Actions/CI (gak ada akses Logcat/adb sama sekali) - tinggal buka dialog-nya
    // di UI, copy teksnya atau screenshot, gak perlu colok laptop/adb logcat.
    private val _extractDebugInfo = MutableStateFlow<String?>(null)
    val extractDebugInfo: StateFlow<String?> = _extractDebugInfo.asStateFlow()

    // Info URL hasil resolve TERAKHIR, diisi baik pas berhasil MAUPUN gagal.
    // Beda sama _extractDebugInfo (yang cuma keisi pas gagal total/fallback
    // WebView) - ini buat kasus resolve() "berhasil" (dapet URL, ExoPlayer native
    // kepasang) tapi kontennya salah/placeholder (mis. "Waiting Encoding video"
    // dari host, atau video hitam/0:00 karena URL-nya sebenarnya gak valid).
    // Ditampilin lewat tombol info netral yang SELALU ada (beda sama warning
    // merah yang cuma nongol pas fallback), biar bisa dicek URL persisnya tanpa
    // Logcat/adb.
    private val _lastResolvedUrlInfo = MutableStateFlow<String?>(null)
    val lastResolvedUrlInfo: StateFlow<String?> = _lastResolvedUrlInfo.asStateFlow()

    // Auth flows
    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isUploadingAvatar = MutableStateFlow(false)
    val isUploadingAvatar: StateFlow<Boolean> = _isUploadingAvatar.asStateFlow()

    // ─── Requested Anime (search MAL utk autofill + upload video ke Cloudinary) ───
    private val _jikanSearchResults = MutableStateFlow<List<JikanAnimeData>>(emptyList())
    val jikanSearchResults: StateFlow<List<JikanAnimeData>> = _jikanSearchResults.asStateFlow()

    private val _isSearchingJikan = MutableStateFlow(false)
    val isSearchingJikan: StateFlow<Boolean> = _isSearchingJikan.asStateFlow()

    private val _isUploadingRequestedAnime = MutableStateFlow(false)
    val isUploadingRequestedAnime: StateFlow<Boolean> = _isUploadingRequestedAnime.asStateFlow()

    private val _uploadRequestedAnimeProgress = MutableStateFlow(0)
    val uploadRequestedAnimeProgress: StateFlow<Int> = _uploadRequestedAnimeProgress.asStateFlow()

    private val _requestedAnimeList = MutableStateFlow<List<RequestedAnimeDto>>(emptyList())
    val requestedAnimeList: StateFlow<List<RequestedAnimeDto>> = _requestedAnimeList.asStateFlow()

    private val _requestedAnimeError = MutableStateFlow<String?>(null)
    val requestedAnimeError: StateFlow<String?> = _requestedAnimeError.asStateFlow()

    // Admin management state
    private val _adminUsers = MutableStateFlow<List<ProfileDto>>(emptyList())
    val adminUsers: StateFlow<List<ProfileDto>> = _adminUsers.asStateFlow()

    private val _adminAnnouncements = MutableStateFlow<List<AnnouncementDto>>(emptyList())
    val adminAnnouncements: StateFlow<List<AnnouncementDto>> = _adminAnnouncements.asStateFlow()

    private val _adminFeatured = MutableStateFlow<List<FeaturedAnimeDto>>(emptyList())
    val adminFeatured: StateFlow<List<FeaturedAnimeDto>> = _adminFeatured.asStateFlow()

    private val _adminBlacklist = MutableStateFlow<List<BlacklistedAnimeDto>>(emptyList())
    val adminBlacklist: StateFlow<List<BlacklistedAnimeDto>> = _adminBlacklist.asStateFlow()

    // Feature flags - rollout bertahap "Beta akses duluan". Di-fetch sekali pas app
    // dibuka (lihat init{}), di-cache di memori selama sesi app berjalan.
    private val _featureFlags = MutableStateFlow<Map<String, FeatureFlagDto>>(emptyMap())
    val featureFlags: StateFlow<Map<String, FeatureFlagDto>> = _featureFlags.asStateFlow()

    fun loadFeatureFlags() {
        viewModelScope.launch {
            try {
                val flags = NetworkClient.supabaseDbApi.getFeatureFlags(SUPABASE_ANON_KEY)
                _featureFlags.value = flags.associateBy { it.feature_key }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed loading feature flags", e)
            }
        }
    }

    // Helper utama buat dipanggil dari UI: `if (viewModel.canAccessFeature("fitur_x")) { ... }`
    // Kalau flag-nya belum ada di database sama sekali, default-nya TERTUTUP buat semua
    // (fail-closed) - biar aman, gak keburu kebuka ke semua orang gara-gara lupa insert baris.
    fun canAccessFeature(key: String): Boolean {
        val flag = _featureFlags.value[key] ?: return false
        if (flag.enabled_for_all) return true
        return flag.enabled_for_beta && session.value.isBeta
    }

    // Toggle satu kolom boolean di feature_flags - cuma admin (moderator TIDAK bisa,
    // sama pola-nya kayak updateUserRole). field cuma boleh "enabled_for_beta" atau
    // "enabled_for_all", divalidasi biar gak bisa dipakai buat nulis kolom sembarangan.
    fun toggleFeatureFlag(key: String, field: String, value: Boolean) {
        if (!session.value.isAdmin) return
        if (field != "enabled_for_beta" && field != "enabled_for_all") return
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.updateFeatureFlag(
                    keyQuery = "eq.$key",
                    body = mapOf(field to value),
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                if (response.isSuccessful || response.code() == 204) {
                    loadFeatureFlags()
                } else {
                    Log.e("AnikuVM", "toggleFeatureFlag gagal: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed toggling feature flag $key", e)
            }
        }
    }

    // Bikin flag baru - cuma admin. Default: kebuka buat Beta doang, tertutup buat semua.
    fun createFeatureFlag(key: String, description: String?) {
        if (!session.value.isAdmin) return
        if (key.isBlank()) return
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.insertFeatureFlag(
                    body = FeatureFlagDto(
                        feature_key = key.trim(),
                        enabled_for_beta = true,
                        enabled_for_all = false,
                        description = description?.trim()?.takeIf { it.isNotBlank() }
                    ),
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                if (response.isSuccessful || response.code() == 201 || response.code() == 204) {
                    loadFeatureFlags()
                } else {
                    Log.e("AnikuVM", "createFeatureFlag gagal: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed creating feature flag $key", e)
            }
        }
    }

    private val _adminBlacklistGenres = MutableStateFlow<List<BlacklistedGenreDto>>(emptyList())
    val adminBlacklistGenres: StateFlow<List<BlacklistedGenreDto>> = _adminBlacklistGenres.asStateFlow()

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
        loadBlacklistGenreSlugs()
        loadHomeData()
        loadGenres()
        loadSearchPopular()
        checkForUpdate()
        loadDonations()
        loadFeatureFlags()
        // Presence: heartbeat + polling total online seluruh aplikasi
        startAppPresenceHeartbeat()
        startOnlineCountPolling()
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
                    val latestClean = tagName.trimStart('v').substringBefore('-')
                    val appVersion = (try { com.example.BuildConfig.VERSION_NAME.trimStart('v') } catch (e: Exception) { "1.3.5" }).substringBefore('-')

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
            // Pakai folder khusus app (bukan folder publik) biar gak butuh izin
            // WRITE_EXTERNAL_STORAGE sama sekali -- itu udah gak berlaku lagi
            // di Android 10+ (dicap maxSdkVersion 28), makanya kalau pakai
            // setDestinationInExternalPublicDir bakal SecurityException di Android 10+.
            setDestinationInExternalFilesDir(appContext, android.os.Environment.DIRECTORY_DOWNLOADS, "Aniku-${version}.apk")
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

    private fun loadBlacklistGenreSlugs() {
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.getBlacklistedGenres(
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
                _blacklistedGenreSlugs.value = response.map { it.genre_slug }.toSet()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed to load blacklisted genre slugs", e)
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
                    } else if (dataSource.value == "Dayynime-v3") {
                        launch {
                            try {
                                val homeRes = retryIO { animekompiApi.getHome() }
                                val terbaruRes = retryIO { animekompiApi.getTerbaru(page = 1) }
                                // Samain urutan sama proxy referensi: "recent" dari /home, "ongoing" dari /terbaru
                                _homeRecent.value = (homeRes.data ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                                _homeOngoing.value = (terbaruRes.data ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (he: Exception) { Log.e("AnikuVM", "Failed animekompi home", he) }
                        }
                        launch {
                            try {
                                val popularRes = retryIO { animekompiApi.getPopular(page = 1) }
                                _homePopular.value = (popularRes.data ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (pe: Exception) { Log.e("AnikuVM", "Failed animekompi home popular", pe) }
                        }
                        launch {
                            try {
                                val moviesRes = retryIO { animekompiApi.getMovies(page = 1) }
                                _homeMovies.value = (moviesRes.data ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (me: Exception) { Log.e("AnikuVM", "Failed animekompi home movies", me) }
                        }
                        launch {
                            try {
                                val completedRes = retryIO { animekompiApi.getCompleted(page = 1) }
                                _homeCompleted.value = (completedRes.data ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (ce: Exception) { Log.e("AnikuVM", "Failed animekompi home completed", ce) }
                        }
                        launch {
                            try {
                                val schedRes = retryIO { animekompiApi.getSchedule() }
                                val todayDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
                                val todayName = indoDayNames[todayDay - 1]
                                val todayList = schedRes.data
                                    ?.firstOrNull { (it.day ?: "").lowercase().replace("'", "") == todayName }
                                    ?.list?.map { it.toAnimeRaw() } ?: emptyList()
                                _homeTodaySchedule.value = todayList.filterNot { blacklist.contains(it.slug) }
                            } catch (se: Exception) { Log.e("AnikuVM", "Failed animekompi home schedule", se) }
                        }
                    } else if (dataSource.value == "Dayynime-v4") {
                        launch {
                            try {
                                val homeRes = retryIO { donghuaApi.getHome() }
                                _homeRecent.value = (homeRes.latest_release ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                                _homeCompleted.value = (homeRes.completed_donghua ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (he: Exception) { Log.e("AnikuVM", "Failed donghua home", he) }
                        }
                        launch {
                            try {
                                val ongoingRes = retryIO { donghuaApi.getOngoing() }
                                _homeOngoing.value = (ongoingRes.ongoing_donghua ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (oe: Exception) { Log.e("AnikuVM", "Failed donghua ongoing", oe) }
                        }
                        launch {
                            try {
                                // Donghua nggak punya endpoint "popular" khusus, jadi pakai "latest" sebagai proxy.
                                val latestRes = retryIO { donghuaApi.getLatest() }
                                _homePopular.value = (latestRes.latest_donghua ?: emptyList())
                                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            } catch (pe: Exception) { Log.e("AnikuVM", "Failed donghua home popular", pe) }
                        }
                        launch {
                            // Donghua (Anichin) nggak punya kategori "Movie" terpisah — dikosongin.
                            _homeMovies.value = emptyList()
                        }
                        launch {
                            try {
                                val schedRes = retryIO { donghuaApi.getSchedule() }
                                val todayDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
                                val engDayNames = listOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
                                val todayEngName = engDayNames[todayDay - 1]
                                val todayList = (schedRes.schedule ?: emptyList())
                                    .firstOrNull { it.day.equals(todayEngName, ignoreCase = true) }
                                    ?.donghua_list?.map { it.toAnimeRaw() } ?: emptyList()
                                _homeTodaySchedule.value = todayList.filterNot { blacklist.contains(it.slug) }
                            } catch (se: Exception) { Log.e("AnikuVM", "Failed donghua home schedule", se) }
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

    fun loadSearchPopular() {
        viewModelScope.launch {
            try {
                if (dataSource.value == "Dayynime-v2") {
                    val res = retryIO { samehadakuApi.getPopular(page = 1) }
                    _searchPopular.value = (res.data?.animeList ?: emptyList())
                        .map { it.toAnimeRaw() }.filterNot { _blacklistedSlugs.value.contains(it.slug) }
                } else if (dataSource.value == "Dayynime-v3") {
                    val res = retryIO { animekompiApi.getPopular(page = 1) }
                    _searchPopular.value = (res.data ?: emptyList())
                        .map { it.toAnimeRaw() }.filterNot { _blacklistedSlugs.value.contains(it.slug) }
                } else if (dataSource.value == "Dayynime-v4") {
                    val res = retryIO { donghuaApi.getLatest() }
                    _searchPopular.value = (res.latest_donghua ?: emptyList())
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
        _searchPage.value = 1
        _searchHasNext.value = false
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
                val (items, hasNext) = fetchSearchPage(query, page = 1)
                _searchResults.value = items
                _searchHasNext.value = hasNext
                _isSearchLoading.value = false
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _searchHasNext.value = false
                _isSearchLoading.value = false
                Log.e("AnikuVM", "Failed searching keywords: $query", e)
            }
        }
    }

    fun loadNextSearchPage() {
        val query = _searchQuery.value
        if (query.isBlank() || _isSearchLoading.value || _isSearchLoadingMore.value || !_searchHasNext.value) return
        val nextPage = _searchPage.value + 1
        _isSearchLoadingMore.value = true
        viewModelScope.launch {
            try {
                val (items, hasNext) = fetchSearchPage(query, page = nextPage)
                _searchPage.value = nextPage
                _searchResults.value = _searchResults.value + items
                _searchHasNext.value = hasNext
                _isSearchLoadingMore.value = false
            } catch (e: Exception) {
                _searchHasNext.value = false
                _isSearchLoadingMore.value = false
                Log.e("AnikuVM", "Failed loading next search page for: $query", e)
            }
        }
    }

    private suspend fun fetchSearchPage(query: String, page: Int): Pair<List<AnimeRaw>, Boolean> {
        val blacklist = _blacklistedSlugs.value
        return retryIO {
            if (dataSource.value == "Dayynime-v2") {
                val res = samehadakuApi.search(query, page = page)
                val items = (res.data?.animeList ?: emptyList())
                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                val hasNext = res.pagination?.hasNextPage ?: false
                Pair(items, hasNext)
            } else if (dataSource.value == "Dayynime-v3") {
                val res = animekompiApi.search(keyword = query, page = page)
                val items = (res.data ?: emptyList())
                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                val hasNext = res.pagination?.has_next ?: false
                Pair(items, hasNext)
            } else if (dataSource.value == "Dayynime-v4") {
                // Endpoint search Donghua belum kelihatan support pagination, jadi cuma page 1.
                val res = donghuaApi.search(query)
                val items = (res.data ?: emptyList())
                    .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                Pair(items, false)
            } else {
                val res = animeApi.search(query, page = page)
                val items = (res.animes ?: emptyList()).filterNot { blacklist.contains(it.slug) }
                val hasNext = res.pagination?.hasNext ?: false
                Pair(items, hasNext)
            }
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            try {
                if (dataSource.value == "Dayynime-v2") {
                    val res = retryIO { samehadakuApi.getGenres() }
                    _genres.value = (res.data?.genreList ?: emptyList()).map { it.toGenreRaw() }
                } else if (dataSource.value == "Dayynime-v3") {
                    val res = retryIO { animekompiApi.getGenres() }
                    _genres.value = (res.data ?: emptyList()).map { it.toGenreRaw() }
                        .sortedBy { it.name }
                } else if (dataSource.value == "Dayynime-v4") {
                    val res = retryIO { donghuaApi.getGenres() }
                    _genres.value = (res.data ?: emptyList()).map { it.toGenreRaw() }
                        .sortedBy { it.name }
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
        val page = _explorePage.value
        viewModelScope.launch {
            try {
                val blacklist = _blacklistedSlugs.value

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
                    } else if (dataSource.value == "Dayynime-v3") {
                        val kRes = if (_selectedGenreSlug.value != null) {
                            animekompiApi.getAnimeByGenre(slug = _selectedGenreSlug.value!!, page = page)
                        } else {
                            when (_exploreTab.value) {
                                "Ongoing" -> animekompiApi.getOngoing(page = page)
                                "Completed" -> animekompiApi.getCompleted(page = page)
                                "Movie" -> animekompiApi.getMovies(page = page)
                                "Latest" -> animekompiApi.getTerbaru(page = page)
                                else -> animekompiApi.getOngoing(page = page)
                            }
                        }
                        val items = (kRes.data ?: emptyList()).map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                        val hasNext = kRes.pagination?.has_next ?: items.isNotEmpty()
                        Pair(items, hasNext)
                    } else if (dataSource.value == "Dayynime-v4") {
                        // Donghua nggak punya kategori "Movie" — di-fallback ke ongoing.
                        if (_selectedGenreSlug.value != null) {
                            val dRes = donghuaApi.getByGenre(slug = _selectedGenreSlug.value!!, page = page)
                            val items = (dRes.data ?: emptyList()).map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                            Pair(items, items.isNotEmpty())
                        } else {
                            when (_exploreTab.value) {
                                "Ongoing" -> {
                                    val dRes = donghuaApi.getOngoing(page = page)
                                    val items = (dRes.ongoing_donghua ?: emptyList()).map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                                    Pair(items, items.isNotEmpty())
                                }
                                "Completed" -> {
                                    val dRes = donghuaApi.getCompleted(page = page)
                                    val items = (dRes.completed_donghua ?: emptyList()).map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                                    Pair(items, items.isNotEmpty())
                                }
                                "Latest" -> {
                                    val dRes = donghuaApi.getLatest(page = page)
                                    val items = (dRes.latest_donghua ?: emptyList()).map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                                    Pair(items, items.isNotEmpty())
                                }
                                else -> {
                                    val dRes = donghuaApi.getOngoing(page = page)
                                    val items = (dRes.ongoing_donghua ?: emptyList()).map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                                    Pair(items, items.isNotEmpty())
                                }
                            }
                        }
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
                if (page <= 1) {
                    _exploreAnimes.value = emptyList()
                }
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
                } else if (dataSource.value == "Dayynime-v3") {
                    val res = retryIO { animekompiApi.getSchedule() }
                    val map = mutableMapOf<String, List<AnimeRaw>>()
                    for (dayEntry in res.data ?: emptyList()) {
                        val normalized = (dayEntry.day ?: "").lowercase().replace("'", "")
                        val key = when (normalized) {
                            "minggu" -> "Minggu"; "senin" -> "Senin"; "selasa" -> "Selasa"
                            "rabu" -> "Rabu"; "kamis" -> "Kamis"; "jumat" -> "Jumat"; "sabtu" -> "Sabtu"
                            else -> dayEntry.day ?: ""
                        }
                        map[key] = (dayEntry.list ?: emptyList())
                            .map { it.toAnimeRaw() }.filterNot { blacklist.contains(it.slug) }
                    }
                    _scheduleMap.value = map
                } else if (dataSource.value == "Dayynime-v4") {
                    val res = retryIO { donghuaApi.getSchedule() }
                    val dayNameMap = mapOf(
                        "Sunday" to "Minggu", "Monday" to "Senin", "Tuesday" to "Selasa",
                        "Wednesday" to "Rabu", "Thursday" to "Kamis", "Friday" to "Jumat", "Saturday" to "Sabtu"
                    )
                    val map = mutableMapOf<String, List<AnimeRaw>>()
                    for (day in res.schedule ?: emptyList()) {
                        val key = dayNameMap[day.day] ?: (day.day ?: "")
                        map[key] = (day.donghua_list ?: emptyList())
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
                } else if (dataSource.value == "Dayynime-v3") {
                    val res = retryIO { animekompiApi.getDetail(slug) }
                    _animeDetail.value = res.data?.toDetailData()
                } else if (dataSource.value == "Dayynime-v4") {
                    val res = retryIO { donghuaApi.getDetail(slug) }
                    _animeDetail.value = res.toDetailData()
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
                                        _extractDebugInfo.value = null
                                        _lastResolvedUrlInfo.value = buildResolvedInfoText(resolvedUrl, extracted)
                                    } else {
                                        // Ekstraksi gagal — jangan pakai resolvedUrl mentah kalau itu
                                        // shortlink (short.ink/short.icu/dll) yang DNS-nya di-block ISP,
                                        // WebView bisa ERR_NAME_NOT_RESOLVED. Follow redirect-nya dulu.
                                        _activeStreamUrl.value = VideoExtractor.resolveForWebViewFallback(
                                            resolvedUrl, "https://v2.samehadaku.how/"
                                        )
                                        _resolvedHeaders.value = emptyMap()
                                        _isDirectStream.value = false
                                        _extractDebugInfo.value = VideoExtractor.lastDebugSnippet
                                        _lastResolvedUrlInfo.value = "embedUrl: $resolvedUrl\nresolve GAGAL total -> fallback WebView\nfallbackUrl: ${_activeStreamUrl.value}"
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
                } else if (dataSource.value == "Dayynime-v3") {
                    val res = retryIO { animekompiApi.getEpisode(slug) }
                    val epData = res.data
                    _streamEpisodeTitle.value = epData?.title ?: "Tonton Tayangan"

                    // Urutin mirror: yang namanya ada "bebas iklan" (biasanya direct/tanpa ads) naik duluan,
                    // sama kaya prioritas di web referensi.
                    val rawMirrors = epData?.mirrors ?: emptyList()
                    val streamList = rawMirrors
                        .filter { !it.url.isNullOrBlank() }
                        .sortedByDescending { Regex("bebas iklan", RegexOption.IGNORE_CASE).containsMatchIn(it.name ?: "") }
                        .map { StreamRaw(name = it.name ?: "Server", url = it.url!!) }
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
                            _extractDebugInfo.value = null
                            _lastResolvedUrlInfo.value = buildResolvedInfoText(firstUrl, resolved)
                        } else {
                            _activeStreamUrl.value = VideoExtractor.resolveForWebViewFallback(firstUrl, null)
                            _resolvedHeaders.value = emptyMap()
                            _isDirectStream.value = isDirectUrl(firstUrl)
                            _extractDebugInfo.value = VideoExtractor.lastDebugSnippet
                            _lastResolvedUrlInfo.value = "embedUrl: $firstUrl\nresolve GAGAL total -> fallback WebView\nfallbackUrl: ${_activeStreamUrl.value}"
                        }
                    } else {
                        _streamError.value = "Tidak ada tautan streaming yang tersedia."
                    }
                } else if (dataSource.value == "Dayynime-v4") {
                    val res = retryIO { donghuaApi.getEpisode(slug) }
                    _streamEpisodeTitle.value = res.episode ?: "Tonton Tayangan"

                    // Urutin server: yang namanya nggak ada "[Ads]" naik duluan (biasanya "Premium").
                    val rawServers = res.streaming?.servers ?: emptyList()
                    val streamList = rawServers
                        .filter { !it.url.isNullOrBlank() }
                        .sortedByDescending { !Regex("\\[Ads]", RegexOption.IGNORE_CASE).containsMatchIn(it.name ?: "") }
                        .map { StreamRaw(name = it.name ?: "Server", url = it.url!!) }
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
                            _extractDebugInfo.value = null
                            _lastResolvedUrlInfo.value = buildResolvedInfoText(firstUrl, resolved)
                        } else {
                            _activeStreamUrl.value = VideoExtractor.resolveForWebViewFallback(firstUrl, null)
                            _resolvedHeaders.value = emptyMap()
                            _isDirectStream.value = isDirectUrl(firstUrl)
                            _extractDebugInfo.value = VideoExtractor.lastDebugSnippet
                            _lastResolvedUrlInfo.value = "embedUrl: $firstUrl\nresolve GAGAL total -> fallback WebView\nfallbackUrl: ${_activeStreamUrl.value}"
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
                            _extractDebugInfo.value = null
                            _lastResolvedUrlInfo.value = buildResolvedInfoText(firstUrl, resolved)
                        } else {
                            _activeStreamUrl.value = VideoExtractor.resolveForWebViewFallback(firstUrl, null)
                            _resolvedHeaders.value = emptyMap()
                            _isDirectStream.value = isDirectUrl(firstUrl)
                            _extractDebugInfo.value = VideoExtractor.lastDebugSnippet
                            _lastResolvedUrlInfo.value = "embedUrl: $firstUrl\nresolve GAGAL total -> fallback WebView\nfallbackUrl: ${_activeStreamUrl.value}"
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
                                    _activeStreamUrl.value = VideoExtractor.resolveForWebViewFallback(
                                        resolvedUrl, "https://v2.samehadaku.how/"
                                    )
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
                        _extractDebugInfo.value = null
                        _lastResolvedUrlInfo.value = buildResolvedInfoText(rawUrl, resolved)
                    } else {
                        _activeStreamUrl.value = VideoExtractor.resolveForWebViewFallback(rawUrl, null)
                        _resolvedHeaders.value = emptyMap()
                        _isDirectStream.value = isDirectUrl(rawUrl)
                        _extractDebugInfo.value = VideoExtractor.lastDebugSnippet
                        _lastResolvedUrlInfo.value = "embedUrl: $rawUrl\nresolve GAGAL total -> fallback WebView\nfallbackUrl: ${_activeStreamUrl.value}"
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

    // Bangun teks buat dialog "Info stream". Kalau hasilnya HLS (isHls=true),
    // ikutan ambil isi mentah manifest-nya (pakai header yang sama kayak yang
    // dipakai ExoPlayer) - supaya kalau manifest-nya rusak/malformed di sisi
    // server, keliatan langsung dari sini tanpa user harus buka browser lain
    // & paste URL manual.
    private suspend fun buildResolvedInfoText(embedUrl: String, resolved: ResolvedStream): String {
        val base = "embedUrl: $embedUrl\nresolvedUrl: ${resolved.url}\nisHls: ${resolved.isHls}\nheaders: ${resolved.headers}"
        if (!resolved.isHls) return base
        val manifestPreview = withContext(Dispatchers.IO) {
            VideoExtractor.peekManifestText(resolved.url, resolved.headers)
        }
        return "$base\n\n--- Isi manifest (debug) ---\n$manifestPreview"
    }

    // Toggle Bookmarks - local cache tetap dipertahankan biar UI instan, tapi sekarang juga
    // disinkron ke Supabase (fire-and-forget) biar keliatan di tab Favorit profil publik.
    fun toggleBookmark(slug: String, title: String, poster: String, type: String? = null, ep: String? = null) {
        val currentlyBookmarked = bookmarkManager.isBookmarked(slug)
        if (currentlyBookmarked) {
            bookmarkManager.removeBookmark(slug)
        } else {
            bookmarkManager.addBookmark(BookmarkedAnime(slug, title, poster, type, ep))
        }
        refreshBookmarks()

        val userId = session.value.userId ?: return
        viewModelScope.launch {
            try {
                if (currentlyBookmarked) {
                    NetworkClient.supabaseDbApi.deleteUserBookmark(
                        userIdQuery = "eq.$userId",
                        animeSlugQuery = "eq.$slug",
                        authHeader = getAuthHeader(),
                        apiKey = SUPABASE_ANON_KEY
                    )
                } else {
                    NetworkClient.supabaseDbApi.upsertUserBookmark(
                        data = UserBookmarkRequest(
                            user_id = userId,
                            anime_slug = slug,
                            title = title,
                            poster = poster,
                            type = type,
                            episode = ep
                        ),
                        authHeader = getAuthHeader(),
                        apiKey = SUPABASE_ANON_KEY
                    )
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal sinkron bookmark ke Supabase: ${e.message}")
            }
        }
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
                    username = profile?.username.nullIfBlank() ?: (res.user?.user_metadata?.get("username")?.toString() ?: email.substringBefore("@")),
                    avatarUrl = profile?.avatar_url,
                    isAdmin = profile?.isAdmin() ?: false,
                    isModerator = profile?.isModerator() ?: false,
                    isBeta = profile?.isBeta() ?: false,
                    customNameColor = profile?.custom_name_color,
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

                // Cek apakah ini akun BARU (baru aja kebuat oleh handle_new_user trigger),
                // bukan login biasa dari user Google yang udah lama punya akun.
                // signInWithIdToken dipakai buat login DAN sign-up pertama kali sekaligus,
                // jadi IP guard cuma boleh jalan sekali pas akun baru pertama kali dibuat.
                val isNewAccount = try {
                    val createdAtStr = profile?.created_at
                    if (createdAtStr != null) {
                        // Normalisasi string Postgres timestamptz ("...Z" atau "...+00:00",
                        // fractional seconds bisa 0-6 digit) jadi format yang bisa diparse
                        // SimpleDateFormat (aman dari API 1, gak butuh java.time/desugaring
                        // yang gak tersedia native di minSdk 24 tanpa coreLibraryDesugaring).
                        var normalized = createdAtStr.trim()
                        if (normalized.endsWith("Z")) {
                            normalized = normalized.dropLast(1) + "+00:00"
                        }
                        normalized = if (normalized.contains(".")) {
                            normalized.replace(Regex("""\.(\d+)""")) { m ->
                                "." + m.groupValues[1].padEnd(3, '0').take(3)
                            }
                        } else {
                            // Sisipin fractional seconds ".000" sebelum tanda offset (+/-)
                            val offsetIdx = normalized.indexOfLast { it == '+' || it == '-' }
                            if (offsetIdx > 10) {
                                normalized.substring(0, offsetIdx) + ".000" + normalized.substring(offsetIdx)
                            } else {
                                normalized
                            }
                        }
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
                        val createdMillis = sdf.parse(normalized)?.time
                        if (createdMillis != null) {
                            val ageMs = System.currentTimeMillis() - createdMillis
                            ageMs in 0..20000 // baru dibuat dalam 20 detik terakhir
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    Log.e("AnikuVM", "Gagal parse created_at buat cek akun baru Google", e)
                    false
                }

                if (isNewAccount) {
                    // Cek IP guard: kalau kedeteksi >2 akun dari IP yang sama,
                    // edge function bakal auto-ban IP + akun ini juga.
                    try {
                        val guardRes = NetworkClient.supabaseFunctionsApi.checkIpGuard(
                            request = IpGuardRequest(user_id = uId, email = emailFromRes),
                            apiKey = SUPABASE_ANON_KEY,
                            authHeader = "Bearer $SUPABASE_ANON_KEY"
                        )
                        if (guardRes.banned == true) {
                            _authError.value = "Pendaftaran ditolak: terdeteksi lebih dari 2 akun dari jaringan/IP yang sama. Akun ini otomatis di-ban."
                            _authLoading.value = false
                            return@launch
                        }
                    } catch (e: Exception) {
                        // Kalau edge function gagal dipanggil (mis. lagi maintenance),
                        // jangan blokir user biasa yang lagi daftar normal.
                        Log.e("AnikuVM", "IP guard check (Google) failed, lanjut tanpa cek", e)
                    }
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
                    isBeta = profile?.isBeta() ?: false,
                    customNameColor = profile?.custom_name_color,
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

                // Cek IP guard: kalau kedeteksi >2 akun dari IP yang sama,
                // edge function bakal auto-ban IP + akun ini juga.
                try {
                    val guardRes = NetworkClient.supabaseFunctionsApi.checkIpGuard(
                        request = IpGuardRequest(user_id = uId, email = email),
                        apiKey = SUPABASE_ANON_KEY,
                        authHeader = "Bearer $SUPABASE_ANON_KEY"
                    )
                    if (guardRes.banned == true) {
                        _authError.value = "Pendaftaran ditolak: terdeteksi lebih dari 2 akun dari jaringan/IP yang sama. Akun ini otomatis di-ban."
                        _authLoading.value = false
                        return@launch
                    }
                } catch (e: Exception) {
                    // Kalau edge function gagal dipanggil (mis. lagi maintenance),
                    // jangan blokir user biasa yang lagi daftar normal.
                    Log.e("AnikuVM", "IP guard check failed, lanjut tanpa cek", e)
                }

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
                    username = profile?.username.nullIfBlank() ?: username,
                    avatarUrl = profile?.avatar_url,
                    isAdmin = profile?.isAdmin() ?: false,
                    isModerator = profile?.isModerator() ?: false,
                    isBeta = profile?.isBeta() ?: false,
                    customNameColor = profile?.custom_name_color,
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

    // Dipanggil pas user klik link "Konfirmasi Email". Supabase udah kasih access_token
    // langsung di redirect link-nya (type=signup), jadi tinggal decode JWT buat ambil
    // userId/email terus bikin session — gak perlu login manual lagi.
    fun confirmEmailAndLogin(accessToken: String, refreshToken: String?, onSuccess: () -> Unit) {
        _authLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            try {
                val payload = accessToken.split(".").getOrNull(1)
                    ?: throw IllegalArgumentException("Token tidak valid")
                val decoded = String(
                    android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                )
                val json = org.json.JSONObject(decoded)
                val uId = json.optString("sub")
                val email = json.optString("email")
                if (uId.isBlank()) throw IllegalArgumentException("User ID tidak ditemukan di token")

                val profiles = NetworkClient.supabaseDbApi.getProfileByUserId(
                    idQuery = "eq.$uId",
                    authHeader = "Bearer $accessToken",
                    apiKey = SUPABASE_ANON_KEY
                )
                val profile = profiles.firstOrNull()

                val activeSession = UserSession(
                    token = accessToken,
                    refreshToken = refreshToken,
                    userId = uId,
                    email = email,
                    username = profile?.username.nullIfBlank() ?: email.substringBefore("@"),
                    avatarUrl = profile?.avatar_url,
                    isAdmin = profile?.isAdmin() ?: false,
                    isModerator = profile?.isModerator() ?: false,
                    isBeta = profile?.isBeta() ?: false,
                    customNameColor = profile?.custom_name_color,
                    isBanned = profile?.is_banned ?: false,
                    userNumber = profile?.user_number
                )

                settingsStore.saveSession(activeSession)
                _authLoading.value = false
                onSuccess()
            } catch (e: Exception) {
                _authLoading.value = false
                _authError.value = "Konfirmasi email gagal: ${e.message}"
                Log.e("AnikuVM", "confirmEmailAndLogin Exception", e)
            }
        }
    }

    fun updateProfileUsername(newUsername: String, onComplete: () -> Unit) {
        val trimmed = newUsername.trim()
        if (trimmed.isBlank()) {
            _authError.value = "Nama pengguna tidak boleh kosong"
            return
        }
        val sess = session.value
        val token = sess.token ?: return
        val uId = sess.userId ?: return
        viewModelScope.launch {
            try {
                val updateFields = mapOf("username" to trimmed)
                NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.$uId",
                    profile = updateFields,
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                val updatedSession = sess.copy(
                    username = trimmed,
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
                
                // 2. Siapkan multipart request, deteksi GIF dari signature file biar Cloudinary
                // tidak strip animasinya (sama kayak logic uploadBanner)
                val isGif = fileBytes.size > 3 &&
                    fileBytes[0] == 'G'.code.toByte() &&
                    fileBytes[1] == 'I'.code.toByte() &&
                    fileBytes[2] == 'F'.code.toByte()
                val mimeType = if (isGif) "image/gif" else "image/*"
                val fileName = if (isGif) "avatar.gif" else "avatar.jpg"
                val requestFile = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull(), 0, fileBytes.size)
                val body = MultipartBody.Part.createFormData("file", fileName, requestFile)
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

    // Cari anime di MyAnimeList (Jikan) buat autofill poster/sinopsis/genre/studio.
    // Dipanggil pas admin ngetik judul anime yang direquest user.
    fun searchJikanAnime(query: String) {
        if (query.isBlank()) {
            _jikanSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearchingJikan.value = true
            try {
                val res = NetworkClient.jikanApi.searchAnime(query)
                _jikanSearchResults.value = res.data ?: emptyList()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Jikan search failed", e)
                _jikanSearchResults.value = emptyList()
            } finally {
                _isSearchingJikan.value = false
            }
        }
    }

    fun clearJikanSearch() {
        _jikanSearchResults.value = emptyList()
    }

    // Upload video anime requestan ke Cloudinary (preset anime_request_video),
    // lalu simpan hasilnya + metadata dari Jikan (yang udah dipilih admin) ke Supabase.
    fun uploadRequestedAnimeVideo(
        videoUri: Uri,
        selectedAnime: JikanAnimeData,
        episode: String?,
        onProgress: (Boolean) -> Unit
    ) {
        val sess = session.value
        val token = sess.token ?: return
        _isUploadingRequestedAnime.value = true
        _requestedAnimeError.value = null
        onProgress(true)
        viewModelScope.launch {
            try {
                // 1. Baca bytes video dari URI
                val contentResolver = appContext.contentResolver
                val inputStream = contentResolver.openInputStream(videoUri)
                val byteBuffer = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var len: Int
                if (inputStream != null) {
                    while (inputStream.read(buffer).also { len = it } != -1) {
                        byteBuffer.write(buffer, 0, len)
                    }
                    inputStream.close()
                }
                val fileBytes = byteBuffer.toByteArray()

                // 2. Siapkan multipart request buat Cloudinary, dibungkus ProgressRequestBody
                //    biar progress upload-nya (0-100%) bisa dipantau real-time dari UI
                _uploadRequestedAnimeProgress.value = 0
                val plainRequestFile = fileBytes.toRequestBody("video/*".toMediaTypeOrNull(), 0, fileBytes.size)
                val requestFile = ProgressRequestBody(plainRequestFile) { written, total ->
                    if (total > 0) {
                        val percent = ((written * 100) / total).toInt()
                        _uploadRequestedAnimeProgress.value = percent
                    }
                }
                val body = MultipartBody.Part.createFormData("file", "requested_video.mp4", requestFile)
                val presetBody = "anime_request_video".toRequestBody("text/plain".toMediaTypeOrNull())

                // 3. Upload ke Cloudinary (preset unsigned khusus video requestan)
                val cloudinaryRes = NetworkClient.cloudinaryApi.uploadRequestedVideo(body, presetBody)
                val videoUrl = cloudinaryRes.secure_url

                // 4. Susun metadata dari hasil Jikan yang udah dipilih
                val genresStr = selectedAnime.genres?.mapNotNull { it.name }?.joinToString(",")
                val studioStr = selectedAnime.studios?.mapNotNull { it.name }?.joinToString(",")
                val posterUrl = selectedAnime.images?.jpg?.large_image_url
                    ?: selectedAnime.images?.jpg?.image_url

                val payload = mapOf(
                    "mal_id" to selectedAnime.mal_id,
                    "title" to selectedAnime.title,
                    "poster_url" to posterUrl,
                    "synopsis" to selectedAnime.synopsis,
                    "genres" to genresStr,
                    "studio" to studioStr,
                    "rating" to selectedAnime.score?.toString(),
                    "anime_status" to selectedAnime.status,
                    "episode" to episode,
                    "video_url" to videoUrl,
                    "status" to "pending"
                )

                // 5. Simpan ke tabel requested_anime di Supabase
                val inserted = NetworkClient.supabaseDbApi.insertRequestedAnime(
                    data = payload,
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                _requestedAnimeList.value = inserted + _requestedAnimeList.value

                _isUploadingRequestedAnime.value = false
                _uploadRequestedAnimeProgress.value = 0
                onProgress(false)
            } catch (e: Exception) {
                Log.e("AnikuVM", "Upload requested anime failed", e)
                _requestedAnimeError.value = e.message ?: "Upload gagal"
                _isUploadingRequestedAnime.value = false
                _uploadRequestedAnimeProgress.value = 0
                onProgress(false)
            }
        }
    }

    fun fetchRequestedAnimeList() {
        val sess = session.value
        val token = sess.token ?: return
        viewModelScope.launch {
            try {
                val res = NetworkClient.supabaseDbApi.getRequestedAnime(
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                _requestedAnimeList.value = res
            } catch (e: Exception) {
                Log.e("AnikuVM", "Fetch requested anime failed", e)
            }
        }
    }

    // Admin approve/reject anime request
    fun setRequestedAnimeStatus(id: String, status: String) {
        val sess = session.value
        val token = sess.token ?: return
        if (!sess.isAdmin) return
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.updateRequestedAnime(
                    idQuery = "eq.$id",
                    data = mapOf("status" to status),
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                _requestedAnimeList.value = _requestedAnimeList.value.map {
                    if (it.id == id) it.copy(status = status) else it
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Update requested anime status failed", e)
            }
        }
    }

    fun deleteRequestedAnime(id: String) {
        val sess = session.value
        val token = sess.token ?: return
        if (!sess.isAdmin) return
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deleteRequestedAnime(
                    idQuery = "eq.$id",
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                _requestedAnimeList.value = _requestedAnimeList.value.filter { it.id != id }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Delete requested anime failed", e)
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

    // --- Clan & Diamond ---
    private val _clanTagMap = MutableStateFlow<Map<String, Pair<String, String?>>>(emptyMap())
    val clanTagMap: StateFlow<Map<String, Pair<String, String?>>> = _clanTagMap.asStateFlow()

    fun loadClanTagMap() {
        viewModelScope.launch {
            try {
                val raw = NetworkClient.supabaseDbApi.getAllClanTags(getAuthHeader(), SUPABASE_ANON_KEY)
                val map = mutableMapOf<String, Pair<String, String?>>()
                raw.forEach { row ->
                    @Suppress("UNCHECKED_CAST")
                    val clanMap = row["clans"] as? Map<String, Any?>
                    val userId = row["user_id"] as? String
                    val tag = clanMap?.get("tag") as? String
                    val iconUrl = clanMap?.get("icon_url") as? String
                    if (userId != null && tag != null) map[userId] = tag to iconUrl
                }
                _clanTagMap.value = map
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal load clan tag map", e)
            }
        }
    }

    private val _topClans = MutableStateFlow<List<ClanDto>>(emptyList())
    val topClans: StateFlow<List<ClanDto>> = _topClans.asStateFlow()

    private val _myClanMembership = MutableStateFlow<ClanMemberDto?>(null)
    val myClanMembership: StateFlow<ClanMemberDto?> = _myClanMembership.asStateFlow()

    private val _myClanDetail = MutableStateFlow<ClanDto?>(null)
    val myClanDetail: StateFlow<ClanDto?> = _myClanDetail.asStateFlow()

    // Member clan sendiri (dipakai di MyClanCard) - dipisah dari selectedClanMembers biar gak numpuk pas liat clan orang
    private val _myClanMembers = MutableStateFlow<List<ClanMemberDto>>(emptyList())
    val myClanMembers: StateFlow<List<ClanMemberDto>> = _myClanMembers.asStateFlow()

    // Member clan yang lagi diliat (clan orang lain / dialog preview) - terpisah dari myClanMembers
    private val _selectedClanMembers = MutableStateFlow<List<ClanMemberDto>>(emptyList())
    val selectedClanMembers: StateFlow<List<ClanMemberDto>> = _selectedClanMembers.asStateFlow()

    private val _clanActionError = MutableStateFlow<String?>(null)
    val clanActionError: StateFlow<String?> = _clanActionError.asStateFlow()

    private val _isClanLoading = MutableStateFlow(false)
    val isClanLoading: StateFlow<Boolean> = _isClanLoading.asStateFlow()

    private val _diamondBalance = MutableStateFlow(0)
    val diamondBalance: StateFlow<Int> = _diamondBalance.asStateFlow()

    // Refresh saldo Diamond dari profile sendiri (dipanggil setelah create/join/contribute clan)
    fun refreshProfile() {
        val uid = session.value.userId ?: return
        viewModelScope.launch {
            try {
                val result = withValidToken { token ->
                    NetworkClient.supabaseDbApi.getProfileByUserId(
                        idQuery = "eq.$uid",
                        authHeader = "Bearer $token",
                        apiKey = SUPABASE_ANON_KEY
                    )
                }
                _diamondBalance.value = result.firstOrNull()?.diamond_balance ?: 0
            } catch (e: Exception) {
                Log.e("AnikuVM", "refreshProfile (diamond) error: ${e.message}")
            }
        }
    }

    fun loadClans() {
        viewModelScope.launch {
            try {
                _topClans.value = NetworkClient.supabaseDbApi.getClans(getAuthHeader(), SUPABASE_ANON_KEY)
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal load clans", e)
            }
        }
    }

    fun loadMyClanMembership() {
        val userId = session.value.userId ?: return
        viewModelScope.launch {
            try {
                val result = NetworkClient.supabaseDbApi.getMyClanMembership("eq.$userId", getAuthHeader(), SUPABASE_ANON_KEY)
                val membership = result.firstOrNull()
                _myClanMembership.value = membership
                if (membership != null) {
                    val clanResult = NetworkClient.supabaseDbApi.getClanById("eq.${membership.clan_id}", getAuthHeader(), SUPABASE_ANON_KEY)
                    _myClanDetail.value = clanResult.firstOrNull()
                } else {
                    _myClanDetail.value = null
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal load clan membership", e)
            }
        }
    }

    // Fetch mentah + mapping, dipakai bareng oleh loadClanMembers (clan orang) dan loadMyClanMembers (clan sendiri)
    private suspend fun fetchClanMembers(clanId: String): List<ClanMemberDto> {
        val raw = NetworkClient.supabaseDbApi.getClanMembers("eq.$clanId", getAuthHeader(), SUPABASE_ANON_KEY)
        return raw.map { row ->
            @Suppress("UNCHECKED_CAST")
            val profileMap = row["profiles"] as? Map<String, Any?>
            ClanMemberDto(
                id = row["id"] as? String ?: "",
                clan_id = row["clan_id"] as? String ?: "",
                user_id = row["user_id"] as? String ?: "",
                role = row["role"] as? String,
                contributed_xp = (row["contributed_xp"] as? Double)?.toInt(),
                username = profileMap?.get("username") as? String,
                avatar_url = profileMap?.get("avatar_url") as? String
            )
        }.sortedByDescending { it.contributed_xp ?: 0 }
    }

    // Buat liat member clan orang lain / dialog preview clan dari leaderboard & daftar clan
    fun loadClanMembers(clanId: String) {
        viewModelScope.launch {
            try {
                _selectedClanMembers.value = fetchClanMembers(clanId)
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal load clan members", e)
            }
        }
    }

    // Khusus buat member clan sendiri (MyClanCard) - state-nya kepisah dari selectedClanMembers
    fun loadMyClanMembers(clanId: String) {
        viewModelScope.launch {
            try {
                _myClanMembers.value = fetchClanMembers(clanId)
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal load my clan members", e)
            }
        }
    }

    fun createClan(name: String, tag: String) {
        _clanActionError.value = null
        _isClanLoading.value = true
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.createClan(
                    mapOf("p_name" to name, "p_tag" to tag),
                    getAuthHeader(), SUPABASE_ANON_KEY
                )
                if (response.isSuccessful) {
                    loadMyClanMembership()
                    loadClans()
                    refreshProfile()
                } else {
                    _clanActionError.value = response.errorBody()?.string() ?: "Gagal membuat clan"
                }
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal membuat clan"
            } finally {
                _isClanLoading.value = false
            }
        }
    }

    fun joinClan(clanId: String) {
        _clanActionError.value = null
        _isClanLoading.value = true
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.joinClan(
                    mapOf("p_clan_id" to clanId),
                    getAuthHeader(), SUPABASE_ANON_KEY
                )
                if (response.isSuccessful) {
                    loadMyClanMembership()
                    loadClans()
                } else {
                    _clanActionError.value = response.errorBody()?.string() ?: "Gagal join clan"
                }
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal join clan"
            } finally {
                _isClanLoading.value = false
            }
        }
    }

    fun contributeToClan(amount: Int) {
        _clanActionError.value = null
        _isClanLoading.value = true
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.contributeToClan(
                    mapOf("p_amount" to amount),
                    getAuthHeader(), SUPABASE_ANON_KEY
                )
                if (response.isSuccessful) {
                    loadMyClanMembership()
                    loadClans()
                    refreshProfile()
                } else {
                    _clanActionError.value = response.errorBody()?.string() ?: "Gagal kontribusi"
                }
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal kontribusi"
            } finally {
                _isClanLoading.value = false
            }
        }
    }

    private val _isUploadingClanIcon = MutableStateFlow(false)
    val isUploadingClanIcon: StateFlow<Boolean> = _isUploadingClanIcon.asStateFlow()

    private val _pendingJoinRequests = MutableStateFlow<List<ClanJoinRequestDto>>(emptyList())
    val pendingJoinRequests: StateFlow<List<ClanJoinRequestDto>> = _pendingJoinRequests.asStateFlow()

    fun loadPendingJoinRequests(clanId: String) {
        viewModelScope.launch {
            try {
                val raw = NetworkClient.supabaseDbApi.getClanJoinRequests("eq.$clanId", "eq.pending", getAuthHeader(), SUPABASE_ANON_KEY)
                _pendingJoinRequests.value = raw.map { row ->
                    @Suppress("UNCHECKED_CAST")
                    val profileMap = row["profiles"] as? Map<String, Any?>
                    ClanJoinRequestDto(
                        id = row["id"] as? String ?: "",
                        clan_id = row["clan_id"] as? String ?: "",
                        user_id = row["user_id"] as? String ?: "",
                        status = row["status"] as? String,
                        username = profileMap?.get("username") as? String,
                        avatar_url = profileMap?.get("avatar_url") as? String
                    )
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal load join requests", e)
            }
        }
    }

    fun requestJoinClan(clanId: String) {
        _clanActionError.value = null
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.requestJoinClan(mapOf("p_clan_id" to clanId), getAuthHeader(), SUPABASE_ANON_KEY)
                if (!response.isSuccessful) _clanActionError.value = response.errorBody()?.string() ?: "Gagal request gabung clan"
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal request gabung clan"
            }
        }
    }

    fun approveJoinRequest(requestId: String, clanId: String) {
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.approveJoinRequest(mapOf("p_request_id" to requestId), getAuthHeader(), SUPABASE_ANON_KEY)
                if (response.isSuccessful) {
                    loadPendingJoinRequests(clanId)
                    loadMyClanMembers(clanId)
                    loadClans()
                } else {
                    _clanActionError.value = response.errorBody()?.string() ?: "Gagal approve"
                }
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal approve"
            }
        }
    }

    fun rejectJoinRequest(requestId: String, clanId: String) {
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.rejectJoinRequest(mapOf("p_request_id" to requestId), getAuthHeader(), SUPABASE_ANON_KEY)
                loadPendingJoinRequests(clanId)
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal reject join request", e)
            }
        }
    }

    fun leaveClan(onDone: () -> Unit = {}) {
        _clanActionError.value = null
        _isClanLoading.value = true
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.leaveClan(getAuthHeader(), SUPABASE_ANON_KEY)
                if (response.isSuccessful) {
                    _myClanMembership.value = null
                    _myClanDetail.value = null
                    _myClanMembers.value = emptyList()
                    loadClans()
                    refreshProfile()
                    onDone()
                } else {
                    _clanActionError.value = response.errorBody()?.string() ?: "Gagal keluar clan"
                }
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal keluar clan"
            } finally {
                _isClanLoading.value = false
            }
        }
    }

    fun kickMember(clanId: String, userId: String) {
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.kickMember(mapOf("p_clan_id" to clanId, "p_user_id" to userId), getAuthHeader(), SUPABASE_ANON_KEY)
                if (response.isSuccessful) loadMyClanMembers(clanId) else _clanActionError.value = response.errorBody()?.string() ?: "Gagal kick member"
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal kick member"
            }
        }
    }

    fun deleteClan(clanId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.deleteClan(mapOf("p_clan_id" to clanId), getAuthHeader(), SUPABASE_ANON_KEY)
                if (response.isSuccessful) {
                    _myClanMembers.value = emptyList()
                    loadMyClanMembership()
                    loadClans()
                    onDone()
                } else {
                    _clanActionError.value = response.errorBody()?.string() ?: "Gagal hapus clan"
                }
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal hapus clan"
            }
        }
    }

    fun renameClan(clanId: String, newName: String) {
        _clanActionError.value = null
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.renameClan(mapOf("p_clan_id" to clanId, "p_new_name" to newName), getAuthHeader(), SUPABASE_ANON_KEY)
                if (response.isSuccessful) {
                    loadMyClanMembership()
                    loadClans()
                    refreshProfile()
                } else {
                    _clanActionError.value = response.errorBody()?.string() ?: "Gagal ganti nama clan"
                }
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal ganti nama clan"
            }
        }
    }

    fun setClanPrivacy(clanId: String, isPrivate: Boolean) {
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.setClanPrivacy(mapOf("p_clan_id" to clanId, "p_is_private" to isPrivate), getAuthHeader(), SUPABASE_ANON_KEY)
                if (response.isSuccessful) loadMyClanMembership() else _clanActionError.value = response.errorBody()?.string() ?: "Gagal ubah privasi"
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal ubah privasi"
            }
        }
    }

    fun uploadClanIcon(clanId: String, uri: android.net.Uri) {
        _isUploadingClanIcon.value = true
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
                val body = MultipartBody.Part.createFormData("file", "clan_icon.jpg", requestFile)
                val presetBody = "aniku_avatar".toRequestBody("text/plain".toMediaTypeOrNull())

                val cloudinaryRes = NetworkClient.cloudinaryApi.uploadAvatar(body, presetBody)
                val secureUrl = cloudinaryRes.secure_url

                val response = NetworkClient.supabaseDbApi.updateClanIcon(
                    mapOf("p_clan_id" to clanId, "p_icon_url" to secureUrl),
                    getAuthHeader(), SUPABASE_ANON_KEY
                )
                if (response.isSuccessful) {
                    loadMyClanMembership()
                    loadClans()
                } else {
                    _clanActionError.value = response.errorBody()?.string() ?: "Gagal update icon clan"
                }
            } catch (e: Exception) {
                _clanActionError.value = e.message ?: "Gagal upload icon clan"
            } finally {
                _isUploadingClanIcon.value = false
            }
        }
    }

    // Admin only: kredit DM manual (dipanggil dari Admin Panel setelah verifikasi bukti transfer)
    fun adminAddDiamond(userId: String, amount: Int, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.adminAddDiamond(
                    mapOf("p_user_id" to userId, "p_amount" to amount),
                    getAuthHeader(), SUPABASE_ANON_KEY
                )
                onDone(response.isSuccessful)
                if (response.isSuccessful) loadAdminDetails()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal tambah diamond", e)
                onDone(false)
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

                // 4b. Load blacklisted genres list
                _adminBlacklistGenres.value = NetworkClient.supabaseDbApi.getBlacklistedGenres(authHeader, SUPABASE_ANON_KEY)
                
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

    // Ganti warna nama sendiri di chat - cuma efektif kalau role beta/moderator/admin
    // (dipaksa ulang di server lewat trigger enforce_custom_name_color, jadi walau
    // dipanggil user biasa lewat cara lain, server bakal nge-null-in lagi otomatis).
    fun updateMyNameColor(hexColor: String?, onComplete: (Boolean) -> Unit = {}) {
        val sess = session.value
        if (!sess.isBeta && !sess.isModerator && !sess.isAdmin) {
            onComplete(false)
            return
        }
        val token = sess.token ?: return
        val uId = sess.userId ?: return
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.updateProfile(
                    idQuery = "eq.$uId",
                    profile = mapOf("custom_name_color" to hexColor),
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
                if (response.isSuccessful || response.code() == 204) {
                    val updatedSession = sess.copy(customNameColor = hexColor)
                    settingsStore.saveSession(updatedSession)
                    onComplete(true)
                } else {
                    Log.e("AnikuVM", "Failed updateMyNameColor: HTTP ${response.code()}")
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed to update name color", e)
                onComplete(false)
            }
        }
    }

    // Kasih Diamond ke user lain (username tujuan) - fitur eksklusif role beta/moderator/
    // admin. Validasi asli (role, saldo, limit harian 200 DM) ada di server (function
    // give_diamond), jadi walau pengecekan client-side ini di-bypass, server tetap nolak.
    fun giveDiamond(receiverUsername: String, amount: Int, onResult: (Boolean, String?) -> Unit) {
        val sess = session.value
        if (!sess.isBeta && !sess.isModerator && !sess.isAdmin) {
            onResult(false, "Fitur ini cuma buat role Beta/Moderator/Admin")
            return
        }
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.giveDiamond(
                    body = mapOf(
                        "p_receiver_username" to receiverUsername,
                        "p_amount" to amount
                    ),
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                if (response.isSuccessful || response.code() == 204) {
                    onResult(true, null)
                } else {
                    // Postgres RAISE EXCEPTION dari function nyampe ke sini lewat error body
                    // JSON (field "message") - ini yang dipakai buat kasih tau alasan gagal
                    // yang jelas ke user (mis. "melebihi batas harian...", bukan cuma "HTTP 400").
                    val errorMsg = try {
                        val errorBody = response.errorBody()?.string()
                        val json = errorBody?.let { org.json.JSONObject(it) }
                        json?.optString("message")?.takeIf { it.isNotBlank() }
                    } catch (e: Exception) { null }
                    onResult(false, errorMsg ?: "Gagal memberi Diamond (HTTP ${response.code()})")
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "giveDiamond error", e)
                onResult(false, e.message ?: "Terjadi kesalahan")
            }
        }
    }

    // Roll gacha karakter - potong DM sesuai cost, dapet 1 karakter random (rarity
    // ditentuin server). Semua validasi (saldo cukup, dll) ada di function gacha_roll,
    // jadi response error di sini ngambil pesan yang sama kayak giveDiamond di atas.
    fun rollGacha(cost: Int = 50, onResult: (GachaRollResult?, String?) -> Unit) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val result = NetworkClient.supabaseDbApi.rollGacha(
                    body = GachaRollRequest(p_cost = cost),
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                onResult(result, null)
            } catch (e: retrofit2.HttpException) {
                val errorMsg = try {
                    val errorBody = e.response()?.errorBody()?.string()
                    val json = errorBody?.let { org.json.JSONObject(it) }
                    json?.optString("message")?.takeIf { it.isNotBlank() }
                } catch (parseErr: Exception) { null }
                onResult(null, errorMsg ?: "Gagal gacha (HTTP ${e.code()})")
            } catch (e: Exception) {
                Log.e("AnikuVM", "rollGacha error", e)
                onResult(null, e.message ?: "Terjadi kesalahan")
            }
        }
    }

    // Roll gacha berkali-kali sekaligus (mis. paket "x6"). Manggil RPC gacha_roll
    // yang sama beberapa kali berturut-turut (BUKAN function SQL baru) - masing-masing
    // panggilan tetap 1 transaksi atom sendiri di server, jadi tetap aman dari race
    // condition. Kalau di tengah jalan saldo abis, berhenti & kasih hasil yang udah
    // didapet sejauh itu (partial), bukan gagal total.
    fun rollGachaMulti(times: Int = 6, cost: Int = 50, onResult: (List<GachaRollResult>, String?) -> Unit) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            val results = mutableListOf<GachaRollResult>()
            var errorMsg: String? = null
            for (i in 1..times) {
                try {
                    val result = NetworkClient.supabaseDbApi.rollGacha(
                        body = GachaRollRequest(p_cost = cost),
                        authHeader = authHeader,
                        apiKey = SUPABASE_ANON_KEY
                    )
                    results.add(result)
                } catch (e: retrofit2.HttpException) {
                    errorMsg = try {
                        val errorBody = e.response()?.errorBody()?.string()
                        val json = errorBody?.let { org.json.JSONObject(it) }
                        json?.optString("message")?.takeIf { it.isNotBlank() }
                    } catch (parseErr: Exception) { null }
                    errorMsg = errorMsg ?: "Gagal gacha di tarikan ke-$i (HTTP ${e.code()})"
                    break
                } catch (e: Exception) {
                    Log.e("AnikuVM", "rollGachaMulti error di tarikan ke-$i", e)
                    errorMsg = e.message ?: "Terjadi kesalahan di tarikan ke-$i"
                    break
                }
            }
            onResult(results, errorMsg)
        }
    }

    // ─────────────── Quiz "Tebak Anime dari Poster" ───────────────

    // Ngecek eligibility (wajib punya clan) + motong jatah harian/Diamond di server
    // lewat function play_quiz_round. HARUS dipanggil sebelum nampilin soal ke user -
    // kalau gagal (mis. belum join clan / DM gak cukup), jangan tampilkan soalnya.
    fun playQuizRound(cost: Int = 5000, onResult: (PlayQuizRoundResult?, String?) -> Unit) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val result = NetworkClient.supabaseDbApi.playQuizRound(
                    body = PlayQuizRoundRequest(p_cost = cost),
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                onResult(result, null)
            } catch (e: retrofit2.HttpException) {
                val errorMsg = try {
                    val errorBody = e.response()?.errorBody()?.string()
                    val json = errorBody?.let { org.json.JSONObject(it) }
                    json?.optString("message")?.takeIf { it.isNotBlank() }
                } catch (parseErr: Exception) { null }
                onResult(null, errorMsg ?: "Gagal mulai quiz (HTTP ${e.code()})")
            } catch (e: Exception) {
                Log.e("AnikuVM", "playQuizRound error", e)
                onResult(null, e.message ?: "Terjadi kesalahan")
            }
        }
    }

    // Dipanggil setelah user milih jawaban. Kalau bener, server ngasih XP ke diri
    // sendiri (penuh) + semua member clan lain (setengah) lewat function
    // submit_quiz_answer - semua logic hitung XP jalan di server, gak bisa
    // dicurangin dari client.
    fun submitQuizAnswer(correct: Boolean, fast: Boolean, onResult: (SubmitQuizAnswerResult?, String?) -> Unit) {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val result = NetworkClient.supabaseDbApi.submitQuizAnswer(
                    body = SubmitQuizAnswerRequest(p_correct = correct, p_fast = fast),
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                // Refresh XP/level sendiri biar badge di chat & profile langsung update
                if (correct) refreshProfile()
                onResult(result, null)
            } catch (e: retrofit2.HttpException) {
                val errorMsg = try {
                    val errorBody = e.response()?.errorBody()?.string()
                    val json = errorBody?.let { org.json.JSONObject(it) }
                    json?.optString("message")?.takeIf { it.isNotBlank() }
                } catch (parseErr: Exception) { null }
                onResult(null, errorMsg ?: "Gagal submit jawaban (HTTP ${e.code()})")
            } catch (e: Exception) {
                Log.e("AnikuVM", "submitQuizAnswer error", e)
                onResult(null, e.message ?: "Terjadi kesalahan")
            }
        }
    }

    // Ambil soal buat quiz dari Animasu (bukan Jikan lagi) - sumbernya API sendiri
    // (sankavollerei.com/anime, animeApi yang udah dipake fitur lain di app), jadi
    // lebih stabil dibanding Jikan yang sering 504/timeout. Cuma butuh field
    // "poster" dan "title" dari AnimeRaw, sisanya diabaikan.
    // NOTE: "animasu/animelist" (getAnimeList) ternyata gak pernah kepake di
    // fitur lain di app ini, jadi belum pernah kebukti jalan - itu yang bikin
    // quiz selalu gagal ambil soal. Endpoint yang UDAH kebukti jalan (dipake
    // infinite-scroll di fitur Explore) adalah getOngoing/getCompleted/getMovies/
    // getLatest, jadi quiz sekarang ambil dari situ aja - gantian tiap attempt
    // biar poolnya bervariasi dan sekalian ada fallback kalau salah satu error.
    fun fetchQuizQuestion(
        decoyCount: Int = 3,
        onResult: (correctAnswer: AnimeRaw?, decoys: List<String>, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            var pool: List<AnimeRaw> = emptyList()
            var attempts = 0
            var lastErrorWasTransient = false
            val endpoints: List<suspend (Int) -> AnimesListResponse> = listOf(
                { p -> animeApi.getOngoing(page = p) },
                { p -> animeApi.getCompleted(page = p) },
                { p -> animeApi.getMovies(page = p) },
                { p -> animeApi.getLatest(page = p) }
            )
            while (pool.size < decoyCount + 1 && attempts < 5) {
                val fetchFn = endpoints[attempts % endpoints.size]
                attempts++
                try {
                    val page = Random.nextInt(1, 10)
                    val res = fetchFn(page)
                    val validItems = res.animes?.filter {
                        it.poster.isNotBlank() && it.title.isNotBlank()
                    } ?: emptyList()
                    if (validItems.size >= decoyCount + 1) {
                        pool = validItems
                    }
                    lastErrorWasTransient = false
                } catch (e: Exception) {
                    Log.e("AnikuVM", "fetchQuizQuestion attempt $attempts gagal", e)
                    lastErrorWasTransient = true
                }
                if (pool.size < decoyCount + 1) delay(500)
            }

            if (pool.size < decoyCount + 1) {
                val msg = if (lastErrorWasTransient) {
                    "Server Animasu lagi gangguan/timeout, coba lagi sebentar"
                } else {
                    "Gagal ambil soal, coba lagi"
                }
                onResult(null, emptyList(), msg)
                return@launch
            }

            val shuffled = pool.shuffled(Random(System.nanoTime()))
            val correct = shuffled[0]
            val decoys = shuffled.drop(1)
                .map { it.title }
                .distinct()
                .filter { it != correct.title }
                .take(decoyCount)

            onResult(correct, decoys, if (decoys.size < decoyCount) "Sebagian pilihan jawaban gagal dimuat" else null)
        }
    }


    private val _gachaCollection = MutableStateFlow<List<UserCharacterEntry>>(emptyList())
    val gachaCollection: StateFlow<List<UserCharacterEntry>> = _gachaCollection.asStateFlow()

    // Ambil koleksi karakter user sendiri, di-join langsung sama tabel characters
    // (nama, gambar, rarity, anime asal) lewat fitur embed PostgREST - 1 request
    // doang, gak perlu loop manual gabungin data di client.
    fun loadGachaCollection() {
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val result = NetworkClient.supabaseDbApi.getUserCharacters(
                    select = "count,obtained_at,last_obtained_at,characters(mal_id,name,image_url,anime_title,rarity)",
                    order = "last_obtained_at.desc",
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                _gachaCollection.value = result
            } catch (e: Exception) {
                Log.e("AnikuVM", "loadGachaCollection error", e)
            }
        }
    }

    // ── Pajangan kartu karakter di profil (maks 6) ──

    // id karakter yang lagi dipajang punya USER SENDIRI (buat isi awal editor di GachaScreen)
    private val _myShowcaseIds = MutableStateFlow<List<Int>>(emptyList())
    val myShowcaseIds: StateFlow<List<Int>> = _myShowcaseIds.asStateFlow()

    // detail karakter yang dipajang -- dipakai buat nampilin di ProfileScreen/UserProfileScreen
    // (bisa punya sendiri atau punya orang lain, makanya terima parameter ids)
    private val _showcaseCharacters = MutableStateFlow<List<CharacterInfoDto>>(emptyList())
    val showcaseCharacters: StateFlow<List<CharacterInfoDto>> = _showcaseCharacters.asStateFlow()

    fun setMyShowcaseIds(ids: List<Int>) {
        _myShowcaseIds.value = ids
    }

    // Ambil pajangan yang lagi aktif buat user sendiri (dipanggil pas buka GachaScreen
    // biar toggle "dipajang/enggak" di tiap kartu koleksi sesuai kondisi terakhir).
    fun loadMyShowcaseIds() {
        val uid = session.value.userId ?: return
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val result = NetworkClient.supabaseDbApi.getProfileByUserId(
                    idQuery = "eq.$uid",
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                _myShowcaseIds.value = result.firstOrNull()?.showcase_character_ids ?: emptyList()
            } catch (e: Exception) {
                Log.e("AnikuVM", "loadMyShowcaseIds error", e)
            }
        }
    }

    // Ambil detail (nama/gambar/rarity) buat sekumpulan id karakter yang dipajang,
    // urutannya disesuaikan sama urutan "ids" biar konsisten sama pilihan user.
    fun loadShowcaseCharacters(ids: List<Int>) {
        if (ids.isEmpty()) {
            _showcaseCharacters.value = emptyList()
            return
        }
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val filter = "in.(${ids.joinToString(",")})"
                val result = NetworkClient.supabaseDbApi.getCharactersByIds(
                    malIdFilter = filter,
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                val byId = result.associateBy { it.mal_id }
                _showcaseCharacters.value = ids.mapNotNull { byId[it] }
            } catch (e: Exception) {
                Log.e("AnikuVM", "loadShowcaseCharacters error", e)
            }
        }
    }

    // Simpan pilihan pajangan (maks 6, validasi kepemilikan dicek ulang di server
    // lewat RPC set_profile_showcase -- ini bukan sekadar UPDATE tabel profiles biasa).
    fun saveShowcase(ids: List<Int>, onResult: (Boolean, String?) -> Unit) {
        if (ids.size > 6) {
            onResult(false, "Maksimal 6 karakter yang bisa dipajang")
            return
        }
        val authHeader = getAuthHeader()
        viewModelScope.launch {
            try {
                val response = NetworkClient.supabaseDbApi.setProfileShowcase(
                    body = mapOf("p_character_ids" to ids),
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
                if (response.isSuccessful) {
                    _myShowcaseIds.value = ids
                    onResult(true, null)
                } else {
                    onResult(false, response.errorBody()?.string() ?: "Gagal menyimpan pajangan")
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "saveShowcase error", e)
                onResult(false, e.message)
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

    // Blacklist genre — cukup tap chip buat nyembunyiin/nampilin lagi genre dari Eksplor, gak perlu form manual
    fun toggleGenreBlacklist(genreSlug: String, genreName: String) {
        val authHeader = getAuthHeader()
        val existing = _adminBlacklistGenres.value.find { it.genre_slug == genreSlug }
        viewModelScope.launch {
            try {
                if (existing != null) {
                    NetworkClient.supabaseDbApi.deleteBlacklistedGenre("eq.${existing.id}", authHeader, SUPABASE_ANON_KEY)
                } else {
                    NetworkClient.supabaseDbApi.insertBlacklistedGenre(
                        mapOf("genre_slug" to genreSlug, "genre_name" to genreName),
                        authHeader,
                        SUPABASE_ANON_KEY
                    )
                }
                _adminBlacklistGenres.value = NetworkClient.supabaseDbApi.getBlacklistedGenres(authHeader, SUPABASE_ANON_KEY)
                loadBlacklistGenreSlugs()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Failed toggling genre blacklist", e)
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

    // ── Typing indicator ("sedang mengetik...") ─────────────────
    private val _typingUsers = MutableStateFlow<List<TypingStatus>>(emptyList())
    val typingUsers: StateFlow<List<TypingStatus>> = _typingUsers.asStateFlow()

    private var typingPollingJob: kotlinx.coroutines.Job? = null
    private var lastTypingSentAt = 0L

    // Dipanggil tiap kali teks input chat berubah. Di-throttle 2 detik
    // biar ga spam request ke Supabase pas user ngetik cepat.
    fun notifyTyping() {
        val userId = session.value.userId ?: return
        val token = session.value.token ?: return
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < 2000L) return
        lastTypingSentAt = now
        val username = session.value.username.nullIfBlank()
            ?: session.value.email?.substringBefore("@") ?: "Anonymous"
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.upsertTyping(
                    data = mapOf(
                        "user_id" to userId,
                        "username" to username,
                        "updated_at" to java.time.Instant.now().toString()
                    ),
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (_: Exception) {}
        }
    }

    // Hapus status typing sendiri, dipanggil pas kirim pesan atau keluar dari chat
    fun clearTyping() {
        val userId = session.value.userId ?: return
        val token = session.value.token ?: return
        lastTypingSentAt = 0L
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.removeTyping(
                    userId = "eq.$userId",
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (_: Exception) {}
        }
    }

    fun startTypingPolling() {
        typingPollingJob?.cancel()
        typingPollingJob = viewModelScope.launch {
            while (true) {
                try {
                    // Cuma ambil status typing yang di-update 5 detik terakhir,
                    // jadi otomatis "ilang" begitu user berhenti ngetik tanpa perlu delete manual.
                    val cutoff = java.time.Instant.now().minusSeconds(5).toString()
                    val result = NetworkClient.supabaseDbApi.getTypingUsers(
                        updatedAtFilter = "gte.$cutoff",
                        authHeader = "Bearer $SUPABASE_ANON_KEY",
                        apiKey = SUPABASE_ANON_KEY
                    )
                    _typingUsers.value = result.filter { it.user_id != session.value.userId }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(2000L)
            }
        }
    }

    fun stopTypingPolling() {
        typingPollingJob?.cancel()
        typingPollingJob = null
        _typingUsers.value = emptyList()
    }

    // ── Read receipt ("dilihat oleh" + avatar) ───────────────────
    private val _chatReads = MutableStateFlow<List<ChatReadStatus>>(emptyList())
    val chatReads: StateFlow<List<ChatReadStatus>> = _chatReads.asStateFlow()

    private var chatReadsPollingJob: kotlinx.coroutines.Job? = null
    private var lastMarkedReadMessageId: String? = null

    // Dipanggil pas chat pertama dibuka & tiap kali ada pesan baru ke-load,
    // upsert baris chat_reads milik user ini nunjuk ke pesan terakhir yang keliatan.
    fun markChatReadReceipt(lastMessageId: String) {
        if (lastMessageId == lastMarkedReadMessageId) return
        val userId = session.value.userId ?: return
        val token = session.value.token ?: return
        lastMarkedReadMessageId = lastMessageId
        val username = session.value.username.nullIfBlank()
            ?: session.value.email?.substringBefore("@") ?: "Anonymous"
        val avatar = session.value.avatarUrl
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.upsertChatRead(
                    data = mapOf(
                        "user_id" to userId,
                        "username" to username,
                        "avatar_url" to avatar,
                        "last_read_message_id" to lastMessageId,
                        "updated_at" to java.time.Instant.now().toString()
                    ),
                    authHeader = "Bearer $token",
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (_: Exception) {}
        }
    }

    fun startChatReadsPolling() {
        chatReadsPollingJob?.cancel()
        chatReadsPollingJob = viewModelScope.launch {
            while (true) {
                try {
                    _chatReads.value = NetworkClient.supabaseDbApi.getChatReads(
                        authHeader = "Bearer $SUPABASE_ANON_KEY",
                        apiKey = SUPABASE_ANON_KEY
                    )
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(4000L)
            }
        }
    }

    fun stopChatReadsPolling() {
        chatReadsPollingJob?.cancel()
        chatReadsPollingJob = null
        lastMarkedReadMessageId = null
    }

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

    // ── Komentar Episode (permanen, non-realtime) ───────────────
    private val _episodeComments = MutableStateFlow<List<EpisodeComment>>(emptyList())
    val episodeComments: StateFlow<List<EpisodeComment>> = _episodeComments.asStateFlow()

    private val _isEpisodeCommentsLoading = MutableStateFlow(false)
    val isEpisodeCommentsLoading: StateFlow<Boolean> = _isEpisodeCommentsLoading.asStateFlow()

    private val _isPostingEpisodeComment = MutableStateFlow(false)
    val isPostingEpisodeComment: StateFlow<Boolean> = _isPostingEpisodeComment.asStateFlow()

    private val _episodeCommentError = MutableStateFlow<String?>(null)
    val episodeCommentError: StateFlow<String?> = _episodeCommentError.asStateFlow()

    fun clearEpisodeCommentError() {
        _episodeCommentError.value = null
    }

    fun loadEpisodeComments(episodeSlug: String) {
        viewModelScope.launch {
            _isEpisodeCommentsLoading.value = true
            try {
                val comments = NetworkClient.supabaseDbApi.getEpisodeComments(
                    episodeSlug = "eq.$episodeSlug",
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
                // Fetch semua profiles sekali buat manual-join user_number & season_level
                val profilesMap = try {
                    NetworkClient.supabaseDbApi.getProfiles(
                        authHeader = getAuthHeader(),
                        apiKey = SUPABASE_ANON_KEY
                    ).associateBy { it.id }
                } catch (e: Exception) {
                    Log.e("AnikuVM", "Gagal fetch profiles buat join komentar episode", e)
                    emptyMap()
                }
                val joined = comments.map { c ->
                    c.copy(
                        user_number = profilesMap[c.user_id]?.user_number,
                        season_level = profilesMap[c.user_id]?.season_level
                    )
                }
                // API balikin terbaru dulu (desc), reverse jadi kronologis lama -> baru
                _episodeComments.value = joined.reversed()
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal load komentar episode", e)
            } finally {
                _isEpisodeCommentsLoading.value = false
            }
        }
    }

    fun postEpisodeComment(
        episodeSlug: String,
        message: String,
        animeSlug: String? = null,
        animeTitle: String? = null,
        animePoster: String? = null,
        parentCommentId: String? = null,
        replyToUsername: String? = null
    ) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) return
        val trimmed = message.trim()
        if (trimmed.isEmpty() || trimmed.length > 500) return
        // Label sumber data saat komentar dikirim, mis. "Dayynime-v1" -> "v1" (tanpa prefix "Dayynime")
        val sourceLabel = dataSource.value.substringAfterLast("-")
        viewModelScope.launch {
            _isPostingEpisodeComment.value = true
            try {
                NetworkClient.supabaseDbApi.insertEpisodeComment(
                    data = EpisodeCommentRequest(
                        episode_slug = episodeSlug,
                        user_id = currentSession.userId ?: "",
                        username = currentSession.username.nullIfBlank() ?: currentSession.email?.substringBefore("@") ?: "User",
                        avatar_url = currentSession.avatarUrl,
                        role = when { currentSession.isAdmin -> "admin"; currentSession.isModerator -> "moderator"; currentSession.isBeta -> "beta"; else -> "user" },
                        is_admin = currentSession.isAdmin,
                        message = trimmed,
                        source = sourceLabel,
                        anime_slug = animeSlug,
                        anime_title = animeTitle,
                        anime_poster = animePoster,
                        parent_comment_id = parentCommentId,
                        reply_to_username = replyToUsername
                    ),
                    authHeader = "Bearer ${currentSession.token}",
                    apiKey = SUPABASE_ANON_KEY
                )
                loadEpisodeComments(episodeSlug)
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal kirim komentar episode", e)
                _episodeCommentError.value = extractSupabaseErrorMessage(e)
            } finally {
                _isPostingEpisodeComment.value = false
            }
        }
    }

    // Ambil pesan error dari response Supabase (mis. dari "raise exception" di trigger Postgres),
    // fallback ke pesan generik kalau gak ketemu/gagal parse.
    private fun extractSupabaseErrorMessage(e: Exception): String {
        return try {
            if (e is retrofit2.HttpException) {
                val body = e.response()?.errorBody()?.string()
                val msg = body?.let { org.json.JSONObject(it).optString("message").takeIf { m -> m.isNotBlank() } }
                msg ?: "Komentar gagal terkirim, coba lagi nanti"
            } else {
                "Komentar gagal terkirim, coba lagi nanti"
            }
        } catch (parseError: Exception) {
            "Komentar gagal terkirim, coba lagi nanti"
        }
    }

    // ── Komentar Terbaru (lintas semua episode) — widget Home ──
    private val _recentComments = MutableStateFlow<List<EpisodeComment>>(emptyList())
    val recentComments: StateFlow<List<EpisodeComment>> = _recentComments.asStateFlow()

    private val _isRecentCommentsLoading = MutableStateFlow(false)
    val isRecentCommentsLoading: StateFlow<Boolean> = _isRecentCommentsLoading.asStateFlow()

    fun loadRecentComments() {
        viewModelScope.launch {
            _isRecentCommentsLoading.value = true
            try {
                val comments = NetworkClient.supabaseDbApi.getRecentComments(
                    authHeader = "Bearer $SUPABASE_ANON_KEY",
                    apiKey = SUPABASE_ANON_KEY
                )
                // Join profiles biar badge Lv./#id di widget Home sama kayak di komentar episode
                val profilesMap = try {
                    NetworkClient.supabaseDbApi.getProfiles(
                        authHeader = getAuthHeader(),
                        apiKey = SUPABASE_ANON_KEY
                    ).associateBy { it.id }
                } catch (e: Exception) {
                    Log.e("AnikuVM", "Gagal fetch profiles buat join komentar terbaru", e)
                    emptyMap()
                }
                _recentComments.value = comments.map { c ->
                    c.copy(
                        user_number = profilesMap[c.user_id]?.user_number,
                        season_level = profilesMap[c.user_id]?.season_level
                    )
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal load komentar terbaru", e)
            } finally {
                _isRecentCommentsLoading.value = false
            }
        }
    }

    fun deleteEpisodeComment(episodeSlug: String, commentId: String) {
        val currentSession = session.value
        val userId = currentSession.userId ?: return
        if (currentSession.token.isNullOrEmpty()) return
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deleteEpisodeComment(
                    idQuery = "eq.$commentId",
                    userIdQuery = "eq.$userId",
                    authHeader = "Bearer ${currentSession.token}",
                    apiKey = SUPABASE_ANON_KEY
                )
                _episodeComments.value = _episodeComments.value.filterNot { it.id == commentId }
            } catch (e: Exception) {
                Log.e("AnikuVM", "Gagal hapus komentar episode", e)
            }
        }
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
                        username = currentSession.username.nullIfBlank() ?: currentSession.email?.substringBefore("@") ?: "User",
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
                            username = currentSession.username.nullIfBlank() ?: currentSession.email?.substringBefore("@") ?: "Anonymous",
                            avatar_url = currentSession.avatarUrl,
                            role = when { currentSession.isAdmin -> "admin"; currentSession.isModerator -> "moderator"; currentSession.isBeta -> "beta"; else -> "user" },
                            is_admin = currentSession.isAdmin,
                            custom_name_color = currentSession.customNameColor,
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

    // --- CLAN CHAT (chat khusus member clan, di-scope per clan_id) ---
    private val _clanChatMessages = MutableStateFlow<List<ClanChatMessage>>(emptyList())
    val clanChatMessages: StateFlow<List<ClanChatMessage>> = _clanChatMessages.asStateFlow()

    private val _isClanChatLoading = MutableStateFlow(false)
    val isClanChatLoading: StateFlow<Boolean> = _isClanChatLoading.asStateFlow()

    private val _clanChatError = MutableStateFlow<String?>(null)
    val clanChatError: StateFlow<String?> = _clanChatError.asStateFlow()

    fun clearClanChatError() { _clanChatError.value = null }

    fun loadClanChatMessages(clanId: String) {
        viewModelScope.launch {
            _isClanChatLoading.value = true
            try {
                val messagesDeferred = NetworkClient.supabaseDbApi.getClanChatMessages(
                    clanIdQuery = "eq.$clanId",
                    authHeader = getAuthHeader(),
                    apiKey = SUPABASE_ANON_KEY
                )
                // Sama kayak chat global: API return terbaru dulu (desc), lalu di-reverse
                // di sini jadi kronologis (lama -> baru) buat ditampilin.
                _clanChatMessages.value = messagesDeferred.reversed()
            } catch (e: retrofit2.HttpException) {
                val errBody = e.response()?.errorBody()?.string() ?: "no body"
                _clanChatError.value = "HTTP ${e.code()}: $errBody"
                Log.e("AnikuVM", "loadClanChatMessages failed: HTTP ${e.code()} - $errBody")
            } catch (e: Exception) {
                _clanChatError.value = "Gagal memuat chat clan: ${e.message}"
                Log.e("AnikuVM", "loadClanChatMessages failed", e)
            } finally {
                _isClanChatLoading.value = false
            }
        }
    }

    fun sendClanChatMessage(
        clanId: String,
        message: String,
        replyToId: String? = null,
        replyToUsername: String? = null,
        replyToMessage: String? = null,
        imageUrl: String? = null
    ) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) {
            _clanChatError.value = "Kamu harus login untuk mengirim pesan"
            return
        }
        if (currentSession.isBanned) {
            _clanChatError.value = "Akunmu dibanned dari chat"
            return
        }
        val trimmed = message.trim()
        if (trimmed.isEmpty() && imageUrl == null) return
        if (trimmed.length > 300) return

        viewModelScope.launch {
            try {
                withValidToken { token ->
                    NetworkClient.supabaseDbApi.insertClanChatMessage(
                        data = ClanChatMessageRequest(
                            clan_id = clanId,
                            user_id = currentSession.userId ?: "",
                            username = currentSession.username.nullIfBlank() ?: currentSession.email?.substringBefore("@") ?: "Anonymous",
                            avatar_url = currentSession.avatarUrl,
                            role = when { currentSession.isAdmin -> "admin"; currentSession.isModerator -> "moderator"; currentSession.isBeta -> "beta"; else -> "user" },
                            is_admin = currentSession.isAdmin,
                            custom_name_color = currentSession.customNameColor,
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
                loadClanChatMessages(clanId)
            } catch (e: Exception) {
                _clanChatError.value = "Gagal kirim pesan: ${e.message}"
            }
        }
    }

    fun deleteClanChatMessage(messageId: String) {
        val currentSession = session.value
        if (currentSession.token.isNullOrEmpty()) return

        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deleteClanChatMessage(
                    idQuery = "eq.$messageId",
                    authHeader = "Bearer ${currentSession.token}",
                    apiKey = SUPABASE_ANON_KEY
                )
                _clanChatMessages.value = _clanChatMessages.value.filter { it.id != messageId }
            } catch (e: Exception) {
                _clanChatError.value = "Gagal hapus pesan: ${e.message}"
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
    fun reportWatchEvent(animeSlug: String, episodeSlug: String, checkpointNumber: Int) {
        val uid = session.value.userId ?: return
        viewModelScope.launch {
            try {
                withValidToken { token ->
                    val response = NetworkClient.supabaseDbApi.insertWatchCheckpoint(
                        data = WatchCheckpointRequest(
                            p_user_id = uid,
                            p_anime_slug = animeSlug,
                            p_episode_slug = episodeSlug,
                            p_checkpoint_number = checkpointNumber
                        ),
                        authHeader = "Bearer $token",
                        apiKey = SUPABASE_ANON_KEY
                    )
                    // PENTING: Response<Unit> dari Retrofit TIDAK nge-throw exception buat
                    // HTTP error (400/404/dst) - dia cuma balikin isSuccessful=false. Tanpa
                    // pengecekan ini, RPC yang gagal bakal ketelen diem-diem tanpa jejak sama
                    // sekali (gak masuk catch, gak ke-log). Toast ini SEMENTARA buat debugging
                    // tanpa perlu adb/logcat - bisa dihapus/diganti Log.e biasa setelah dipastikan
                    // beres.
                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        Log.e("AnikuVM", "reportWatchEvent gagal: HTTP ${response.code()} - $errorBody")
                        android.widget.Toast.makeText(
                            appContext,
                            "XP checkpoint gagal: ${response.code()} - ${errorBody?.take(150)}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "reportWatchEvent error: ${e.message}")
                android.widget.Toast.makeText(
                    appContext,
                    "XP checkpoint error: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
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

    private val _viewedProfileClan = MutableStateFlow<ClanDto?>(null)
    val viewedProfileClan: StateFlow<ClanDto?> = _viewedProfileClan.asStateFlow()

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
                _viewedProfileClan.value = null

                try {
                    val clanRows = NetworkClient.supabaseDbApi.getUserClanMembership("eq.$userId", authHeader, SUPABASE_ANON_KEY)
                    @Suppress("UNCHECKED_CAST")
                    val clanMap = clanRows.firstOrNull()?.get("clans") as? Map<String, Any?>
                    if (clanMap != null) {
                        _viewedProfileClan.value = ClanDto(
                            id = clanMap["id"] as? String ?: "",
                            name = clanMap["name"] as? String ?: "",
                            tag = clanMap["tag"] as? String ?: "",
                            level = (clanMap["level"] as? Double)?.toInt() ?: 1,
                            icon_url = clanMap["icon_url"] as? String
                        )
                    }
                } catch (e: Exception) {
                    Log.e("AnikuVM", "loadPublicUserProfile clan error: ${e.message}")
                }

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

    // ── Konten tab profil publik: Komentar / Favorit / Histori ──
    private val _viewedProfileComments = MutableStateFlow<List<EpisodeComment>>(emptyList())
    val viewedProfileComments: StateFlow<List<EpisodeComment>> = _viewedProfileComments.asStateFlow()

    private val _viewedProfileBookmarks = MutableStateFlow<List<UserBookmarkDto>>(emptyList())
    val viewedProfileBookmarks: StateFlow<List<UserBookmarkDto>> = _viewedProfileBookmarks.asStateFlow()

    private val _viewedProfileWatchHistory = MutableStateFlow<List<UserWatchHistoryDto>>(emptyList())
    val viewedProfileWatchHistory: StateFlow<List<UserWatchHistoryDto>> = _viewedProfileWatchHistory.asStateFlow()

    private val _isViewedProfileActivityLoading = MutableStateFlow(false)
    val isViewedProfileActivityLoading: StateFlow<Boolean> = _isViewedProfileActivityLoading.asStateFlow()

    fun loadPublicUserActivity(userId: String) {
        viewModelScope.launch {
            _isViewedProfileActivityLoading.value = true
            _viewedProfileComments.value = emptyList()
            _viewedProfileBookmarks.value = emptyList()
            _viewedProfileWatchHistory.value = emptyList()
            val authHeader = getAuthHeader()

            try {
                _viewedProfileComments.value = NetworkClient.supabaseDbApi.getUserComments(
                    userIdQuery = "eq.$userId",
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (e: Exception) {
                Log.e("AnikuVM", "loadPublicUserActivity comments error: ${e.message}")
            }

            try {
                _viewedProfileBookmarks.value = NetworkClient.supabaseDbApi.getUserBookmarks(
                    userIdQuery = "eq.$userId",
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (e: Exception) {
                Log.e("AnikuVM", "loadPublicUserActivity bookmarks error: ${e.message}")
            }

            try {
                _viewedProfileWatchHistory.value = NetworkClient.supabaseDbApi.getUserWatchHistory(
                    userIdQuery = "eq.$userId",
                    authHeader = authHeader,
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (e: Exception) {
                Log.e("AnikuVM", "loadPublicUserActivity history error: ${e.message}")
            }

            _isViewedProfileActivityLoading.value = false
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
                            username = currentSession.username.nullIfBlank() ?: currentSession.email?.substringBefore("@") ?: "Anonymous",
                            avatar_url = currentSession.avatarUrl,
                            role = when { currentSession.isAdmin -> "admin"; currentSession.isModerator -> "moderator"; currentSession.isBeta -> "beta"; else -> "user" },
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
                        username = currentSession.username.nullIfBlank() ?: currentSession.email?.substringBefore("@") ?: "Anonymous",
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
        val username = session.value.username.orDefault("Host")
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
        val username = session.value.username.orDefault("Guest")
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

    // ═══════════════════════════════════════════════════════
    // ── PERTEMANAN (Add Teman) ──
    // ═══════════════════════════════════════════════════════
    private val _friendships = MutableStateFlow<List<FriendshipDto>>(emptyList())
    val friendships: StateFlow<List<FriendshipDto>> = _friendships.asStateFlow()

    private val _isFriendshipsLoading = MutableStateFlow(false)
    val isFriendshipsLoading: StateFlow<Boolean> = _isFriendshipsLoading.asStateFlow()

    /** Daftar teman yang udah accepted. */
    val friendsList: StateFlow<List<FriendshipDto>> = friendships
        .map { list -> list.filter { it.status == "accepted" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Permintaan masuk (orang lain ngirim ke aku, belum aku respon). */
    val incomingFriendRequests: StateFlow<List<FriendshipDto>> = friendships
        .map { list ->
            val myId = session.value.userId
            list.filter { it.status == "pending" && it.addressee_id == myId }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun loadFriendships() {
        val myId = session.value.userId ?: return
        viewModelScope.launch {
            _isFriendshipsLoading.value = true
            try {
                _friendships.value = NetworkClient.supabaseDbApi.getFriendships(
                    orQuery = "(requester_id.eq.$myId,addressee_id.eq.$myId)",
                    authHeader = getAuthHeader(),
                    apiKey = SUPABASE_ANON_KEY
                )
            } catch (e: Exception) {
                Log.e("AnikuVM", "loadFriendships error: ${e.message}")
            } finally {
                _isFriendshipsLoading.value = false
            }
        }
    }

    /** Status pertemanan aku dengan userId tertentu: null / "pending_sent" / "pending_received" / "accepted". */
    fun friendshipStatusWith(userId: String): String? {
        val myId = session.value.userId ?: return null
        val match = friendships.value.firstOrNull {
            (it.requester_id == myId && it.addressee_id == userId) ||
                (it.requester_id == userId && it.addressee_id == myId)
        } ?: return null
        return when {
            match.status == "accepted" -> "accepted"
            match.status == "pending" && match.requester_id == myId -> "pending_sent"
            match.status == "pending" && match.addressee_id == myId -> "pending_received"
            else -> null
        }
    }

    fun friendshipWith(userId: String): FriendshipDto? {
        val myId = session.value.userId ?: return null
        return friendships.value.firstOrNull {
            (it.requester_id == myId && it.addressee_id == userId) ||
                (it.requester_id == userId && it.addressee_id == myId)
        }
    }

    fun sendFriendRequest(targetUserId: String) {
        val myId = session.value.userId ?: return
        if (myId == targetUserId) return
        viewModelScope.launch {
            try {
                val result = NetworkClient.supabaseDbApi.sendFriendRequest(
                    data = FriendshipRequest(requester_id = myId, addressee_id = targetUserId),
                    authHeader = getAuthHeader(),
                    apiKey = SUPABASE_ANON_KEY
                )
                _friendships.value = _friendships.value + result
            } catch (e: Exception) {
                Log.e("AnikuVM", "sendFriendRequest error: ${e.message}")
            }
        }
    }

    fun respondToFriendRequest(friendshipId: String, accept: Boolean) {
        viewModelScope.launch {
            try {
                val newStatus = if (accept) "accepted" else "rejected"
                NetworkClient.supabaseDbApi.updateFriendshipStatus(
                    idQuery = "eq.$friendshipId",
                    data = FriendshipStatusUpdate(status = newStatus, responded_at = java.time.Instant.now().toString()),
                    authHeader = getAuthHeader(),
                    apiKey = SUPABASE_ANON_KEY
                )
                loadFriendships()
            } catch (e: Exception) {
                Log.e("AnikuVM", "respondToFriendRequest error: ${e.message}")
            }
        }
    }

    fun removeFriendOrCancelRequest(friendshipId: String) {
        viewModelScope.launch {
            try {
                NetworkClient.supabaseDbApi.deleteFriendship(
                    idQuery = "eq.$friendshipId",
                    authHeader = getAuthHeader(),
                    apiKey = SUPABASE_ANON_KEY
                )
                _friendships.value = _friendships.value.filterNot { it.id == friendshipId }
            } catch (e: Exception) {
                Log.e("AnikuVM", "removeFriendOrCancelRequest error: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // ── PRIVATE CHAT (realtime, Firebase RTDB) ──
    // ═══════════════════════════════════════════════════════
    private val _privateChatMessages = MutableStateFlow<List<PrivateMessage>>(emptyList())
    val privateChatMessages: StateFlow<List<PrivateMessage>> = _privateChatMessages.asStateFlow()

    private val _activePrivateChatId = MutableStateFlow<String?>(null)
    val activePrivateChatId: StateFlow<String?> = _activePrivateChatId.asStateFlow()

    private val _activePrivateChatOtherUserId = MutableStateFlow<String?>(null)
    val activePrivateChatOtherUserId: StateFlow<String?> = _activePrivateChatOtherUserId.asStateFlow()

    private var privateChatListenJob: kotlinx.coroutines.Job? = null

    /** Pesan yang lagi di-reply di PrivateChatScreen (null = ga ada reply aktif). */
    private val _replyingToMessage = MutableStateFlow<PrivateMessage?>(null)
    val replyingToMessage: StateFlow<PrivateMessage?> = _replyingToMessage.asStateFlow()

    private val _userChats = MutableStateFlow<List<ChatPreview>>(emptyList())
    val userChats: StateFlow<List<ChatPreview>> = _userChats.asStateFlow()
    private var userChatsListenJob: kotlinx.coroutines.Job? = null

    /** Jumlah chat pribadi yang belum dibaca, real-time — dipakai buat badge di icon chat home. */
    val unreadPrivateChatCount: StateFlow<Int> = combine(_userChats, session) { chats, sess ->
        val myId = sess.userId
        if (myId.isNullOrBlank()) return@combine 0
        chats.count { it.lastSenderId.isNotBlank() && it.lastSenderId != myId && it.lastMessageAt > it.lastReadAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Mulai dengerin daftar chat (dipanggil sekali pas FriendsScreen/ChatList dibuka). */
    fun startListeningUserChats() {
        val myId = session.value.userId ?: return
        userChatsListenJob?.cancel()
        userChatsListenJob = viewModelScope.launch {
            PrivateChatManager.listenUserChats(myId).collect { chats ->
                _userChats.value = chats
            }
        }
    }

    fun stopListeningUserChats() {
        userChatsListenJob?.cancel()
        userChatsListenJob = null
    }

    /** Buka chat 1-on-1 dengan otherUserId, mulai dengerin pesan realtime. */
    fun openPrivateChat(otherUserId: String) {
        val myId = session.value.userId ?: return
        val chatId = PrivateChatManager.chatIdFor(myId, otherUserId)
        _activePrivateChatId.value = chatId
        _activePrivateChatOtherUserId.value = otherUserId
        _privateChatMessages.value = emptyList()

        privateChatListenJob?.cancel()
        privateChatListenJob = viewModelScope.launch {
            PrivateChatManager.listenMessages(chatId).collect { messages ->
                _privateChatMessages.value = messages
                // Tandai selalu terbaca selama chat ini lagi kebuka, termasuk pas
                // ada pesan baru masuk saat user masih di layar ini.
                if (messages.isNotEmpty()) {
                    try {
                        PrivateChatManager.markChatAsRead(myId, chatId)
                    } catch (e: Exception) {
                        Log.e("AnikuVM", "markChatAsRead error: ${e.message}")
                    }
                }
            }
        }
    }

    fun closePrivateChat() {
        privateChatListenJob?.cancel()
        privateChatListenJob = null
        _activePrivateChatId.value = null
        _activePrivateChatOtherUserId.value = null
        _privateChatMessages.value = emptyList()
        _replyingToMessage.value = null
    }

    /** Set pesan yang mau di-reply (dipanggil dari long-press bubble). */
    fun setReplyTarget(message: PrivateMessage) {
        _replyingToMessage.value = message
    }

    /** Batalin reply yang lagi aktif (tombol X di preview reply). */
    fun clearReplyTarget() {
        _replyingToMessage.value = null
    }

    fun sendPrivateMessage(text: String) {
        val myId = session.value.userId ?: return
        val chatId = _activePrivateChatId.value ?: return
        val otherUserId = _activePrivateChatOtherUserId.value ?: return
        val myUsername = session.value.username.orDefault("Seseorang")
        val replyTo = _replyingToMessage.value
        _replyingToMessage.value = null
        viewModelScope.launch {
            try {
                PrivateChatManager.sendMessage(chatId, myId, otherUserId, text, replyTo)

                // Kirim push notif ke penerima (targeted, bukan broadcast topic).
                // Gagal kirim notif jangan sampai bikin pesan gagal dianggap error.
                try {
                    NetworkClient.supabaseFunctionsApi.sendPrivateChatNotification(
                        request = PrivateChatNotifRequest(
                            recipientUserId = otherUserId,
                            senderId = myId,
                            senderName = myUsername,
                            messageText = text
                        ),
                        apiKey = SUPABASE_ANON_KEY,
                        authHeader = getAuthHeader()
                    )
                } catch (notifEx: Exception) {
                    Log.e("AnikuVM", "sendPrivateChatNotification gagal: ${notifEx.message}")
                }
            } catch (e: Exception) {
                Log.e("AnikuVM", "sendPrivateMessage error: ${e.message}")
            }
        }
    }

    /** Hapus pesan (soft delete, kayak WA "Hapus untuk semua"). Cuma pengirim yang bisa hapus. */
    fun deletePrivateMessage(messageId: String) {
        val myId = session.value.userId ?: return
        val chatId = _activePrivateChatId.value ?: return
        if (_replyingToMessage.value?.id == messageId) {
            _replyingToMessage.value = null
        }
        viewModelScope.launch {
            try {
                PrivateChatManager.deleteMessage(chatId, messageId, myId)
            } catch (e: Exception) {
                Log.e("AnikuVM", "deletePrivateMessage error: ${e.message}")
            }
        }
    }

}
