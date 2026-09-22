package com.example.model

import androidx.annotation.DrawableRes
import com.example.R

data class SearchEngineInfo(
    val id: String,
    val name: String,
    val shortName: String,
    val category: String,
    @DrawableRes val iconRes: Int,
    val searchUrlTemplate: String
) {
    fun buildSearchUrl(query: String): String {
        val encoded = try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }
        return searchUrlTemplate.replace("{query}", encoded)
    }
}

object SearchEngines {
    val ALL: List<SearchEngineInfo> = listOf(
        SearchEngineInfo(
            id = "google",
            name = "谷歌 (Google)",
            shortName = "谷歌",
            category = "综合搜索",
            iconRes = R.drawable.ic_engine_google,
            searchUrlTemplate = "https://www.google.com/search?q={query}&cs=0"
        ),
        SearchEngineInfo(
            id = "baidu",
            name = "百度 (Baidu)",
            shortName = "百度",
            category = "综合搜索",
            iconRes = R.drawable.ic_engine_baidu,
            searchUrlTemplate = "https://www.baidu.com/s?wd={query}"
        ),
        SearchEngineInfo(
            id = "bing",
            name = "必应 (Bing)",
            shortName = "必应",
            category = "综合搜索",
            iconRes = R.drawable.ic_engine_bing,
            searchUrlTemplate = "https://www.bing.com/search?q={query}"
        ),
        SearchEngineInfo(
            id = "360",
            name = "360 搜索",
            shortName = "360",
            category = "综合搜索",
            iconRes = R.drawable.ic_engine_360,
            searchUrlTemplate = "https://www.so.com/s?q={query}"
        ),
        SearchEngineInfo(
            id = "sogou",
            name = "搜狗搜索",
            shortName = "搜狗",
            category = "综合搜索",
            iconRes = R.drawable.ic_engine_sogou,
            searchUrlTemplate = "https://www.sogou.com/web?query={query}"
        ),
        SearchEngineInfo(
            id = "quark",
            name = "夸克搜索 (Quark)",
            shortName = "夸克",
            category = "移动极速",
            iconRes = R.drawable.ic_engine_quark,
            searchUrlTemplate = "https://quark.sm.cn/s?q={query}"
        ),
        SearchEngineInfo(
            id = "toutiao",
            name = "今日头条 (Toutiao)",
            shortName = "头条",
            category = "资讯发现",
            iconRes = R.drawable.ic_engine_toutiao,
            searchUrlTemplate = "https://so.toutiao.com/search?keyword={query}"
        ),
        SearchEngineInfo(
            id = "duckduckgo",
            name = "DuckDuckGo (隐私)",
            shortName = "DDG",
            category = "隐私安全",
            iconRes = R.drawable.ic_engine_duckduckgo,
            searchUrlTemplate = "https://duckduckgo.com/?q={query}"
        ),
        SearchEngineInfo(
            id = "zhihu",
            name = "知乎搜索 (Zhihu)",
            shortName = "知乎",
            category = "知识问答",
            iconRes = R.drawable.ic_engine_zhihu,
            searchUrlTemplate = "https://www.zhihu.com/search?type=content&q={query}"
        ),
        SearchEngineInfo(
            id = "bilibili",
            name = "哔哩哔哩 (Bilibili)",
            shortName = "B站",
            category = "视频影音",
            iconRes = R.drawable.ic_engine_bilibili,
            searchUrlTemplate = "https://search.bilibili.com/all?keyword={query}"
        ),
        SearchEngineInfo(
            id = "github",
            name = "GitHub (开源社区)",
            shortName = "GitHub",
            category = "开发社区",
            iconRes = R.drawable.ic_engine_github,
            searchUrlTemplate = "https://github.com/search?q={query}"
        ),
        SearchEngineInfo(
            id = "yandex",
            name = "Yandex (国际搜索)",
            shortName = "Yandex",
            category = "国际搜索",
            iconRes = R.drawable.ic_engine_yandex,
            searchUrlTemplate = "https://yandex.com/search/?text={query}"
        )
    )

    fun getById(id: String): SearchEngineInfo {
        return ALL.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ALL.first()
    }
}
