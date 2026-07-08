package com.example.network

import com.squareup.moshi.JsonClass

// Anime API models
@JsonClass(generateAdapter = true)
data class AnimeRaw(
    val title: String,
    val slug: String,
    val poster: String,
    val episode: String? = null,
    val status_or_day: String? = null,
    val type: String? = null,
    val genres: List<String>? = null,
    val release: String? = null,
    val status: String? = null,
    val episode_count: String? = null,
    val score: String? = null,
    val estimation: String? = null
)

@JsonClass(generateAdapter = true)
data class HomeResponse(
    val status: String,
    val ongoing: List<AnimeRaw>?,
    val recent: List<AnimeRaw>?
)

@JsonClass(generateAdapter = true)
data class AnimesListResponse(
    val status: String,
    val animes: List<AnimeRaw>?,
    val pagination: Pagination?
)

@JsonClass(generateAdapter = true)
data class Pagination(
    val hasNext: Boolean?,
    val hasPrev: Boolean?,
    val currentPage: Int?
)

@JsonClass(generateAdapter = true)
data class GenreRaw(
    val name: String,
    val slug: String
)

@JsonClass(generateAdapter = true)
data class GenreListResponse(
    val status: String,
    val genres: List<GenreRaw>?
)

@JsonClass(generateAdapter = true)
data class ScheduleRaw(
    val minggu: List<AnimeRaw>?,
    val senin: List<AnimeRaw>?,
    val selasa: List<AnimeRaw>?,
    val rabu: List<AnimeRaw>?,
    val kamis: List<AnimeRaw>?,
    val jumat: List<AnimeRaw>? = null, // API might use jum'at, let's handle both
    @com.squareup.moshi.Json(name = "jum'at") val jumatAlt: List<AnimeRaw>? = null,
    val sabtu: List<AnimeRaw>?
)

@JsonClass(generateAdapter = true)
data class ScheduleResponse(
    val status: String,
    val schedule: ScheduleRaw?
)

@JsonClass(generateAdapter = true)
data class DetailGenreRaw(
    val name: String,
    val slug: String
)

@JsonClass(generateAdapter = true)
data class DetailEpisodeRaw(
    val name: String,
    val slug: String
)

@JsonClass(generateAdapter = true)
data class DetailCharacterRaw(
    val name: String,
    val slug: String
)

@JsonClass(generateAdapter = true)
data class DetailData(
    val title: String,
    val synonym: String?,
    val poster: String,
    val rating: String?,
    val synopsis: String?,
    val trailer: String?,
    val genres: List<DetailGenreRaw>?,
    val status: String?,
    val aired: String?,
    val type: String?,
    val duration: String?,
    val author: String?,
    val studio: String?,
    val season: String?,
    val episodes: List<DetailEpisodeRaw>?,
    val characters: List<DetailCharacterRaw>?
)

@JsonClass(generateAdapter = true)
data class DetailResponse(
    val status: String,
    val detail: DetailData?
)

@JsonClass(generateAdapter = true)
data class StreamRaw(
    val name: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class EpisodeResponse(
    val status: String,
    val title: String?,
    val streams: List<StreamRaw>?
)

// Supabase Auth models
@JsonClass(generateAdapter = true)
data class SignUpRequest(
    val email: String,
    val password: String,
    val data: SignUpData
)

@JsonClass(generateAdapter = true)
data class SignUpData(
    val username: String
)

@JsonClass(generateAdapter = true)
data class IpGuardRequest(
    val user_id: String,
    val email: String
)

@JsonClass(generateAdapter = true)
data class IpGuardResponse(
    val banned: Boolean?,
    val reason: String?,
    val error: String?
)

@JsonClass(generateAdapter = true)
data class SignInRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class RecoverRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class UpdatePasswordRequest(
    val password: String
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    val refresh_token: String
)

@JsonClass(generateAdapter = true)
data class IdTokenSignInRequest(
    val provider: String = "google",
    val id_token: String
)

// Use Map in AuthResponse since metadata is dynamic
@JsonClass(generateAdapter = true)
data class AuthResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val expires_in: Long? = null,
    val user: AuthUser? = null,
    val refresh_token: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: String,
    val email: String?,
    val user_metadata: Map<String, Any>?
)

// Supabase Database objects
@JsonClass(generateAdapter = true)
data class ProfileDto(
    val id: String,
    val username: String? = null,
    val avatar_url: String? = null,
    val banner_url: String? = null,
    val role: String? = "user",
    val is_admin: Boolean? = false,
    val is_banned: Boolean? = false,
    val user_number: Int? = null,
    val created_at: String? = null,
    val season_xp: Int? = 0,
    val season_level: Int? = 1,
    val diamond_balance: Int? = 0
) {
    fun isAdmin() = role == "admin" || is_admin == true
    fun isModerator() = role == "moderator"
    fun roleLabel() = when (role) {
        "admin" -> "Admin"
        "moderator" -> "Moderator"
        else -> "Pengguna"
    }
}

@JsonClass(generateAdapter = true)
data class ClanDto(
    val id: String,
    val name: String,
    val tag: String,
    val level: Int? = 1,
    val total_xp: Int? = 0,
    val leader_id: String? = null,
    val icon_url: String? = null,
    val is_private: Boolean? = false,
    val created_at: String? = null
)

@JsonClass(generateAdapter = true)
data class ClanJoinRequestDto(
    val id: String,
    val clan_id: String,
    val user_id: String,
    val status: String? = "pending",
    val requested_at: String? = null,
    // Join manual dari profiles pas ditampilkan di UI
    val username: String? = null,
    val avatar_url: String? = null
)

@JsonClass(generateAdapter = true)
data class ClanMemberDto(
    val id: String,
    val clan_id: String,
    val user_id: String,
    val role: String? = "member",
    val contributed_xp: Int? = 0,
    val joined_at: String? = null,
    // Join manual dari profiles pas ditampilkan di UI, bukan dari kolom DB
    val username: String? = null,
    val avatar_url: String? = null
)

@JsonClass(generateAdapter = true)
data class AnnouncementDto(
    val id: String,
    val title: String,
    val message: String,
    val is_active: Boolean? = true,
    val created_at: String? = null,
    val download_url: String? = null
)

@JsonClass(generateAdapter = true)
data class FeaturedAnimeDto(
    val id: String,
    val anime_slug: String,
    val anime_title: String? = null,
    val anime_poster: String? = null,
    val order_index: Int? = 0,
    val created_at: String? = null
)

@JsonClass(generateAdapter = true)
data class BlacklistedAnimeDto(
    val id: String,
    val anime_slug: String,
    val anime_title: String? = null,
    val reason: String? = null,
    val created_at: String? = null
)

// Genre yang disembunyikan dari daftar pilihan genre di Eksplor
@JsonClass(generateAdapter = true)
data class BlacklistedGenreDto(
    val id: String,
    val genre_slug: String,
    val genre_name: String? = null,
    val created_at: String? = null
)

// Cloudinary models
@JsonClass(generateAdapter = true)
data class CloudinaryResponse(
    val secure_url: String
)

// Requested Anime models (anime request khusus, video di-upload manual ke Cloudinary,
// metadata poster/sinopsis/genre diambil dari Jikan API)
@JsonClass(generateAdapter = true)
data class RequestedAnimeDto(
    val id: String,
    val mal_id: Int? = null,
    val title: String,
    val poster_url: String? = null,
    val synopsis: String? = null,
    val genres: String? = null, // disimpan dipisah koma, mis. "Comedy,Romance"
    val studio: String? = null,
    val rating: String? = null, // skor MAL, mis. "6.88"
    val anime_status: String? = null, // Ongoing/Completed, beda kolom dari `status` request
    val episode: String? = null,
    val video_url: String,
    val status: String? = "pending", // status moderasi request: pending/approved/rejected
    val created_at: String? = null
)

// Jikan API (MyAnimeList) models — dipakai buat autofill metadata anime requestan
@JsonClass(generateAdapter = true)
data class JikanSearchResponse(
    val data: List<JikanAnimeData>?
)

@JsonClass(generateAdapter = true)
data class JikanAnimeData(
    val mal_id: Int,
    val title: String,
    val synopsis: String? = null,
    val score: Double? = null,
    val status: String? = null,
    val images: JikanImages? = null,
    val genres: List<JikanGenre>? = null,
    val studios: List<JikanGenre>? = null
)

@JsonClass(generateAdapter = true)
data class JikanImages(
    val jpg: JikanImageUrl? = null
)

@JsonClass(generateAdapter = true)
data class JikanImageUrl(
    val image_url: String? = null,
    val large_image_url: String? = null
)

@JsonClass(generateAdapter = true)
data class JikanGenre(
    val mal_id: Int? = null,
    val name: String? = null
)

// Chat Room models
@JsonClass(generateAdapter = true)
data class ChatMessage(
    val id: String,
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val role: String? = "user",
    val is_admin: Boolean? = false,
    val user_number: Int? = null,
    val season_level: Int? = null,
    val message: String,
    val created_at: String,
    val reply_to_id: String? = null,
    val reply_to_username: String? = null,
    val reply_to_message: String? = null,
    val image_url: String? = null
)

@JsonClass(generateAdapter = true)
data class ProfileNumberDto(
    val user_number: Int? = null
)

@JsonClass(generateAdapter = true)
data class ChatMessageWithProfile(
    val id: String,
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val role: String? = "user",
    val is_admin: Boolean? = false,
    val message: String,
    val created_at: String,
    val reply_to_id: String? = null,
    val reply_to_username: String? = null,
    val reply_to_message: String? = null,
    val image_url: String? = null,
    val profiles: ProfileNumberDto? = null
) {
    fun toChatMessage() = ChatMessage(
        id = id,
        user_id = user_id,
        username = username,
        avatar_url = avatar_url,
        role = role,
        is_admin = is_admin,
        user_number = profiles?.user_number,
        message = message,
        created_at = created_at,
        reply_to_id = reply_to_id,
        reply_to_username = reply_to_username,
        reply_to_message = reply_to_message,
        image_url = image_url
    )
}

@JsonClass(generateAdapter = true)
data class IdOnlyDto(
    val id: String
)

@JsonClass(generateAdapter = true)
data class WatchEventRequest(
    val user_id: String,
    val anime_slug: String?,
    val episode_slug: String
)

@JsonClass(generateAdapter = true)
data class ChatMessageRequest(
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val role: String? = "user",
    val is_admin: Boolean? = false,
    val user_number: Int? = null,
    val message: String,
    val reply_to_id: String? = null,
    val reply_to_username: String? = null,
    val reply_to_message: String? = null,
    val image_url: String? = null
)

// Referensi anime yang dibagikan ke feed (dipakai di CreatePostScreen)
@JsonClass(generateAdapter = true)
data class SharedAnimeRef(
    val slug: String,
    val title: String,
    val poster: String,
    val type: String? = null
)

// Feed / Post models
@JsonClass(generateAdapter = true)
data class Post(
    val id: String,
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val role: String? = "user",
    val is_admin: Boolean? = false,
    val caption: String? = null,
    val image_url: String? = null,
    val anime_slug: String? = null,
    val anime_title: String? = null,
    val anime_poster: String? = null,
    val anime_type: String? = null,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class PostRequest(
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val role: String? = "user",
    val is_admin: Boolean? = false,
    val caption: String? = null,
    val image_url: String? = null,
    val anime_slug: String? = null,
    val anime_title: String? = null,
    val anime_poster: String? = null,
    val anime_type: String? = null
)

@JsonClass(generateAdapter = true)
data class PostLike(
    val id: String,
    val post_id: String,
    val user_id: String
)

@JsonClass(generateAdapter = true)
data class PostLikeRequest(
    val post_id: String,
    val user_id: String
)

@JsonClass(generateAdapter = true)
data class PostComment(
    val id: String,
    val post_id: String,
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val message: String,
    val reply_to_id: String? = null,
    val reply_to_username: String? = null,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class PostCommentRequest(
    val post_id: String,
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val message: String,
    val reply_to_id: String? = null,
    val reply_to_username: String? = null
)

// ================================================================
// SAMEHADAKU MODELS
// ================================================================

@JsonClass(generateAdapter = true)
data class SamehadakuHomeResponse(
    val status: String,
    val data: SamehadakuHomeData?
)

@JsonClass(generateAdapter = true)
data class SamehadakuHomeData(
    val recent: SamehadakuAnimeSection?,
    val movie: SamehadakuAnimeSection?
)

@JsonClass(generateAdapter = true)
data class SamehadakuAnimeSection(
    val animeList: List<SamehadakuAnimeItem>?
)

@JsonClass(generateAdapter = true)
data class SamehadakuAnimeItem(
    val title: String,
    val poster: String,
    val animeId: String,
    val episodes: String? = null,
    val releasedOn: String? = null,
    val releaseDate: String? = null,
    val type: String? = null,
    val score: String? = null,
    val status: String? = null,
    val genreList: List<SamehadakuGenreItem>? = null
) {
    fun toAnimeRaw() = AnimeRaw(
        title = title,
        slug = animeId,
        poster = poster,
        episode = episodes,
        type = type,
        score = score,
        status = status,
        release = releaseDate ?: releasedOn,
        genres = genreList?.map { it.title }
    )
}

@JsonClass(generateAdapter = true)
data class SamehadakuListResponse(
    val status: String,
    val data: SamehadakuListData?,
    val pagination: SamehadakuPagination?
)

@JsonClass(generateAdapter = true)
data class SamehadakuListData(
    val animeList: List<SamehadakuAnimeItem>?
)

@JsonClass(generateAdapter = true)
data class SamehadakuPagination(
    val currentPage: Int?,
    val hasNextPage: Boolean?,
    val hasPrevPage: Boolean?,
    val nextPage: Int?,
    val prevPage: Int?,
    val totalPages: Int?
)

@JsonClass(generateAdapter = true)
data class SamehadakuGenresResponse(
    val status: String,
    val data: SamehadakuGenresData?
)

@JsonClass(generateAdapter = true)
data class SamehadakuGenresData(
    val genreList: List<SamehadakuGenreItem>?
)

@JsonClass(generateAdapter = true)
data class SamehadakuGenreItem(
    val title: String,
    val genreId: String
) {
    fun toGenreRaw() = GenreRaw(name = title, slug = genreId)
}

@JsonClass(generateAdapter = true)
data class SamehadakuScheduleResponse(
    val status: String,
    val data: SamehadakuScheduleData?
)

@JsonClass(generateAdapter = true)
data class SamehadakuScheduleData(
    val days: List<SamehadakuScheduleDay>?
)

@JsonClass(generateAdapter = true)
data class SamehadakuScheduleDay(
    val day: String,
    val animeList: List<SamehadakuScheduleAnimeItem>?
)

@JsonClass(generateAdapter = true)
data class SamehadakuScheduleAnimeItem(
    val title: String,
    val poster: String,
    val animeId: String,
    val type: String? = null,
    val score: String? = null,
    val estimation: String? = null,
    val genres: String? = null
) {
    fun toAnimeRaw() = AnimeRaw(
        title = title,
        slug = animeId,
        poster = poster,
        type = type,
        score = score,
        estimation = estimation,
        genres = genres?.split(", ")
    )
}

@JsonClass(generateAdapter = true)
data class SamehadakuDetailResponse(
    val status: String,
    val data: SamehadakuDetailData?
)

@JsonClass(generateAdapter = true)
data class SamehadakuDetailData(
    val title: String,
    val poster: String,
    val score: SamehadakuScore? = null,
    val japanese: String? = null,
    val synonyms: String? = null,
    val english: String? = null,
    val status: String? = null,
    val type: String? = null,
    val source: String? = null,
    val duration: String? = null,
    val episodes: String? = null,
    val season: String? = null,
    val studios: String? = null,
    val producers: String? = null,
    val aired: String? = null,
    val trailer: String? = null,
    val synopsis: SamehadakuSynopsis? = null,
    val genreList: List<SamehadakuGenreItem>? = null,
    val episodeList: List<SamehadakuEpisodeItem>? = null,
    val recommendedAnimeList: List<SamehadakuAnimeItem>? = null
) {
    fun toDetailData() = DetailData(
        title = title.ifBlank { english ?: japanese ?: synonyms ?: "" },
        poster = poster,
        synonym = synonyms,
        rating = score?.value,
        synopsis = synopsis?.paragraphs?.joinToString("\n\n"),
        trailer = trailer,
        genres = genreList?.map { DetailGenreRaw(it.title, it.genreId) },
        status = status,
        aired = aired,
        type = type,
        duration = duration,
        author = null,
        studio = studios,
        season = season,
        episodes = episodeList?.map { it.toDetailEpisodeRaw() },
        characters = null
    )
}

@JsonClass(generateAdapter = true)
data class SamehadakuScore(
    val value: String?,
    val users: String?
)

@JsonClass(generateAdapter = true)
data class SamehadakuSynopsis(
    val paragraphs: List<String>?
)

@JsonClass(generateAdapter = true)
data class SamehadakuEpisodeItem(
    val title: String? = null,
    val episodeId: String
) {
    fun toDetailEpisodeRaw() = DetailEpisodeRaw(
        name = if (title != null) "Episode $title" else episodeId,
        slug = episodeId
    )
}

@JsonClass(generateAdapter = true)
data class SamehadakuEpisodeResponse(
    val status: String,
    val data: SamehadakuEpisodeData?
)

@JsonClass(generateAdapter = true)
data class SamehadakuEpisodeData(
    val title: String? = null,
    val animeId: String? = null,
    val poster: String? = null,
    val defaultStreamingUrl: String? = null,
    val hasPrevEpisode: Boolean? = null,
    val prevEpisode: SamehadakuEpisodeNav? = null,
    val hasNextEpisode: Boolean? = null,
    val nextEpisode: SamehadakuEpisodeNav? = null,
    val server: SamehadakuServer? = null
)

@JsonClass(generateAdapter = true)
data class SamehadakuEpisodeNav(
    val title: String? = null,
    val episodeId: String? = null
)

@JsonClass(generateAdapter = true)
data class SamehadakuServer(
    val qualities: List<SamehadakuQuality>?
)

@JsonClass(generateAdapter = true)
data class SamehadakuQuality(
    val title: String,
    val serverList: List<SamehadakuServerItem>?
)

@JsonClass(generateAdapter = true)
data class SamehadakuServerItem(
    val title: String,
    val serverId: String
)

@JsonClass(generateAdapter = true)
data class SamehadakuServerLinkResponse(
    val status: String,
    val data: SamehadakuServerLinkData?
)

@JsonClass(generateAdapter = true)
data class SamehadakuServerLinkData(
    val url: String?
)

// ================================================================
// ANIMEKOMPI MODELS (Dayynime-v3)
// Sumber: https://www.sankavollerei.web.id/anime/animekompi/
// ================================================================

@JsonClass(generateAdapter = true)
data class AnimekompiPagination(
    val has_next: Boolean? = null,
    val prev_page: Int? = null,
    val current_page: Int? = null,
    val next_page: Int? = null,
    val max_page: Int? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiItem(
    val title: String? = null,
    val slug: String? = null,
    val poster: String? = null,
    val image: String? = null,
    val episode: String? = null,
    val type: String? = null,
    val rating: String? = null,
    val status: String? = null,
    val date: String? = null,
    val time: String? = null,
    val tooltip_id: String? = null,
    val detail_slug: String? = null
) {
    // Endpoint home/terbaru/search/filter Animekompi ngebalikin slug *episode*,
    // bukan slug anime. Contoh: "one-piece-episode-1168-subtitle-indonesia" -> "one-piece"
    private fun animeSlugFromEpSlug(epSlug: String): String {
        val match = Regex("^(.+?)-episode-\\d").find(epSlug)
        return match?.groupValues?.get(1) ?: epSlug.removeSuffix("-subtitle-indonesia")
    }

    fun toAnimeRaw(): AnimeRaw {
        val rawSlug = (slug ?: "").trim()
        val animeSlug = detail_slug?.takeIf { it.isNotBlank() } ?: animeSlugFromEpSlug(rawSlug)
        return AnimeRaw(
            title = title ?: "Unknown",
            slug = animeSlug,
            poster = poster ?: image ?: "",
            episode = episode,
            type = type,
            score = rating,
            status = status,
            release = date,
            genres = null,
            estimation = time
        )
    }
}

@JsonClass(generateAdapter = true)
data class AnimekompiHomeResponse(
    val data: List<AnimekompiItem>? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiListResponse(
    val data: List<AnimekompiItem>? = null,
    val pagination: AnimekompiPagination? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiGenreItem(
    val name: String? = null,
    val value: String? = null
) {
    fun toGenreRaw() = GenreRaw(name = name ?: "", slug = value ?: "")
}

@JsonClass(generateAdapter = true)
data class AnimekompiGenresResponse(
    val data: List<AnimekompiGenreItem>? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiScheduleItem(
    val title: String? = null,
    val slug: String? = null,
    val poster: String? = null,
    val episode: String? = null,
    val time: String? = null
) {
    fun toAnimeRaw() = AnimeRaw(
        title = title ?: "Unknown",
        slug = slug ?: "",
        poster = poster ?: "",
        episode = episode,
        estimation = time
    )
}

@JsonClass(generateAdapter = true)
data class AnimekompiScheduleDay(
    val day: String? = null,
    val list: List<AnimekompiScheduleItem>? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiScheduleResponse(
    val data: List<AnimekompiScheduleDay>? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiDetailGenre(
    val name: String? = null,
    val slug: String? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiDetailEpisode(
    val title: String? = null,
    val num: String? = null,
    val slug: String? = null
) {
    fun toDetailEpisodeRaw() = DetailEpisodeRaw(
        name = title?.takeIf { it.isNotBlank() } ?: num?.let { "Episode $it" } ?: (slug ?: ""),
        slug = slug ?: ""
    )
}

@JsonClass(generateAdapter = true)
data class AnimekompiMetadata(
    val tipe: String? = null,
    val status: String? = null,
    val dirilis: String? = null,
    val dirilis_2: String? = null,
    val durasi: String? = null,
    val studio: String? = null,
    val season: String? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiDetailData(
    val title: String? = null,
    val alter_title: String? = null,
    val image: String? = null,
    val rating: String? = null,
    val synopsis: String? = null,
    val metadata: AnimekompiMetadata? = null,
    val genres: List<AnimekompiDetailGenre>? = null,
    val episodes: List<AnimekompiDetailEpisode>? = null
) {
    fun toDetailData() = DetailData(
        title = title?.takeIf { it.isNotBlank() } ?: alter_title ?: "Unknown",
        synonym = alter_title,
        poster = image ?: "",
        rating = rating ?: "N/A",
        synopsis = synopsis,
        trailer = null,
        genres = genres?.map { DetailGenreRaw(it.name ?: "", it.slug ?: "") },
        status = metadata?.status,
        aired = metadata?.dirilis_2 ?: metadata?.dirilis,
        type = metadata?.tipe,
        duration = metadata?.durasi,
        author = null,
        studio = metadata?.studio,
        season = metadata?.season,
        episodes = episodes?.map { it.toDetailEpisodeRaw() },
        characters = null
    )
}

@JsonClass(generateAdapter = true)
data class AnimekompiDetailResponse(
    val data: AnimekompiDetailData? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiMirror(
    val name: String? = null,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiDownloadLink(
    val server: String? = null,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiDownloadItem(
    val format: String? = null,
    val resolution: String? = null,
    val links: List<AnimekompiDownloadLink>? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiEpisodeData(
    val title: String? = null,
    val detail_slug: String? = null,
    val prev_episode: String? = null,
    val next_episode: String? = null,
    val mirrors: List<AnimekompiMirror>? = null,
    val downloads: List<AnimekompiDownloadItem>? = null
)

@JsonClass(generateAdapter = true)
data class AnimekompiEpisodeResponse(
    val data: AnimekompiEpisodeData? = null
)

// Trakteer Donation
@JsonClass(generateAdapter = true)
data class Donation(
    val id: String,
    val supporter_name: String,
    val amount: Int,
    val unit: String? = "cup",
    val message: String? = null,
    val total_amount: Int? = 0,
    val created_at: String,
    val is_announced: Boolean? = false
)

// Watch live chat models
@JsonClass(generateAdapter = true)
data class WatchChatMessage(
    val id: String,
    val episode_slug: String,
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val message: String,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class WatchChatRequest(
    val episode_slug: String,
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val message: String
)

// Komentar episode (permanen, non-realtime) - beda dari watch_chat yang cuma sementara.
// user_number & season_level di-join manual dari profiles pas load, sama kayak pola ChatMessage.
// source: label sumber data saat komentar dikirim ("v1"/"v2"/"v3").
// anime_title/anime_slug: disimpen pas komentar dikirim biar widget "Komentar Terbaru" di Home
// bisa nampilin judul animenya (episode_slug doang gak cukup buat dapetin judul yang rapi).
// parent_comment_id/reply_to_username: buat fitur balas komentar — null berarti komentar utama (bukan balasan).
// Semua nullable biar backward-compatible sama baris lama / kalau kolomnya belum ditambahin di Supabase.
@JsonClass(generateAdapter = true)
data class EpisodeComment(
    val id: String,
    val episode_slug: String,
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val role: String? = "user",
    val is_admin: Boolean? = false,
    val message: String,
    val created_at: String,
    val user_number: Int? = null,
    val season_level: Int? = null,
    val source: String? = null,
    val anime_title: String? = null,
    val anime_slug: String? = null,
    val parent_comment_id: String? = null,
    val reply_to_username: String? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeCommentRequest(
    val episode_slug: String,
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val role: String? = "user",
    val is_admin: Boolean? = false,
    val message: String,
    val source: String? = null,
    val anime_title: String? = null,
    val anime_slug: String? = null,
    val parent_comment_id: String? = null,
    val reply_to_username: String? = null
)
