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
data class SignInRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class RecoverRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    val refresh_token: String
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
    val is_admin: Boolean? = false,
    val is_banned: Boolean? = false,
    val created_at: String? = null
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

// Cloudinary models
@JsonClass(generateAdapter = true)
data class CloudinaryResponse(
    val secure_url: String
)

// Chat Room models
@JsonClass(generateAdapter = true)
data class ChatMessage(
    val id: String,
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val is_admin: Boolean? = false,
    val message: String,
    val created_at: String,
    val reply_to_id: String? = null,
    val reply_to_username: String? = null,
    val reply_to_message: String? = null,
    val image_url: String? = null
)

@JsonClass(generateAdapter = true)
data class ChatMessageRequest(
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val is_admin: Boolean? = false,
    val message: String,
    val reply_to_id: String? = null,
    val reply_to_username: String? = null,
    val reply_to_message: String? = null,
    val image_url: String? = null
)

// Referensi anime yang dibagikan ke feed (dipakai di CreatePostScreen)
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
