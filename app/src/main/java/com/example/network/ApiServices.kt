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
        @Query("apikey") apiKey: String = "planaai",
        @Query("page") page: Int? = null
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

// Animekompi (Dayynime-v3) — base url beda dari Animasu/Samehadaku:
// https://www.sankavollerei.web.id/anime/animekompi/
interface AnimekompiApi {
    @GET("home")
    suspend fun getHome(): AnimekompiHomeResponse

    @GET("terbaru")
    suspend fun getTerbaru(@Query("page") page: Int? = null): AnimekompiListResponse

    @GET("order/popular")
    suspend fun getPopular(@Query("page") page: Int? = null): AnimekompiListResponse

    @GET("movie")
    suspend fun getMovies(@Query("page") page: Int? = null): AnimekompiListResponse

    @GET("status/ongoing")
    suspend fun getOngoing(@Query("page") page: Int? = null): AnimekompiListResponse

    @GET("status/completed")
    suspend fun getCompleted(@Query("page") page: Int? = null): AnimekompiListResponse

    @GET("search")
    suspend fun search(@Query("q") keyword: String, @Query("page") page: Int? = null): AnimekompiListResponse

    @GET("genres")
    suspend fun getGenres(): AnimekompiGenresResponse

    @GET("genre/{slug}")
    suspend fun getAnimeByGenre(@Path("slug") slug: String, @Query("page") page: Int? = null): AnimekompiListResponse

    @GET("schedule")
    suspend fun getSchedule(): AnimekompiScheduleResponse

    @GET("detail/{slug}")
    suspend fun getDetail(@Path("slug") slug: String): AnimekompiDetailResponse

    @GET("episode/{slug}")
    suspend fun getEpisode(@Path("slug") slug: String): AnimekompiEpisodeResponse
}

// Donghua (Anichin, scraper Sanka Vollerei) — sumber ke-4, base url beda lagi:
// https://www.sankavollerei.web.id/anime/  (prefix "donghua/...")
interface DonghuaApi {
    @GET("donghua/home/{page}")
    suspend fun getHome(@Path("page") page: Int = 1): DonghuaHomeResponse

    @GET("donghua/ongoing/{page}")
    suspend fun getOngoing(@Path("page") page: Int = 1): DonghuaOngoingResponse

    @GET("donghua/completed/{page}")
    suspend fun getCompleted(@Path("page") page: Int = 1): DonghuaCompletedResponse

    @GET("donghua/latest/{page}")
    suspend fun getLatest(@Path("page") page: Int = 1): DonghuaLatestResponse

    @GET("donghua/schedule")
    suspend fun getSchedule(): DonghuaScheduleResponse

    @GET("donghua/az-list/{letter}/{page}")
    suspend fun getAzList(@Path("letter") letter: String, @Path("page") page: Int = 1): DonghuaAzListResponse

    @GET("donghua/search/{query}")
    suspend fun search(@Path("query") query: String): DonghuaSearchResponse

    @GET("donghua/detail/{slug}")
    suspend fun getDetail(@Path("slug") slug: String): DonghuaDetailResponse

    @GET("donghua/episode/{slug}")
    suspend fun getEpisode(@Path("slug") slug: String): DonghuaEpisodeResponse

    @GET("donghua/genres")
    suspend fun getGenres(): DonghuaGenresResponse

    @GET("donghua/genres/{slug}/{page}")
    suspend fun getByGenre(@Path("slug") slug: String, @Path("page") page: Int = 1): DonghuaGenreDetailResponse

    @GET("donghua/seasons/{year}")
    suspend fun getBySeason(@Path("year") year: String): DonghuaGenreDetailResponse
}

// Animeinweb (Dayynime-v5) — wrapper Flask sendiri, deploy Vercel:
// https://animeinweb-api.vercel.app/api/
// Semua video-nya udah direct mp4 (storages.animein.net), gak perlu VideoExtractor.
interface AnimeinwebApi {
    @GET("homepage")
    suspend fun getHome(): AnimeinwebHomeResponse

    @GET("search")
    suspend fun search(
        @Query("q") keyword: String = "",
        @Query("page") page: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("genre_in") genreIn: String? = null,
        @Query("status") status: String? = null,
        @Query("type") type: String? = null
    ): AnimeinwebSearchResponse

    @GET("anime/{id}")
    suspend fun getDetail(@Path("id") id: String): AnimeinwebItem

    @GET("anime/{id}/episodes")
    suspend fun getEpisodes(
        @Path("id") id: String,
        @Query("page") page: Int? = null
    ): List<AnimeinwebEpisodeItem>

    @GET("episode/{episodeId}/stream")
    suspend fun getEpisodeStream(@Path("episodeId") episodeId: String): AnimeinwebStreamResponse

    @GET("schedule")
    suspend fun getSchedule(@Query("day") day: String): List<AnimeinwebItem>

    @GET("genres")
    suspend fun getGenres(): List<AnimeinwebGenreItem>
}

interface SupabaseFunctionsApi {
    @POST("functions/v1/register-ip-guard")
    suspend fun checkIpGuard(
        @Body request: IpGuardRequest,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): IpGuardResponse

    @POST("functions/v1/bright-processor")
    suspend fun sendPrivateChatNotification(
        @Body request: PrivateChatNotifRequest,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): PrivateChatNotifResponse
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

    @POST("auth/v1/token?grant_type=id_token")
    suspend fun signInWithIdToken(
        @Body request: IdTokenSignInRequest,
        @Header("apikey") apiKey: String
    ): AuthResponse

    @POST("auth/v1/recover")
    suspend fun recoverPassword(
        @Body request: RecoverRequest,
        @Header("apikey") apiKey: String,
        @Query("redirect_to") redirectTo: String = "aniku://reset-password"
    ): retrofit2.Response<Unit>

    @PUT("auth/v1/user")
    suspend fun updateUserPassword(
        @Body request: UpdatePasswordRequest,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): retrofit2.Response<Unit>
}

interface SupabaseDbApi {
    @GET("rest/v1/announcements")
    suspend fun getAnnouncements(
        @Query("is_active") isActive: String = "eq.true",
        @Query("order") order: String = "created_at.desc",
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

    // Feature flags - buat rollout bertahap "Beta akses duluan" tanpa perlu update APK.
    // Dibaca sekali pas app dibuka, di-cache di ViewModel.
    @GET("rest/v1/feature_flags")
    suspend fun getFeatureFlags(
        @Header("apikey") apiKey: String
    ): List<FeatureFlagDto>

    @PATCH("rest/v1/feature_flags")
    suspend fun updateFeatureFlag(
        @Query("feature_key") keyQuery: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=minimal"
    ): retrofit2.Response<Unit>

    @POST("rest/v1/feature_flags")
    suspend fun insertFeatureFlag(
        @Body body: FeatureFlagDto,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=minimal"
    ): retrofit2.Response<Unit>

    // Blacklist genre — genre yang disembunyikan dari daftar pilihan genre di Eksplor
    @GET("rest/v1/blacklisted_genres")
    suspend fun getBlacklistedGenres(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<BlacklistedGenreDto>

    @GET("rest/v1/profiles")
    suspend fun getProfiles(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ProfileDto>

    // Khusus buat layar "Daftar Pengguna" (publik) -- cuma ambil kolom yang
    // dipakai di UI + dibatasi limit, biar response gak makin membengkak
    // seiring user bertambah (ini penyebab OOM crash di HttpLoggingInterceptor
    // waktu response /profiles tanpa select/limit makin gede).
    @GET("rest/v1/profiles")
    suspend fun getUserDirectory(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Query("select") select: String = "id,username,avatar_url,user_number,season_xp,season_level,is_banned,custom_name_color,premium_until",
        @Query("order") order: String = "season_xp.desc.nullslast",
        @Query("limit") limit: Int = 500
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

    @POST("rest/v1/rpc/toggle_user_ban")
    suspend fun toggleUserBan(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // Simpan pajangan kartu karakter di profil (maks 6). Validasi kepemilikan
    // dilakukan di server lewat RPC ini -- jangan pernah PATCH kolom
    // showcase_character_ids langsung ke tabel profiles.
    @POST("rest/v1/rpc/set_profile_showcase")
    suspend fun setProfileShowcase(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // Ambil detail karakter (nama, gambar, rarity) buat sekumpulan mal_id --
    // dipakai buat nampilin kartu yang dipajang di profil (milik sendiri/orang lain).
    @GET("rest/v1/characters")
    suspend fun getCharactersByIds(
        @Query("mal_id") malIdFilter: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Query("select") select: String = "mal_id,name,image_url,anime_title,rarity"
    ): List<CharacterInfoDto>

    // Semua karakter dari 1 anime tertentu (filter by anime_mal_id, BUKAN mal_id
    // karakter kayak getCharactersByIds di atas) -- dipakai buat nampilin isi
    // roster 1 event gacha (EventCharactersScreen), biar admin/user bisa cek
    // karakter apa aja + rarity-nya sebelum/selama event jalan.
    @GET("rest/v1/characters")
    suspend fun getCharactersByAnime(
        @Query("anime_mal_id") animeMalIdFilter: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Query("select") select: String = "mal_id,name,image_url,anime_title,rarity",
        @Query("order") order: String = "name.asc"
    ): List<CharacterInfoDto>

    // --- Clan & Diamond ---
    @GET("rest/v1/clan_members")
    suspend fun getUserClanMembership(
        @Query("user_id") userIdQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Query("select") select: String = "clan_id,clans(id,name,tag,icon_url,level)"
    ): List<Map<String, @JvmSuppressWildcards Any?>>

    @GET("rest/v1/clan_members")
    suspend fun getAllClanTags(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Query("select") select: String = "user_id,clans(tag,icon_url)"
    ): List<Map<String, @JvmSuppressWildcards Any?>>

    @GET("rest/v1/clans")
    suspend fun getClanById(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ClanDto>

    @GET("rest/v1/clans")
    suspend fun getClans(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Query("order") order: String = "total_xp.desc"
    ): List<ClanDto>

    @GET("rest/v1/clan_members")
    suspend fun getClanMembers(
        @Query("clan_id") clanIdQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Query("select") select: String = "*,profiles(username,avatar_url)"
    ): List<Map<String, @JvmSuppressWildcards Any?>>

    @GET("rest/v1/clan_members")
    suspend fun getMyClanMembership(
        @Query("user_id") userIdQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ClanMemberDto>

    @POST("rest/v1/rpc/create_clan")
    suspend fun createClan(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<ClanDto>

    @POST("rest/v1/rpc/join_clan")
    suspend fun joinClan(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/rpc/leave_clan")
    suspend fun leaveClan(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/rpc/contribute_to_clan")
    suspend fun contributeToClan(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/rpc/admin_add_diamond")
    suspend fun adminAddDiamond(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/rpc/update_clan_icon")
    suspend fun updateClanIcon(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/rpc/kick_member")
    suspend fun kickMember(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // Leader jadiin salah satu member biasa jadi co-leader (bisa approve
    // join request & kick member biasa, tapi gak bisa kick leader/co-leader lain)
    @POST("rest/v1/rpc/promote_co_leader")
    suspend fun promoteCoLeader(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // Leader copot co-leader balik jadi member biasa
    @POST("rest/v1/rpc/demote_co_leader")
    suspend fun demoteCoLeader(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // Leader transfer jabatan leader ke member/co-leader lain. Leader lama
    // otomatis turun jadi co-leader (bukan dicopot ke member biasa).
    @POST("rest/v1/rpc/transfer_leader_clan")
    suspend fun transferLeaderClan(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/rpc/delete_clan")
    suspend fun deleteClan(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/rpc/rename_clan")
    suspend fun renameClan(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // Ganti singkatan/tag clan (mis. "COC") -- terpisah dari rename nama biar
    // validasi panjang & keunikan tag gak nyampur sama validasi nama.
    @POST("rest/v1/rpc/rename_clan_tag")
    suspend fun renameClanTag(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // Dipanggil abis register/login berhasil -- nyimpen/nge-update device ID
    // (Android ID) punya user, dan langsung nge-ban akun ini otomatis kalau
    // device-nya udah masuk daftar banned_devices (dipasang admin lewat tombol Ban).
    @POST("rest/v1/rpc/check_device_guard")
    suspend fun checkDeviceGuard(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): DeviceGuardResponse

    // Cek device-ban murni pakai device_id, TANPA butuh user_id -- dipanggil
    // paling awal SEBELUM akun Supabase Auth dibikin (beda dari check_device_guard
    // di atas yang butuh user_id, jadi cuma bisa dipanggil abis akun ada).
    @POST("rest/v1/rpc/is_device_banned")
    suspend fun isDeviceBanned(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): DeviceGuardResponse

    @POST("rest/v1/rpc/set_clan_privacy")
    suspend fun setClanPrivacy(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/rpc/request_join_clan")
    suspend fun requestJoinClan(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<ClanJoinRequestDto>

    @POST("rest/v1/rpc/approve_join_request")
    suspend fun approveJoinRequest(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @POST("rest/v1/rpc/reject_join_request")
    suspend fun rejectJoinRequest(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    @GET("rest/v1/clan_join_requests")
    suspend fun getClanJoinRequests(
        @Query("clan_id") clanIdQuery: String,
        @Query("status") statusQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Query("select") select: String = "*,profiles(username,avatar_url)"
    ): List<Map<String, @JvmSuppressWildcards Any?>>

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

    @PATCH("rest/v1/announcements")
    suspend fun deactivateAllAnnouncements(
        @Query("is_active") isActive: String = "eq.true",
        @Body data: Map<String, @JvmSuppressWildcards Any?> = mapOf("is_active" to false),
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=minimal"
    ): retrofit2.Response<Unit>

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

    @POST("rest/v1/blacklisted_genres")
    suspend fun insertBlacklistedGenre(
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<BlacklistedGenreDto>

    @DELETE("rest/v1/blacklisted_genres")
    suspend fun deleteBlacklistedGenre(
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

    @GET("rest/v1/episode_comments")
    suspend fun getEpisodeComments(
        @Query("episode_slug") episodeSlug: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 200,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<EpisodeComment>

    // Komentar terbaru lintas semua episode — dipakai untuk widget "Komentar Terbaru" di Home
    @GET("rest/v1/episode_comments")
    suspend fun getRecentComments(
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 15,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<EpisodeComment>

    @POST("rest/v1/episode_comments")
    suspend fun insertEpisodeComment(
        @Body data: EpisodeCommentRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<EpisodeComment>

    @DELETE("rest/v1/episode_comments")
    suspend fun deleteEpisodeComment(
        @Query("id") idQuery: String,
        @Query("user_id") userIdQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // SECURITY: dulu ini insert langsung ke rest/v1/watch_events (bisa dispam pakai script
    // asal punya token login, karena gak ada validasi server selain RLS auth.uid()=user_id).
    // Sekarang lewat RPC log_watch_event (SECURITY DEFINER) yang validasi auth.uid() harus
    // sama dengan user_id yang dikirim + rate limit di server. Insert langsung ke tabel
    // watch_events dari role anon/authenticated sudah di-revoke lewat migration SQL, jadi
    // endpoint lama otomatis ke-block walau ada yang masih coba manggilnya manual.
    // Body request (WatchEventRequest) sengaja gak diubah field-nya biar sama persis
    // dengan parameter RPC di database (user_id, anime_slug, episode_slug).
    @POST("rest/v1/rpc/log_watch_event")
    suspend fun insertWatchEvent(
        @Body data: WatchEventRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=minimal"
    ): retrofit2.Response<Unit>

    // Kasih Diamond ke user lain - cuma role beta/moderator/admin (divalidasi ulang
    // server-side di dalam function give_diamond, gak bisa dibypass), dengan limit
    // harian 1000 DM yang juga dicek di server.
    @POST("rest/v1/rpc/give_diamond")
    suspend fun giveDiamond(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=minimal"
    ): retrofit2.Response<Unit>

    // --- Premium Gift & Giveaway ---

    @GET("rest/v1/premium_packages")
    suspend fun getPremiumPackages(
        @Query("is_active") isActive: String = "eq.true",
        @Header("apikey") apiKey: String
    ): List<PremiumPackageDto>

    // Beli premium buat diri sendiri
    @POST("rest/v1/rpc/create_self_premium_claim")
    suspend fun createSelfPremiumClaim(
        @Body body: CreateSelfPremiumClaimRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): PremiumClaimDto

    // Gift langsung ke 1 user tertentu (dari profil orang lain)
    @POST("rest/v1/rpc/create_premium_claim")
    suspend fun createPremiumClaim(
        @Body body: CreatePremiumClaimRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): PremiumClaimDto

    // Versi gift langsung yang bayarnya potong sisa hari Premium PENGIRIM sendiri
    // (bukan uang) -- durasi BEBAS per hari, gak kebatas paket 7/30/90.
    // Nama RPC sama kayak versi paket (overload di server, dibedain PostgREST
    // dari nama parameter di body: p_duration_days vs p_package_id).
    @POST("rest/v1/rpc/create_premium_claim_from_premium")
    suspend fun createPremiumClaimFromPremium(
        @Body body: CreatePremiumClaimFromDaysRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): PremiumClaimDto

    // Bikin giveaway "War di Chat Global" (belum ada target user)
    @POST("rest/v1/rpc/create_giveaway_claim")
    suspend fun createGiveawayClaim(
        @Body body: CreateGiveawayClaimRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): PremiumClaimDto

    // Versi giveaway yang bayarnya potong sisa hari Premium PENGIRIM sendiri
    // (bukan uang) -- durasi BEBAS per hari, gak kebatas paket 7/30/90.
    @POST("rest/v1/rpc/create_giveaway_claim_from_premium")
    suspend fun createGiveawayClaimFromPremium(
        @Body body: CreateGiveawayClaimFromDaysRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): PremiumClaimDto

    // User tap tombol "Klaim" di bubble giveaway chat
    @POST("rest/v1/rpc/claim_giveaway")
    suspend fun claimGiveaway(
        @Body body: ClaimGiveawayRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ClaimGiveawayResult>

    // Cek status klaim tertentu (buat polling status "udah dibayar belum" di UI)
    @GET("rest/v1/premium_claims")
    suspend fun getPremiumClaimById(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<PremiumClaimDto>

    // Badge Top Support & Top XP di profil
    @POST("rest/v1/rpc/get_user_ranks")
    suspend fun getUserRanks(
        @Body body: GetUserRanksRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<UserRanksDto>

    // Grant premium manual oleh admin (buat transaksi yang ga otomatis ke-proses
    // webhook). Return void di server, jadi Response<Unit> di sini.
    @POST("rest/v1/rpc/admin_grant_premium_manual")
    suspend fun adminGrantPremiumManual(
        @Body body: AdminGrantPremiumManualRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=minimal"
    ): retrofit2.Response<Unit>

    // Bikin invoice pembayaran QRIS Sakurupiah buat klaim premium yang udah
    // dibikin (create_premium_claim / create_self_premium_claim / create_giveaway_claim).
    // Ini Edge Function biasa (bukan RPC PostgREST), routing-nya beda tapi host
    // sama, jadi tetep dipanggil dari Retrofit instance yang sama.
    @POST("functions/v1/sakurupiah-create-invoice")
    suspend fun sakurupiahCreateInvoice(
        @Body body: SakurupiahCreateInvoiceRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): SakurupiahInvoiceResponse

    // Bikin invoice pembayaran QRIS Sakurupiah buat top-up Diamond (DM),
    // nominal bebas input user, rasio Rp4 = 1 DM. Beda dari premium, gak
    // ada claim yang dibikin duluan.
    @POST("functions/v1/sakurupiah-create-diamond-invoice")
    suspend fun sakurupiahCreateDiamondInvoice(
        @Body body: SakurupiahDiamondInvoiceRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): SakurupiahDiamondInvoiceResponse

    // Cek status top-up diamond tertentu (buat polling "udah dibayar belum"
    // selagi bottom sheet invoice kebuka).
    @GET("rest/v1/diamond_topups")
    suspend fun getDiamondTopupByRef(
        @Query("payment_ref") refQuery: String,
        @Query("select") select: String = "status,payment_status,diamond_amount,payment_method,manual_review_status",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<DiamondTopupStatusDto>

    // Data top-up Diamond publik (dari view diamond_topups_public), dipakai
    // buat gabungin ke leaderboard Top Supporter, DAN buat banner "Support baru
    // masuk!" (order desc biar item [0] selalu yang paling baru, sama kayak donations).
    @GET("rest/v1/diamond_topups_public")
    suspend fun getDiamondTopupsPublic(
        @Query("order") order: String = "credited_at.desc",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<DiamondTopupPublicDto>
    // log_watch_event di atas, biar gak ganggu skema/data lama). Dipanggil tiap 3 menit aktif
    // nonton, sama kayak sebelumnya, tapi sekarang checkpoint ke-2/3/4 juga beneran kehitung
    // (bukan cuma "retry sampai berhasil 1x" kayak mekanisme lama).
    @POST("rest/v1/rpc/log_watch_checkpoint")
    suspend fun insertWatchCheckpoint(
        @Body data: WatchCheckpointRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=minimal"
    ): retrofit2.Response<Unit>

    // Gacha karakter - potong DM, roll rarity, pilih karakter random, simpen ke
    // koleksi user. SEMUA logic (termasuk cek saldo cukup) jalan di server lewat
    // function gacha_roll (SECURITY DEFINER), jadi gak bisa dicurangin dari client.
    // "return=representation" wajib di sini karena gacha_roll itu function yang
    // ngembaliin jsonb (bukan void kayak give_diamond), beda dari RPC lain di atas.
    @POST("rest/v1/rpc/gacha_roll")
    suspend fun rollGacha(
        @Body body: GachaRollRequest = GachaRollRequest(),
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): GachaRollResult

    // Event gacha yg lagi aktif sekarang (kalau ada) - buat nampilin banner
    // "khusus anime X" di GachaScreen. List kosong = gak ada event.
    @GET("rest/v1/gacha_active_event")
    suspend fun getActiveGachaEvent(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<GachaEventDto>

    // Semua event (aktif/lewat/akan datang) - buat panel admin liat riwayat & jadwal
    @GET("rest/v1/gacha_events")
    suspend fun getAllGachaEvents(
        @Query("select") select: String = "id,title,anime_mal_id,anime_title,banner_image_url,starts_at,ends_at",
        @Query("order") order: String = "starts_at.desc",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<GachaEventDto>

    // Daftar anime (distinct dari tabel characters) buat dropdown pilihan
    // admin pas bikin event - jadi admin milih dari list, bukan ngetik id manual.
    @GET("rest/v1/gacha_anime_list")
    suspend fun getGachaAnimeList(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<GachaAnimeOptionDto>

    // Bikin event gacha baru - CUMA admin, validasi di server (function create_gacha_event)
    @POST("rest/v1/rpc/create_gacha_event")
    suspend fun createGachaEvent(
        @Body body: CreateGachaEventRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): CreateGachaEventResult

    // Hapus event gacha - CUMA admin
    @POST("rest/v1/rpc/delete_gacha_event")
    suspend fun deleteGachaEvent(
        @Body body: DeleteGachaEventRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): DeleteGachaEventResult

    // Quiz "Tebak Anime dari Poster" - dipanggil SEBELUM soal ditampilkan.
    // Ngecek wajib punya clan + motong jatah harian/Diamond di server lewat
    // function play_quiz_round (SECURITY DEFINER), lihat supabase/quiz_system.sql.
    @POST("rest/v1/rpc/play_quiz_round")
    suspend fun playQuizRound(
        @Body body: PlayQuizRoundRequest = PlayQuizRoundRequest(),
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): PlayQuizRoundResult

    // Quiz "Tebak Anime dari Poster" - dipanggil SETELAH user milih jawaban.
    // Ngasih XP ke diri sendiri + semua member clan lain lewat function
    // submit_quiz_answer (SECURITY DEFINER).
    @POST("rest/v1/rpc/submit_quiz_answer")
    suspend fun submitQuizAnswer(
        @Body body: SubmitQuizAnswerRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): SubmitQuizAnswerResult

    // Koleksi karakter user sendiri - RLS di tabel user_characters udah mastiin
    // cuma baris milik user yang login yang keliatan (auth.uid() = user_id),
    // jadi gak perlu filter user_id manual di sini.
    @GET("rest/v1/user_characters")
    suspend fun getUserCharacters(
        @Query("select") select: String = "count,obtained_at,last_obtained_at,characters(mal_id,name,image_url,anime_title,rarity)",
        @Query("order") order: String = "last_obtained_at.desc",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<UserCharacterEntry>

    // ─────────────── Trade kartu gacha antar user ───────────────

    // Bikin listing jual 1 kartu dari koleksi sendiri. Semua validasi (beneran
    // punya kartunya, harga minimum, max listing aktif) jalan di server lewat
    // function create_trade_listing (SECURITY DEFINER).
    @POST("rest/v1/rpc/create_trade_listing")
    suspend fun createTradeListing(
        @Body body: CreateTradeListingRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): CreateTradeListingResult

    // Batalin listing sendiri yang masih aktif.
    @POST("rest/v1/rpc/cancel_trade_listing")
    suspend fun cancelTradeListing(
        @Body body: TradeListingIdRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): CancelTradeListingResult

    // Beli 1 listing - potong DM buyer, kredit DM seller (dipotong fee 10%),
    // pindahin kepemilikan kartu. SEMUA logic atomic di server lewat function
    // buy_trade_listing, gak bisa dicurangin dari client.
    @POST("rest/v1/rpc/buy_trade_listing")
    suspend fun buyTradeListing(
        @Body body: TradeListingIdRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): BuyTradeListingResult

    // Browse pasar - semua listing aktif dari SEMUA user, udah di-join sama
    // data karakter + username seller lewat view trade_listings_market.
    @GET("rest/v1/trade_listings_market")
    suspend fun getTradeMarket(
        @Query("select") select: String = "*",
        @Query("rarity") rarityFilter: String? = null,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<TradeMarketListing>

    // Listing milik user sendiri (aktif + history sold/cancelled) - RLS di
    // trade_listings udah mastiin cuma baris "status=active" ATAU
    // "seller_id=auth.uid()" yang keliatan, jadi query ini otomatis kefilter.
    @GET("rest/v1/trade_listings")
    suspend fun getMyTradeListings(
        @Query("seller_id") sellerIdQuery: String,
        @Query("select") select: String = "id,character_mal_id,price_dm,status,created_at,sold_at,characters(mal_id,name,image_url,anime_title,rarity)",
        @Query("order") order: String = "created_at.desc",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<MyTradeListing>


    @GET("rest/v1/chat_messages")
    suspend fun getChatMessages(
        @Query("order") order: String = "created_at.desc",
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
    ): retrofit2.Response<Unit>

    // Ambil reaction buat sekumpulan pesan sekaligus (batch, biar hemat request
    // - dipanggil bareng tiap loadChatMessages, bukan per-pesan).
    @GET("rest/v1/chat_reactions")
    suspend fun getChatReactions(
        @Query("message_id") messageIdQuery: String,
        @Query("select") select: String = "message_id,user_id,emoji",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ChatReaction>

    // Toggle reaction di 1 pesan Global Chat. Khusus Premium/Beta/Mod/Admin
    // (dicek server-side di function-nya).
    @POST("rest/v1/rpc/toggle_chat_reaction")
    suspend fun toggleChatReaction(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<String>

    // ─── Chat khusus clan ───
    @GET("rest/v1/clan_chat_messages")
    suspend fun getClanChatMessages(
        @Query("clan_id") clanIdQuery: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ClanChatMessage>

    @POST("rest/v1/clan_chat_messages")
    suspend fun insertClanChatMessage(
        @Body data: ClanChatMessageRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<ClanChatMessage>

    @DELETE("rest/v1/clan_chat_messages")
    suspend fun deleteClanChatMessage(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // Reaction versi Clan Chat - pola sama kayak Global.
    @GET("rest/v1/clan_chat_reactions")
    suspend fun getClanChatReactions(
        @Query("message_id") messageIdQuery: String,
        @Query("select") select: String = "message_id,user_id,emoji",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ChatReaction>

    @POST("rest/v1/rpc/toggle_clan_chat_reaction")
    suspend fun toggleClanChatReaction(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<String>

    // ─── Presence: total user online di seluruh aplikasi ───
    @GET("rest/v1/user_presence")
    suspend fun getOnlinePresence(
        @Query("last_seen") lastSeenFilter: String,
        @Query("select") select: String = "user_id",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<Map<String, String>>

    @POST("rest/v1/user_presence")
    suspend fun upsertPresence(
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal"
    ): retrofit2.Response<Unit>

    // ─── Push token (buat notif private chat targeted) ───
    @POST("rest/v1/push_tokens")
    suspend fun upsertPushToken(
        @Body data: PushTokenUpsertRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal"
    ): retrofit2.Response<Unit>

    // ─── Typing indicator chat room ───
    @GET("rest/v1/chat_typing")
    suspend fun getTypingUsers(
        @Query("updated_at") updatedAtFilter: String,
        @Query("select") select: String = "user_id,username,updated_at",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<TypingStatus>

    @POST("rest/v1/chat_typing")
    suspend fun upsertTyping(
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal"
    ): retrofit2.Response<Unit>

    @DELETE("rest/v1/chat_typing")
    suspend fun removeTyping(
        @Query("user_id") userId: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // ─── Read receipt chat room ───
    @GET("rest/v1/chat_reads")
    suspend fun getChatReads(
        @Query("select") select: String = "user_id,username,avatar_url,last_read_message_id,updated_at",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ChatReadStatus>

    @POST("rest/v1/chat_reads")
    suspend fun upsertChatRead(
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal"
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
        @Query("limit") limit: Int = 1000,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<Donation>

    @GET("rest/v1/donations")
    suspend fun getDonationsBySupporter(
        @Query("supporter_name") supporterNameQuery: String,
        @Query("limit") limit: Int = 1000,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<Donation>

    @GET("rest/v1/chat_messages")
    suspend fun getChatMessageIds(
        @Query("user_id") userIdQuery: String,
        @Query("select") select: String = "id",
        @Query("limit") limit: Int = 5000,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<IdOnlyDto>

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

    // ─── Requested Anime (anime yang di-request user, video di-upload manual) ───
    @GET("rest/v1/requested_anime")
    suspend fun getRequestedAnime(
        @Query("order") order: String = "created_at.desc",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<RequestedAnimeDto>

    @GET("rest/v1/requested_anime")
    suspend fun getRequestedAnimeById(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<RequestedAnimeDto>

    @POST("rest/v1/requested_anime")
    suspend fun insertRequestedAnime(
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<RequestedAnimeDto>

    @PATCH("rest/v1/requested_anime")
    suspend fun updateRequestedAnime(
        @Query("id") idQuery: String,
        @Body data: Map<String, @JvmSuppressWildcards Any?>,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<RequestedAnimeDto>

    @DELETE("rest/v1/requested_anime")
    suspend fun deleteRequestedAnime(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // ─── Komentar milik 1 user (buat tab "Komentar" di profil publik) ───
    @GET("rest/v1/episode_comments")
    suspend fun getUserComments(
        @Query("user_id") userIdQuery: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<EpisodeComment>

    // ─── Favorit (bookmark) — disinkron ke Supabase biar keliatan di profil publik ───
    @GET("rest/v1/user_bookmarks")
    suspend fun getUserBookmarks(
        @Query("user_id") userIdQuery: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<UserBookmarkDto>

    @POST("rest/v1/user_bookmarks")
    suspend fun upsertUserBookmark(
        @Body data: UserBookmarkRequest,
        @Query("on_conflict") onConflict: String = "user_id,anime_slug",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation"
    ): List<UserBookmarkDto>

    @DELETE("rest/v1/user_bookmarks")
    suspend fun deleteUserBookmark(
        @Query("user_id") userIdQuery: String,
        @Query("anime_slug") animeSlugQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // ─── Riwayat Tontonan — disinkron ke Supabase biar keliatan di profil publik ───
    @GET("rest/v1/user_watch_history")
    suspend fun getUserWatchHistory(
        @Query("user_id") userIdQuery: String,
        @Query("order") order: String = "watched_at.desc",
        @Query("limit") limit: Int = 50,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<UserWatchHistoryDto>

    @POST("rest/v1/user_watch_history")
    suspend fun upsertUserWatchHistory(
        @Body data: UserWatchHistoryRequest,
        @Query("on_conflict") onConflict: String = "user_id,episode_slug",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation"
    ): List<UserWatchHistoryDto>

    @DELETE("rest/v1/user_watch_history")
    suspend fun deleteAllUserWatchHistory(
        @Query("user_id") userIdQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // ─── Pertemanan (Add Teman) ───
    @GET("rest/v1/friendships")
    suspend fun getFriendships(
        @Query("or") orQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<FriendshipDto>

    @POST("rest/v1/friendships")
    suspend fun sendFriendRequest(
        @Body data: FriendshipRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<FriendshipDto>

    @PATCH("rest/v1/friendships")
    suspend fun updateFriendshipStatus(
        @Query("id") idQuery: String,
        @Body data: FriendshipStatusUpdate,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<FriendshipDto>

    @DELETE("rest/v1/friendships")
    suspend fun deleteFriendship(
        @Query("id") idQuery: String,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): retrofit2.Response<Unit>

    // ─── Badge Store (tag clan asli, auto-sync dari tabel clans) ─────
    // Katalog badge = daftar clan yg diikutin user, dikali beberapa varian skin
    @GET("rest/v1/clan_badge_catalog")
    suspend fun getClanBadgeCatalog(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<ClanBadgeCatalogDto>

    // Skin badge yg udah dimiliki user ini (RLS otomatis batasin ke user login)
    @GET("rest/v1/user_owned_clan_badges")
    suspend fun getMyOwnedClanBadges(
        @Query("select") select: String = "clan_id,skin_id,purchased_at",
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<OwnedClanBadgeDto>

    // Badge yg lagi dipakai tiap user, publik (buat ditampilin di chat/profil orang lain)
    @GET("rest/v1/equipped_badges_public")
    suspend fun getEquippedBadgesPublic(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String
    ): List<EquippedBadgePublicDto>

    // Beli badge tag-clan - potong Diamond, validasi semua di server (function buy_clan_badge)
    @POST("rest/v1/rpc/buy_clan_badge")
    suspend fun buyClanBadge(
        @Body body: BuyClanBadgeRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): BuyClanBadgeResult

    // Pakai/lepas badge tag-clan yg udah dimiliki (p_clan_id null = lepas)
    @POST("rest/v1/rpc/equip_clan_badge")
    suspend fun equipClanBadge(
        @Body body: EquipClanBadgeRequest,
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): EquipClanBadgeResult
}

// Aniku Store (aniku-store.my.id) -- Next.js, host TERPISAH dari Supabase.
// Cuma dipakai buat flow manual QRIS (bayar dari luar negeri): checkout bikin
// row pending di premium_claims/diamond_topups (payment_method='manual_qris'),
// upload bukti bayar disimpen di storage privat, admin approve manual lewat
// /admin/manual-payments di web. Polling status TETAP lewat supabaseDbApi
// (getPremiumClaimById / getDiamondTopupByRef) yang udah ada, gak lewat sini.
interface AnikuStoreApi {
    @POST("api/premium/manual-checkout")
    suspend fun premiumManualCheckout(@Body body: ManualPremiumCheckoutRequest): ManualCheckoutResponseDto

    @POST("api/diamond/manual-checkout")
    suspend fun diamondManualCheckout(@Body body: ManualDiamondCheckoutRequest): ManualCheckoutResponseDto

    // type: "premium" | "diamond". id: claim_id (premium) atau merchant_ref (diamond).
    @Multipart
    @POST("api/manual-proof")
    suspend fun uploadManualProof(
        @Part("type") type: RequestBody,
        @Part("id") id: RequestBody,
        @Part("note") note: RequestBody,
        @Part file: MultipartBody.Part
    ): ManualProofResponseDto
}

interface CloudinaryApi {
    @Multipart
    @POST("v1_1/biwlhrhi/image/upload")
    suspend fun uploadAvatar(
        @Part file: MultipartBody.Part,
        @Part("upload_preset") uploadPreset: RequestBody
    ): CloudinaryResponse

    // Upload video anime requestan, pakai preset khusus "anime_request_video"
    // (folder anime_requests, terpisah dari avatar/banner)
    @Multipart
    @POST("v1_1/biwlhrhi/video/upload")
    suspend fun uploadRequestedVideo(
        @Part file: MultipartBody.Part,
        @Part("upload_preset") uploadPreset: RequestBody
    ): CloudinaryResponse
}

// Jikan API (MyAnimeList, publik & gratis) — buat autofill poster/sinopsis/genre
// dari anime yang di-request user, tanpa perlu apikey.
interface JikanApi {
    @GET("v4/anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5
    ): JikanSearchResponse

    // Dipake buat quiz "Tebak Anime dari Poster" - ambil 1 anime random
    // (lengkap poster + judul) buat dijadiin soal.
    @GET("v4/random/anime")
    suspend fun randomAnime(): JikanRandomAnimeResponse
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
        // BODY logging buffer seluruh response ke memori jadi String -> OOM kalau response gede
        // (misal /profiles tanpa limit). Pakai BASIC di release, BODY cuma kalau debug build.
        level = if (com.example.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.BASIC
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

    fun animekompiApi(context: Context): AnimekompiApi =
        Retrofit.Builder()
            .baseUrl("https://www.sankavollerei.web.id/anime/animekompi/")
            .client(animeOkHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnimekompiApi::class.java)

    fun donghuaApi(context: Context): DonghuaApi =
        Retrofit.Builder()
            .baseUrl("https://www.sankavollerei.web.id/anime/")
            .client(animeOkHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DonghuaApi::class.java)

    fun animeinwebApi(context: Context): AnimeinwebApi =
        Retrofit.Builder()
            .baseUrl("https://animeinweb-api.vercel.app/api/")
            .client(animeOkHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnimeinwebApi::class.java)

    val supabaseAuthApi: SupabaseAuthApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://203-175-11-166.nip.io:8000/")
            .client(defaultOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseAuthApi::class.java)
    }

    val supabaseFunctionsApi: SupabaseFunctionsApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://203-175-11-166.nip.io:8000/")
            .client(defaultOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseFunctionsApi::class.java)
    }

    val supabaseDbApi: SupabaseDbApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://203-175-11-166.nip.io:8000/")
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

    val anikuStoreApi: AnikuStoreApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://aniku-store.my.id/")
            .client(defaultOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnikuStoreApi::class.java)
    }

    val jikanApi: JikanApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.jikan.moe/")
            .client(defaultOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JikanApi::class.java)
    }
}


// ─── Cara update AnikuViewModel.kt ───────────────────────────────────────────
// Tambahkan 2 baris ini di dalam class AnikuViewModel, setelah baris appContext:
//
//   private val animeApi by lazy { NetworkClient.animeApi(appContext) }
//   private val samehadakuApi by lazy { NetworkClient.samehadakuApi(appContext) }
//   private val animekompiApi by lazy { NetworkClient.animekompiApi(appContext) }
//   private val donghuaApi by lazy { NetworkClient.donghuaApi(appContext) }
//
// Lalu ganti semua:
//   NetworkClient.animeApi.xxx  →  animeApi.xxx
//   NetworkClient.samehadakuApi.xxx  →  samehadakuApi.xxx
//   NetworkClient.animekompiApi.xxx  →  animekompiApi.xxx
//   NetworkClient.donghuaApi.xxx  →  donghuaApi.xxx
//
// NetworkClient.supabaseDbApi dan NetworkClient.supabaseAuthApi tidak perlu diubah.
