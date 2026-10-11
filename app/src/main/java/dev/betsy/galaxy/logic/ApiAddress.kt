package dev.betsy.galaxy.logic

import java.net.URI

object ApiAddress {
    fun normalize(raw: String): String {
        var value = raw.trim().trimEnd('/')
        require(value.isNotEmpty()) { "Enter the app API address." }
        if (!value.contains("://")) value = "http://$value"
        val uri = try {
            URI(value)
        } catch (error: Exception) {
            throw IllegalArgumentException("The app API address is not a valid URL.")
        }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "The address must start with http or https." }
        require(uri.userInfo.isNullOrEmpty()) { "Put the token in the token field, not in the URL." }
        require(!uri.host.isNullOrBlank()) { "The address needs a host." }
        var path = uri.rawPath.orEmpty().trimEnd('/')
        path = when {
            path.isEmpty() || path == "/" -> "/galaxy/api/app/v1"
            path.endsWith("/galaxy/api/app/v1") -> path
            path.endsWith("/galaxy") -> "$path/api/app/v1"
            path.endsWith("/galaxy/api") -> "$path/app/v1"
            path.endsWith("/galaxy/api/app") -> "$path/v1"
            else -> path
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "$scheme://${uri.host}$port$path"
    }
}
