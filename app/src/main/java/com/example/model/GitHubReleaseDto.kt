package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @Json(name = "id") val id: Long?,
    @Json(name = "tag_name") val tagName: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "body") val body: String?,
    @Json(name = "published_at") val publishedAt: String?,
    @Json(name = "assets") val assets: List<GitHubAssetDto>?
)

@JsonClass(generateAdapter = true)
data class GitHubAssetDto(
    @Json(name = "id") val id: Long?,
    @Json(name = "name") val name: String?,
    @Json(name = "size") val size: Long?,
    @Json(name = "browser_download_url") val downloadUrl: String?,
    @Json(name = "content_type") val contentType: String?
)
