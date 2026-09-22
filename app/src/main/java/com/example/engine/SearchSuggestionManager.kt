package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object SearchSuggestionManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    // Smart popular keywords for instantaneous local suggestion response
    private val commonKeywords = listOf(
        "google ai studio", "github", "github copilot", "github desktop", "github trending",
        "youtube", "youtube music", "youtube studio", "youtube download",
        "tiktok", "tiktok web", "tiktok download",
        "instagram", "instagram login", "instagram web",
        "bilibili", "baidu", "zhihu", "weibo", "wikipedia",
        "android studio", "jetpack compose", "kotlin", "python", "chatgpt", "gemini"
    )

    suspend fun fetchSuggestions(
        query: String,
        searchEngine: String = "google",
        localHistory: List<String> = emptyList()
    ): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val results = linkedSetOf<String>()

        // 1. First add any local history queries that match (ignoring case)
        localHistory.filter { it.contains(trimmed, ignoreCase = true) }
            .take(3)
            .forEach { results.add(it) }

        // 2. Fetch from Google / Baidu suggest APIs
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = if (searchEngine == "baidu") {
                "https://suggestion.baidu.com/su?wd=$encoded&action=opensearch"
            } else {
                "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val array = JSONArray(body)
                    if (array.length() >= 2) {
                        val suggestions = array.getJSONArray(1)
                        for (i in 0 until suggestions.length()) {
                            val item = suggestions.optString(i)
                            if (item.isNotBlank()) {
                                results.add(item)
                            }
                            if (results.size >= 10) break
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Network fallback
        }

        // 3. Fallback matching from common keywords if needed
        if (results.size < 6) {
            commonKeywords.filter { it.contains(trimmed, ignoreCase = true) && !results.contains(it) }
                .take(6 - results.size)
                .forEach { results.add(it) }
        }

        results.toList()
    }
}
