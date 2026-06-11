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
    val episode_count: String? = null
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
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class ChatMessageRequest(
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val is_admin: Boolean? = false,
    val message: String
)
