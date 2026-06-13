package com.example.network

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
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
    ): Response<Unit>
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
    ): Response<Unit>

    // Admin endpoints: Announcements
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
    ): Response<Unit>

    // Admin endpoints: Featured Anime
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
    ): Response<Unit>

    // Admin endpoints: Blacklist
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
    ): Response<Unit>

    // Chat Room endpoints
    @GET("rest/v1/chat_messages")
    suspend fun getChatMessages(
        @Query("order") order: String = "created_at.asc",
        @Query("limit") limit: Int = 100,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ChatMessage>

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
    ): Response<Unit>

    // Posts
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
    ): Response<Unit>

    // Likes
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
    ): Response<Unit>

    // Comments
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
    ): Response<Unit>
}

interface CloudinaryApi {
    @Multipart
    @POST("v1_1/dzfkklsza/image/upload")
    suspend fun uploadAvatar(
        @Part file: MultipartBody.Part,
        @Part("upload_preset") uploadPreset: RequestBody
    ): CloudinaryResponse
}

object NetworkClient {
    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    val animeApi: AnimeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.sankavollerei.com/anime/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnimeApi::class.java)
    }

    val supabaseAuthApi: SupabaseAuthApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://uczxaiyibnwgycodtcvm.supabase.co/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseAuthApi::class.java)
    }

    val supabaseDbApi: SupabaseDbApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://uczxaiyibnwgycodtcvm.supabase.co/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseDbApi::class.java)
    }

    val cloudinaryApi: CloudinaryApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.cloudinary.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CloudinaryApi::class.java)
    }
}
