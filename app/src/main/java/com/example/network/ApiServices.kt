package com.example.network

import android.content.Context
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.concurrent.TimeUnit

interface AnimeApi {
    @GET("animasu/home")
    suspend fun getHome(
        @Query("apikey") apiKey: String = "planaai"
    ): HomeResponse

    @GET("animasu/popular")
    suspend fun getPopular(
        @Query("apikey") apiKey: String = "planaai",
        @Query("page") page: Int? = null
    ): AnimesListResponse

    @GET("animasu/movies")
    suspend fun getMovies(
        @Query("apikey") apiKey: String = "planaai",
        @Query("page") page: Int? = null
    ): AnimesListResponse

    @GET("animasu/ongoing")
    suspend fun getOngoing(
        @Query("apikey") apiKey: String = "planaai",
        @Query("page") page: Int? = null
    ): AnimesListResponse

    @GET("animasu/completed")
    suspend fun getCompleted(
        @Query("apikey") apiKey: String = "planaai",
        @Query("page") page: Int? = null
    ): AnimesListResponse

    @GET("animasu/latest")
    suspend fun getLatest(
        @Query("apikey") apiKey: String = "planaai",
        @Query("page") page: Int? = null
    ): AnimesListResponse

    @GET("animasu/search/{keyword}")
    suspend fun search(
        @Path("keyword") keyword: String,
        @Query("apikey") apiKey: String = "planaai"
    ): AnimesListResponse

    @GET("animasu/animelist")
    suspend fun getAnimeList(
        @Query("apikey") apiKey: String = "planaai",
        @Query("page") page: Int? = null
    ): AnimesListResponse

    @GET("animasu/genres")
    suspend fun getGenres(
        @Query("apikey") apiKey: String = "planaai"
    ): GenreListResponse

    @GET("animasu/genre/{slug}")
    suspend fun getAnimeByGenre(
        @Path("slug") slug: String,
        @Query("apikey") apiKey: String = "planaai",
        @Query("page") page: Int? = null
    ): AnimesListResponse

    @GET("animasu/schedule")
    suspend fun getSchedule(
        @Query("apikey") apiKey: String = "planaai"
    ): ScheduleResponse

    @GET("animasu/detail/{slug}")
    suspend fun getDetail(
        @Path("slug") slug: String,
        @Query("apikey") apiKey: String = "planaai"
    ): DetailResponse

    @GET("animasu/episode/{slug}")
    suspend fun getEpisode(
        @Path("slug") slug: String,
        @Query("apikey") apiKey: String = "planaai"
    ): EpisodeResponse
}

interface SamehadakuApi {
    @GET("samehadaku/home")
    suspend fun getHome(): SamehadakuHomeResponse

    @GET("samehadaku/recent")
    suspend fun getRecent(@Query("page") page: Int? = null): SamehadakuListResponse

    @GET("samehadaku/popular")
    suspend fun getPopular(@Query("page") page: Int? = null): SamehadakuListResponse

    @GET("samehadaku/movies")
    suspend fun getMovies(@Query("page") page: Int? = null): SamehadakuListResponse

    @GET("samehadaku/ongoing")
    suspend fun getOngoing(@Query("page") page: Int? = null): SamehadakuListResponse

    @GET("samehadaku/completed")
    suspend fun getCompleted(@Query("page") page: Int? = null): SamehadakuListResponse

    @GET("samehadaku/search")
    suspend fun search(@Query("q") keyword: String, @Query("page") page: Int? = null): SamehadakuListResponse

    @GET("samehadaku/list")
    suspend fun getAnimeList(@Query("page") page: Int? = null): SamehadakuListResponse

    @GET("samehadaku/genres")
    suspend fun getGenres(): SamehadakuGenresResponse

    @GET("samehadaku/genres/{genreId}")
    suspend fun getAnimeByGenre(@Path("genreId") genreId: String, @Query("page") page: Int? = null): SamehadakuListResponse

    @GET("samehadaku/schedule")
    suspend fun getSchedule(): SamehadakuScheduleResponse

    @GET("samehadaku/anime/{animeId}")
    suspend fun getDetail(@Path("animeId") animeId: String): SamehadakuDetailResponse

    @GET("samehadaku/episode/{episodeId}")
    suspend fun getEpisode(@Path("episodeId") episodeId: String): SamehadakuEpisodeResponse

    @GET("samehadaku/server/{serverId}")
    suspend fun getServerLink(@Path("serverId") serverId: String): SamehadakuServerLinkResponse
}

interface SupabaseAuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: SignUpRequest,
        @Header("apikey") apiKey: String
    ): AuthResponse

    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(
        @Body request: SignInRequest,
        @Header("apikey") apiKey: String
    ): AuthResponse

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest,
        @Header("apikey") apiKey: String
    ): AuthResponse

    @POST("auth/v1/recover")
    suspend fun recoverPassword(
        @Body request: RecoverRequest,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>
}

interface SupabaseDbApi {
    @GET("rest/v1/announcements")
    suspend fun getAnnouncements(
        @Query("is_active") isActive: String = "eq.true",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<AnnouncementDto>

    @GET("rest/v1/announcements")
    suspend fun getAllAnnouncements(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<AnnouncementDto>

    @GET("rest/v1/featured_anime")
    suspend fun getFeaturedAnime(
        @Query("select") select: String = "*",
        @Query("order") order: String = "order_index.asc",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<FeaturedAnimeDto>

    @GET("rest/v1/blacklisted_anime")
    suspend fun getBlacklistedAnime(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<BlacklistedAnimeDto>

    @GET("rest/v1/profiles")
    suspend fun getProfiles(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ProfileDto>

    @GET("rest/v1/profiles")
    suspend fun getProfileByUserId(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ProfileDto>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Query("id") idQuery: String,
        @Body profile: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=minimal"
    ): retrofit2.Response<Unit>

    @POST("rest/v1/announcements")
    suspend fun insertAnnouncement(
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<AnnouncementDto>

    @PATCH("rest/v1/announcements")
    suspend fun updateAnnouncement(
        @Query("id") idQuery: String,
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<AnnouncementDto>

    @DELETE("rest/v1/announcements")
    suspend fun deleteAnnouncement(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/featured_anime")
    suspend fun insertFeaturedAnime(
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<FeaturedAnimeDto>

    @DELETE("rest/v1/featured_anime")
    suspend fun deleteFeaturedAnime(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/blacklisted_anime")
    suspend fun insertBlacklistedAnime(
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<BlacklistedAnimeDto>

    @DELETE("rest/v1/blacklisted_anime")
    suspend fun deleteBlacklistedAnime(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @GET("rest/v1/watch_chat")
    suspend fun getWatchChatMessages(
        @Query("episode_slug") episodeSlug: String,
        @Query("order") order: String = "created_at.asc",
        @Query("limit") limit: Int = 100,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<WatchChatMessage>

    @POST("rest/v1/watch_chat")
    suspend fun insertWatchChatMessage(
        @Body data: WatchChatRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<WatchChatMessage>

    @GET("rest/v1/chat_messages")
    suspend fun getChatMessages(
        @Query("select") select: String = "*,profiles!chat_messages_user_id_fkey(user_number)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ChatMessageWithProfile>

    @POST("rest/v1/chat_messages")
    suspend fun insertChatMessage(
        @Body data: ChatMessageRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<ChatMessage>

    @DELETE("rest/v1/chat_messages")
    suspend fun deleteChatMessage(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @GET("rest/v1/posts")
    suspend fun getPosts(
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<Post>

    @POST("rest/v1/posts")
    suspend fun insertPost(
        @Body data: PostRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<Post>

    @DELETE("rest/v1/posts")
    suspend fun deletePost(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @GET("rest/v1/post_likes")
    suspend fun getLikes(
        @Query("post_id") postIdQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<PostLike>

    @POST("rest/v1/post_likes")
    suspend fun insertLike(
        @Body data: PostLikeRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<PostLike>

    @DELETE("rest/v1/post_likes")
    suspend fun deleteLike(
        @Query("post_id") postIdQuery: String,
        @Query("user_id") userIdQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @GET("rest/v1/post_comments")
    suspend fun getComments(
        @Query("post_id") postIdQuery: String,
        @Query("order") order: String = "created_at.asc",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<PostComment>

    @POST("rest/v1/post_comments")
    suspend fun insertComment(
        @Body data: PostCommentRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<PostComment>

    @DELETE("rest/v1/post_comments")
    suspend fun deleteComment(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @GET("rest/v1/donations")
    suspend fun getDonations(
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 10,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<Donation>

    @GET("rest/v1/active_viewers")
    suspend fun getViewerCount(
        @Query("anime_slug") animeSlug: String,
        @Query("select") select: String = "user_id",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "count=exact"
    ): List<Map<String, String>>

    @GET("rest/v1/active_viewers")
    suspend fun getAllViewerCounts(
        @Query("select") select: String = "anime_slug",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "count=exact"
    ): List<Map<String, String>>

    @POST("rest/v1/active_viewers")
    suspend fun upsertViewer(
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal"
    ): retrofit2.Response<Unit>

    @DELETE("rest/v1/active_viewers")
    suspend fun removeViewer(
        @Query("anime_slug") animeSlug: String,
        @Query("user_id") userId: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @DELETE("rest/v1/active_viewers")
    suspend fun removeStaleViewers(
        @Query("last_seen") lastSeen: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>
}

interface CloudinaryApi {
    @Multipart
    @POST("v1_1/dzfkklsza/image/upload")
    suspend fun uploadAvatar(
        @Part file: MultipartBody.Part,
        @Part("upload_preset") uploadPreset: RequestBody
    ): CloudinaryResponse
}

// ─── Cache Interceptor untuk Anime API (Sanka) ───────────────────────────────

/**
 * Online: paksa cache 1 jam — request ke Sanka dilakukan max 1x per jam
 * untuk endpoint yang sama. Setelah 1 jam, baru fetch ulang ke server.
 */
class OnlineCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val cacheControl = CacheControl.Builder()
            .maxAge(1, TimeUnit.HOURS)
            .build()
        return response.newBuilder()
            .header("Cache-Control", cacheControl.toString())
            .removeHeader("Pragma") // Pragma: no-cache bisa block OkHttp cache
            .build()
    }
}

/**
 * Offline: kalau tidak ada koneksi, pakai cache sampai 7 hari.
 * User tetap bisa lihat data lama daripada error.
 */
class OfflineCacheInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = if (!isNetworkAvailable(context)) {
            val cacheControl = CacheControl.Builder()
                .maxStale(7, TimeUnit.DAYS)
                .build()
            chain.request().newBuilder()
                .cacheControl(cacheControl)
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

// ─── Network Client ───────────────────────────────────────────────────────────

object NetworkClient {
    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // OkHttpClient khusus Anime API — dengan cache 50MB
    fun animeOkHttpClient(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "anime_api_cache")
        val cache = Cache(cacheDir, 50L * 1024 * 1024) // 50 MB

        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(OfflineCacheInterceptor(context)) // cek offline dulu
            .addNetworkInterceptor(OnlineCacheInterceptor())  // set cache header saat online
            .addInterceptor(loggingInterceptor)
            .build()
    }

    // OkHttpClient untuk Supabase & Cloudinary — no cache (data realtime)
    private val defaultOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    fun animeApi(context: Context): AnimeApi =
        Retrofit.Builder()
            .baseUrl("https://www.sankavollerei.com/anime/")
            .client(animeOkHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnimeApi::class.java)

    fun samehadakuApi(context: Context): SamehadakuApi =
        Retrofit.Builder()
            .baseUrl("https://www.sankavollerei.com/anime/")
            .client(animeOkHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SamehadakuApi::class.java)

    val supabaseAuthApi: SupabaseAuthApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://uczxaiyibnwgycodtcvm.supabase.co/")
            .client(defaultOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseAuthApi::class.java)
    }

    val supabaseDbApi: SupabaseDbApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://uczxaiyibnwgycodtcvm.supabase.co/")
            .client(defaultOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseDbApi::class.java)
    }

    val cloudinaryApi: CloudinaryApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.cloudinary.com/")
            .client(defaultOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CloudinaryApi::class.java)
    }
}


// ─── Cara update AnikuViewModel.kt ───────────────────────────────────────────
// Tambahkan 2 baris ini di dalam class AnikuViewModel, setelah baris appContext:
//
//   private val animeApi by lazy { NetworkClient.animeApi(appContext) }
//   private val samehadakuApi by lazy { NetworkClient.samehadakuApi(appContext) }
//
// Lalu ganti semua:
//   NetworkClient.animeApi.xxx  →  animeApi.xxx
//   NetworkClient.samehadakuApi.xxx  →  samehadakuApi.xxx
//
// NetworkClient.supabaseDbApi dan NetworkClient.supabaseAuthApi tidak perlu diubah.
