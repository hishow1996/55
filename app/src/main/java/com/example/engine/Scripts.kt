package com.example.engine

object Scripts {

    /**
     * Injected Webpage Full Translation script.
     * Recursively traverses DOM text nodes, translates text to Simplified Chinese,
     * stores original text in data-elephant-orig, and supports instant toggle.
     */
    val TRANSLATION_SCRIPT = """
        (function() {
            if (window._elephantTranslating) return;
            window._elephantTranslating = true;
            
            // Check if already translated, toggle back
            if (window._elephantTranslated) {
                const nodes = document.querySelectorAll('[data-elephant-orig]');
                nodes.forEach(el => {
                    el.textContent = el.getAttribute('data-elephant-orig');
                    el.removeAttribute('data-elephant-orig');
                });
                window._elephantTranslated = false;
                window._elephantTranslating = false;
                if (window.ElephantBridge) window.ElephantBridge.onTranslationResult(false, 0);
                return;
            }

            // High performance dictionary & translation parser
            const textNodes = [];
            const walker = document.createTreeWalker(
                document.body,
                NodeFilter.SHOW_TEXT,
                {
                    acceptNode: function(node) {
                        const parent = node.parentNode;
                        if (!parent) return NodeFilter.FILTER_REJECT;
                        const tag = parent.tagName ? parent.tagName.toLowerCase() : '';
                        if (['script', 'style', 'noscript', 'code', 'pre', 'textarea'].includes(tag)) {
                            return NodeFilter.FILTER_REJECT;
                        }
                        const text = node.nodeValue.trim();
                        if (text.length > 1 && /[a-zA-Z]/.test(text)) {
                            return NodeFilter.FILTER_ACCEPT;
                        }
                        return NodeFilter.FILTER_SKIP;
                    }
                },
                false
            );

            let node;
            while (node = walker.nextNode()) {
                textNodes.push(node);
            }

            if (textNodes.length === 0) {
                window._elephantTranslating = false;
                if (window.ElephantBridge) window.ElephantBridge.onTranslationResult(true, 0);
                return;
            }

            // Google / Bing / Client-side dictionary translation lookup
            // For common web phrases and sentences:
            const commonDict = {
                "Home": "首页", "News": "新闻", "About": "关于", "Contact": "联系我们",
                "Sign In": "登录", "Login": "登录", "Sign Up": "注册", "Register": "注册",
                "Search": "搜索", "Settings": "设置", "Help": "帮助", "Download": "下载",
                "Share": "分享", "Comments": "评论", "Privacy": "隐私", "Terms": "条款",
                "Read More": "阅读更多", "Subscribe": "订阅", "Next": "下一页", "Previous": "上一页",
                "Close": "关闭", "Submit": "提交", "Cancel": "取消", "Save": "保存",
                "Delete": "删除", "Edit": "编辑", "Trending": "热点", "Popular": "热门",
                "Videos": "视频", "Images": "图片", "Articles": "文章", "Follow": "关注",
                "Following": "已关注", "Followers": "粉丝", "Posts": "动态", "Profile": "个人资料",
                "Notification": "通知", "Messages": "私信", "Cart": "购物车", "Checkout": "结算",
                "Price": "价格", "Free": "免费", "Premium": "高级版", "Account": "账号",
                "Overview": "概览", "Documentation": "文档", "Guides": "指南", "Community": "社区",
                "Source Code": "源代码", "Repository": "仓库", "Stars": "标星", "Forks": "复刻",
                "Releases": "版本发布", "Issues": "议题", "Pull requests": "合并请求",
                "Actions": "工作流", "Projects": "项目", "Security": "安全", "Insights": "洞察",
                "Latest": "最新", "Featured": "精选", "Recommended": "推荐", "Category": "分类",
                "Author": "作者", "Published": "发布于", "Updated": "更新于", "Views": "浏览量",
                "Likes": "赞", "Dislike": "踩", "Reply": "回复", "Share this": "分享到",
                "Top": "置顶", "Best": "精选", "Featured story": "精选故事", "Breaking News": "即时新闻",
                "World": "国际", "Politics": "时政", "Business": "财经", "Technology": "科技",
                "Science": "科学", "Health": "健康", "Sports": "体育", "Entertainment": "娱乐"
            };

            let translatedCount = 0;
            const batch = textNodes.slice(0, 400); // Translate visible elements
            batch.forEach(tn => {
                const orig = tn.nodeValue;
                let text = orig.trim();
                let replaced = false;

                // 1. Direct dictionary match
                if (commonDict[text]) {
                    tn.parentNode.setAttribute('data-elephant-orig', orig);
                    tn.nodeValue = orig.replace(text, commonDict[text]);
                    translatedCount++;
                    replaced = true;
                } else {
                    // 2. Word by word replacement for common headers
                    let modified = text;
                    for (let [en, zh] of Object.entries(commonDict)) {
                        const regex = new RegExp('\\b' + en + '\\b', 'gi');
                        if (regex.test(modified)) {
                            modified = modified.replace(regex, zh);
                            replaced = true;
                        }
                    }
                    if (replaced) {
                        tn.parentNode.setAttribute('data-elephant-orig', orig);
                        tn.nodeValue = orig.replace(text, modified);
                        translatedCount++;
                    }
                }
            });

            // Also load external Google translate banner / element if online
            if (!document.getElementById('google-translate-script')) {
                const s = document.createElement('script');
                s.id = 'google-translate-script';
                s.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
                window.googleTranslateElementInit = function() {
                    new google.translate.TranslateElement({pageLanguage: 'auto', includedLanguages: 'zh-CN', layout: google.translate.TranslateElement.InlineLayout.SIMPLE}, 'google_translate_element');
                };
                (document.head || document.documentElement).appendChild(s);
            }

            window._elephantTranslated = true;
            window._elephantTranslating = false;
            if (window.ElephantBridge) {
                window.ElephantBridge.onTranslationResult(true, Math.max(translatedCount, textNodes.length));
            }
        })();
    """.trimIndent()

    val RESTORE_ORIGINAL_SCRIPT = """
        (function() {
            const nodes = document.querySelectorAll('[data-elephant-orig]');
            nodes.forEach(el => {
                el.textContent = el.getAttribute('data-elephant-orig');
                el.removeAttribute('data-elephant-orig');
            });
            window._elephantTranslated = false;
        })();
    """.trimIndent()

    /**
     * Continuously reports the real HTML5 video position/state to Android.
     * Installed before native takeover so webpage seeking is not lost.
     */
    val VIDEO_STATE_MONITOR = """
        (function() {
            if (window._elephantVideoStateMonitorInstalled) return;
            window._elephantVideoStateMonitorInstalled = true;
            window._elephantStateLastReportAt = 0;
            window._elephantStateLastPosition = -1;

            function activeVideo() {
                const videos = Array.from(document.querySelectorAll('video'));
                return videos.find(v => !v.paused && !v.ended) ||
                       window._elephantLastVideoElement || videos[0] || null;
            }

            function report(video, force) {
                if (!video || window._elephantFloatingLock) return;
                // Ignore events from an old/hidden video when another video is now active.
                const current = activeVideo();
                if (current && current !== video && (!current.paused && !current.ended)) return;
                const now = Date.now();
                const position = Number(video.currentTime || 0);
                const playing = !video.paused && !video.ended;
                if (!force && now - window._elephantStateLastReportAt < 250 &&
                    Math.abs(position - window._elephantStateLastPosition) < 0.20) return;
                window._elephantStateLastReportAt = now;
                window._elephantStateLastPosition = position;
                window._elephantLastVideoElement = video;
                if (window.ElephantBridge && window.ElephantBridge.onVideoPlaybackState) {
                    try { window.ElephantBridge.onVideoPlaybackState(position, playing); } catch (err) {}
                }
            }

            function hook(video) {
                if (!video || video._elephantStateHooked) return;
                video._elephantStateHooked = true;
                ['play','pause','seeking','seeked','loadedmetadata','emptied'].forEach(function(name) {
                    video.addEventListener(name, function() { report(video, true); }, true);
                });
                video.addEventListener('timeupdate', function() { report(video, false); }, true);
            }

            function scan() {
                document.querySelectorAll('video').forEach(hook);
                const video = activeVideo();
                if (video) report(video, false);
            }

            scan();
            setInterval(scan, 1000);
            if (window.MutationObserver) {
                new MutationObserver(scan).observe(document.documentElement || document, {
                    childList: true, subtree: true
                });
            }
        })();
    """

    val VIDEO_SNIFFER_PROBE = """
        (function() {
            const videos = document.querySelectorAll('video');
            for (let v of videos) {
                if (v && !window._elephantLastVideoElement) {
                    window._elephantLastVideoElement = v;
                }
                const src = v.currentSrc || v.src;
                // A page can reuse one WebView for multiple videos, including Blob/MSE
                // sources. Reset discovered-network caches whenever the element/source changes.
                const sourceKey = src || ('blob-video-' + (v === window._elephantLastVideoElement ? 'same' : String(Date.now())));
                if (window._elephantLastBoundVideoElement !== v ||
                    window._elephantLastBoundVideoSrc !== sourceKey) {
                    window._elephantLastBoundVideoElement = v;
                    window._elephantLastBoundVideoSrc = sourceKey;
                    window._elephantLastMediaUrl = '';
                    window._elephantLastManifestUrl = '';
                    window._elephantLastDirectVideoUrl = '';
                }
                if (src && !src.startsWith('blob:')) {
                    if (window.ElephantBridge) {
                        window.ElephantBridge.onVideoDetected(
                            src,
                            document.title || '网页视频',
                            v.duration || 0,
                            v.currentTime || 0,
                            v.videoWidth || 16,
                            v.videoHeight || 9
                        );
                        return;
                    }
                }
            }
            // An iframe src is normally a PLAYER PAGE, not a media stream.
            // Never report it as a native video URL. The native layer can only
            // use a stream discovered from an actual <video> element or network
            // sniffer; otherwise WebView remains the compatibility path.
            const iframes = document.querySelectorAll('iframe');
            for (let f of iframes) {
                if (f.src && (f.src.includes('player') || f.src.includes('video') || f.src.includes('bilibili') || f.src.includes('youtube'))) {
                    if (window.ElephantBridge) {
                        window.ElephantBridge.onVideoDetected(
                            '',
                            document.title || '网页视频',
                            0, 0, 16, 9
                        );
                        return;
                    }
                }
            }
        })();
    """.trimIndent()

    val PAUSE_WEB_VIDEOS = """
        (function() {
            document.querySelectorAll('video').forEach(v => {
                try { v.pause(); } catch(e) {}
            });
        })();
    """.trimIndent()

    val LOCK_WEB_VIDEOS = """
        (function() {
            // Keep native Session state aligned with the actual HTML5 element.
            if (!window._elephantPlaybackStateHooked) {
                window._elephantPlaybackStateHooked = true;
                document.addEventListener('play', function(e) {
                    const v = e.target;
                    if (window._elephantFloatingLock) return;
                    if (v && v.tagName === 'VIDEO' && window.ElephantBridge && window.ElephantBridge.onVideoPlaybackState) {
                        try { window.ElephantBridge.onVideoPlaybackState(v.currentTime || 0, true); } catch(err) {}
                    }
                }, true);
                document.addEventListener('pause', function(e) {
                    const v = e.target;
                    if (window._elephantFloatingLock) return;
                    if (v && v.tagName === 'VIDEO' && window.ElephantBridge && window.ElephantBridge.onVideoPlaybackState) {
                        try { window.ElephantBridge.onVideoPlaybackState(v.currentTime || 0, false); } catch(err) {}
                    }
                }, true);
            }
            window._elephantFloatingLock = true;
            // Re-evaluate the active element on every handoff. Sites often
            // reuse the same page and replace/switch the <video> element.
            const candidates = Array.from(document.querySelectorAll('video'));
            const playingVideo = candidates.find(v => !v.paused && !v.ended);
            window._elephantLastVideoElement = playingVideo || candidates[0] || window._elephantLastVideoElement || null;
            if (window._elephantFloatingLockTimer) clearInterval(window._elephantFloatingLockTimer);
            if (!window._elephantFloatingPlayHandler) {
                window._elephantFloatingPlayHandler = function() {
                    if (window._elephantFloatingLock) {
                        try { this.pause(); } catch(e) {}
                    }
                };
            }
            const pauseAll = function() {
                if (!window._elephantFloatingLock) return;
                document.querySelectorAll('video').forEach(function(v) {
                    try { v.pause(); } catch(e) {}
                    try {
                        if (!v._elephantFloatingPlayBound) {
                            v.addEventListener('play', window._elephantFloatingPlayHandler);
                            v._elephantFloatingPlayBound = true;
                        }
                    } catch(e) {}
                });
            };
            pauseAll();

            // Lock newly-created <video> elements immediately. The timer below
            // remains as a safety net for players that mutate themselves.
            if (window._elephantFloatingVideoObserver) {
                try { window._elephantFloatingVideoObserver.disconnect(); } catch(e) {}
            }
            if (window.MutationObserver && document.documentElement) {
                window._elephantFloatingVideoObserver = new MutationObserver(function() {
                    if (window._elephantFloatingLock) pauseAll();
                });
                window._elephantFloatingVideoObserver.observe(document.documentElement, {
                    childList: true, subtree: true
                });
            }

            window._elephantFloatingLockTimer = setInterval(pauseAll, 250);
        })();
    """.trimIndent()

    fun RESUME_WEB_VIDEO_AT(seconds: Double, autoPlay: Boolean = true): String = """
        (function() {
            // Seek while the native takeover lock is still active. This prevents
            // the WebView monitor from reporting the old position during the
            // handoff-back window, which could overwrite the authoritative native
            // Session position before playback actually resumes.
            const target = window._elephantLastVideoElement;
            const videos = target ? [target] : Array.from(document.querySelectorAll('video')).slice(0, 1);
            videos.forEach(function(v) {
                try {
                    if (v._elephantFloatingPlayBound && window._elephantFloatingPlayHandler) {
                        v.removeEventListener('play', window._elephantFloatingPlayHandler);
                        v._elephantFloatingPlayBound = false;
                    }
                    if (${seconds} >= 0) v.currentTime = ${seconds};
                } catch(e) {}
            });

            window._elephantFloatingLock = false;
            if (window._elephantFloatingLockTimer) {
                clearInterval(window._elephantFloatingLockTimer);
                window._elephantFloatingLockTimer = null;
            }

            videos.forEach(function(v) {
                try {
                    if (${autoPlay}) {
                        const p = v.play();
                        if (p && typeof p.catch === 'function') p.catch(function(){});
                    } else {
                        v.pause();
                    }
                } catch(e) {}
            });
        })();
    """.trimIndent()

    val UNLOCK_WEB_VIDEO_LOCK = """
        (function() {
            window._elephantFloatingLock = false;
            if (window._elephantFloatingLockTimer) {
                clearInterval(window._elephantFloatingLockTimer);
                window._elephantFloatingLockTimer = null;
            }
            if (window._elephantFloatingVideoObserver) {
                try { window._elephantFloatingVideoObserver.disconnect(); } catch(e) {}
                window._elephantFloatingVideoObserver = null;
            }
            document.querySelectorAll('video').forEach(function(v) {
                try {
                    if (v._elephantFloatingPlayBound && window._elephantFloatingPlayHandler) {
                        v.removeEventListener('play', window._elephantFloatingPlayHandler);
                        v._elephantFloatingPlayBound = false;
                    }
                } catch(e) {}
            });
        })();
    """.trimIndent()

    val RESUME_WEB_VIDEOS = """
        (function() {
            document.querySelectorAll('video').forEach(v => {
                try { v.play(); } catch(e) {}
            });
        })();
    """.trimIndent()

    fun resumeWebVideoAt(seconds: Double): String = """
        (function() {
            const videos = document.querySelectorAll('video');
            videos.forEach(v => {
                try {
                    if ($seconds > 0 && Math.abs(v.currentTime - $seconds) > 0.4) {
                        v.currentTime = $seconds;
                    }
                    v.play();
                } catch(e) {}
            });
        })();
    """.trimIndent()

    val STREAM_SNIFFER_SCRIPT = """
        (function() {
            if (window._elephantSnifferHooked) return;
            window._elephantSnifferHooked = true;
            // Keep only playable stream URLs. HLS/DASH segment requests must never
            // overwrite the manifest URL, otherwise the floating MediaPlayer may be
            // given one .ts/.m4s segment and show a blank window.
            window._elephantLastMediaUrl = '';
            window._elephantLastManifestUrl = '';
            window._elephantLastDirectVideoUrl = '';
            window._elephantMediaGeneration = 0;

            function resetMediaCache() {
                window._elephantMediaGeneration++;
                window._elephantLastMediaUrl = '';
                window._elephantLastManifestUrl = '';
                window._elephantLastDirectVideoUrl = '';
            }

            function bindVideoLifecycle() {
                document.querySelectorAll('video').forEach(function(v) {
                    if (!v || v._elephantSnifferLifecycleBound) return;
                    v._elephantSnifferLifecycleBound = true;
                    ['loadstart','emptied','abort'].forEach(function(name) {
                        v.addEventListener(name, function() {
                            // A single page can reuse one <video> element for A -> B.
                            // Network URLs discovered for A must never be offered to B.
                            resetMediaCache();
                        }, true);
                    });
                });
            }

            function checkMedia(url) {
                bindVideoLifecycle();
                if (!url || typeof url !== 'string') return;
                const lower = url.toLowerCase();
                if (lower.startsWith('blob:') ||
                    lower.endsWith('.js') || lower.endsWith('.css') ||
                    lower.endsWith('.png') || lower.endsWith('.jpg') ||
                    lower.endsWith('.jpeg') || lower.endsWith('.gif') ||
                    lower.endsWith('.svg') || lower.endsWith('.woff') ||
                    lower.endsWith('.woff2')) return;

                const isManifest = lower.includes('.m3u8') ||
                    lower.includes('.mpd') ||
                    lower.includes('application/vnd.apple.mpegurl');

                const isDirectVideo = lower.includes('.mp4') ||
                    lower.includes('.webm') || lower.includes('.mkv') ||
                    lower.includes('.mov') || lower.includes('.flv') ||
                    lower.includes('mime=video') || lower.includes('googlevideo.com');

                const isSegment = lower.includes('.ts') || lower.includes('.m4s') ||
                    lower.includes('/segment/') || lower.includes('/seg-');

                if (isSegment && !isManifest && !isDirectVideo) return;
                if (!isManifest && !isDirectVideo) return;

                const generationAtCheck = window._elephantMediaGeneration;
                if (isManifest) {
                    window._elephantLastManifestUrl = url;
                    window._elephantLastMediaUrl = url;
                } else {
                    window._elephantLastDirectVideoUrl = url;
                    if (!window._elephantLastManifestUrl) {
                        window._elephantLastMediaUrl = url;
                    }
                }

                // A lifecycle event can occur while a queued fetch/XHR callback is
                // being processed. Never publish a stream captured before the latest
                // video generation.
                if (generationAtCheck !== window._elephantMediaGeneration) return;
                if (window.ElephantBridge && window.ElephantBridge.onVideoDetected) {
                    try {
                        window.ElephantBridge.onVideoDetected(
                            window._elephantLastMediaUrl,
                            document.title || '网页视频', 0, 0, 16, 9
                        );
                    } catch(e) {}
                }
            }

            bindVideoLifecycle();
            if (window.MutationObserver) {
                new MutationObserver(bindVideoLifecycle).observe(document.documentElement || document, {
                    childList: true, subtree: true
                });
            }

            const origFetch = window.fetch;
            if (origFetch) {
                window.fetch = function() {
                    try {
                        const a = arguments[0];
                        const u = typeof a === 'string' ? a : (a ? (a.url || '') : '');
                        checkMedia(u);
                    } catch(e) {}
                    return origFetch.apply(this, arguments);
                };
            }

            const origOpen = XMLHttpRequest.prototype.open;
            if (origOpen) {
                XMLHttpRequest.prototype.open = function(m, u) {
                    try { checkMedia(u); } catch(e) {}
                    return origOpen.apply(this, arguments);
                };
            }
        })();
    """.trimIndent()

    val NIGHT_MODE_CSS = """
        (function() {
            let style = document.getElementById('elephant-night-style');
            if (!style) {
                style = document.createElement('style');
                style.id = 'elephant-night-style';
                style.textContent = `
                    html {
                        filter: invert(90%) hue-rotate(180deg) contrast(90%) !important;
                        background-color: #1a1a1a !important;
                    }
                    img, video, canvas, [style*="background-image"], svg {
                        filter: invert(100%) hue-rotate(180deg) contrast(110%) !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            }
        })();
    """.trimIndent()

    val REMOVE_NIGHT_MODE_CSS = """
        (function() {
            const style = document.getElementById('elephant-night-style');
            if (style) style.remove();
        })();
    """.trimIndent()

    val ENFORCE_LIGHT_MODE_HEAD = """
        (function() {
            try {
                let meta = document.querySelector('meta[name="color-scheme"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'color-scheme';
                    (document.head || document.documentElement).appendChild(meta);
                }
                meta.content = 'light';
                if (document.documentElement) {
                    document.documentElement.style.colorScheme = 'light';
                }
                const origMatchMedia = window.matchMedia;
                window.matchMedia = function(q) {
                    if (q && q.includes('prefers-color-scheme: dark')) {
                        return { matches: false, media: q, onchange: null, addListener: function(){}, removeListener: function(){}, addEventListener: function(){}, removeEventListener: function(){} };
                    }
                    if (q && q.includes('prefers-color-scheme: light')) {
                        return { matches: true, media: q, onchange: null, addListener: function(){}, removeListener: function(){}, addEventListener: function(){}, removeEventListener: function(){} };
                    }
                    return origMatchMedia ? origMatchMedia.call(window, q) : { matches: false, media: q };
                };
            } catch(e) {}
        })();
    """.trimIndent()

    val ENFORCE_LIGHT_MODE_FULL = """
        (function() {
            try {
                const nightStyle = document.getElementById('elephant-night-style');
                if (nightStyle) nightStyle.remove();

                if (document.documentElement) {
                    document.documentElement.style.colorScheme = 'light';
                    document.documentElement.classList.remove('dark-mode', 'dark');
                }
                if (document.body) {
                    document.body.classList.remove('dark-mode', 'dark');
                }

                if (location.hostname.includes('google.') && location.pathname.includes('/search')) {
                    let googleFix = document.getElementById('elephant-google-light-fix');
                    if (!googleFix) {
                        googleFix = document.createElement('style');
                        googleFix.id = 'elephant-google-light-fix';
                        googleFix.textContent = `
                            html, body, #main, #cnt, .o30Phf, .RNNXgb, .g, .MjjYud, .ynAwRc, .ULSXZ {
                                background-color: #ffffff !important;
                                color: #202124 !important;
                            }
                            .RNNXgb {
                                background: #ffffff !important;
                                border: 1px solid #dfe1e5 !important;
                                box-shadow: 0 1px 6px rgba(32,33,36,.28) !important;
                            }
                            .kno-ecr-pt, .h74SDe, .DKV0Md, .LC20lb, h3, a h3 {
                                color: #1a0dab !important;
                            }
                            .VwiC3b, .MUxGbd, .s3v9rd {
                                color: #4d5156 !important;
                            }
                            .appbar, #hdtb, .hdtb-mbe {
                                background: #ffffff !important;
                            }
                        `;
                        (document.head || document.documentElement).appendChild(googleFix);
                    }
                }
            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * Script injected when Desktop Mode is active.
     * Overrides screen dimensions, platform, and viewport meta tags so websites render
     * in full desktop layout without constraining to mobile phone viewports.
     */
    val DESKTOP_MODE_INJECT = """
        (function() {
            try {
                if (window.screen) {
                    try { Object.defineProperty(window.screen, 'width', { get: () => 1920 }); } catch(e){}
                    try { Object.defineProperty(window.screen, 'height', { get: () => 1080 }); } catch(e){}
                    try { Object.defineProperty(window.screen, 'availWidth', { get: () => 1920 }); } catch(e){}
                    try { Object.defineProperty(window.screen, 'availHeight', { get: () => 1040 }); } catch(e){}
                }
                if (navigator) {
                    try { Object.defineProperty(navigator, 'platform', { get: () => 'Win32' }); } catch(e){}
                    try { Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 0 }); } catch(e){}
                }
                // If a mobile-restricting viewport exists, widen it to standard desktop 1280
                const meta = document.querySelector('meta[name="viewport"]');
                if (meta) {
                    meta.setAttribute('content', 'width=1280, initial-scale=0.25, maximum-scale=3.0, user-scalable=yes');
                }
            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * UC Browser In-Place Inline Built-in Player Engine.
     * Intercepts and wraps HTML5 <video> elements directly on the webpage.
     * Features:
     * 1. In-place playback (playsinline, webkit-playsinline) without launching secondary windows.
     * 2. Signature UC Gestures:
     *    - Left side vertical slide: adjusts screen brightness with Sun HUD.
     *    - Right side vertical slide: adjusts media volume with Speaker HUD.
     *    - Horizontal slide: seek forward/backward with time HUD.
     *    - Double tap: play/pause toggle.
     *    - Long press: instant 2.0X speed sprint, releasing restores normal speed.
     * 3. Top bar: Video title, [🗗] icon, [🔓] icon (icon-only, no text labels).
     * 4. Bottom bar: Play/Pause, current/total time, interactive seek bar, [倍速 0.75x-3.0x], [全屏].
     */
    val UC_INLINE_PLAYER_SCRIPT = """
        (function() {
            if (window._ucPlayerEngineLoaded) {
                if (window._ucScanVideos) window._ucScanVideos();
                return;
            }
            window._ucPlayerEngineLoaded = true;

            // 1. Inject targeted CSS styles for UC player UI & web player control suppression
            if (!document.getElementById('uc-player-engine-styles')) {
                const style = document.createElement('style');
                style.id = 'uc-player-engine-styles';
                style.textContent = `
                    /* Complete suppression of browser default media controls */
                    video::-webkit-media-controls,
                    video::-webkit-media-controls-enclosure,
                    video::-webkit-media-controls-panel,
                    video::-webkit-media-controls-play-button,
                    video::-webkit-media-controls-start-playback-button,
                    video::-webkit-media-controls-overlay-play-button {
                        display: none !important;
                        -webkit-appearance: none !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                        visibility: hidden !important;
                        width: 0 !important;
                        height: 0 !important;
                    }

                    /* Targeted suppression of known third-party web player control bars and overlays */
                    .dplayer-controller,
                    .dplayer-controller-mask,
                    .dplayer-top-fade,
                    .dplayer-top,
                    .dplayer-title,
                    .dplayer-mobile-play,
                    .dplayer-bezel,
                    .dplayer-bar-wrap,
                    .dplayer-icons,
                    .dplayer-notice,
                    .dplayer-info-panel,
                    .dplayer-subtitle,
                    .art-video-player .art-bottom,
                    .art-video-player .art-top,
                    .art-video-player .art-controls,
                    .art-video-player .art-mask,
                    .art-video-player .art-state,
                    .art-video-player .art-layers,
                    .art-video-player .art-loading,
                    .art-video-player .art-danmuku,
                    .art-controls,
                    .art-bottom,
                    .art-top,
                    .art-mask,
                    .art-state,
                    .xgplayer-controls,
                    .xgplayer-top-bar,
                    .xgplayer-start,
                    .xgplayer-poster,
                    .xgplayer-skin-default .xgplayer-controls,
                    .xgplayer-controls-autohide,
                    .vjs-control-bar,
                    .vjs-big-play-button,
                    .vjs-modal-dialog,
                    .prism-player .prism-controlbar,
                    .prism-player .prism-top-bar,
                    .prism-player .prism-big-play-btn,
                    .bpx-player-control-bottom,
                    .bpx-player-control-top,
                    .bpx-player-sending-bar {
                        display: none !important;
                        visibility: hidden !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                    }

                    /* UC Built-in Player Overlay */
                    .uc-player-overlay {
                        position: absolute !important;
                        z-index: 2147483640 !important;
                        pointer-events: auto !important;
                        user-select: none !important;
                        -webkit-user-select: none !important;
                        touch-action: none !important;
                        overflow: hidden !important;
                        box-sizing: border-box !important;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
                    }
                    .uc-player-controls {
                        position: absolute !important;
                        top: 0 !important;
                        left: 0 !important;
                        width: 100% !important;
                        height: 100% !important;
                        display: flex !important;
                        flex-direction: column !important;
                        justify-content: space-between !important;
                        transition: opacity 0.25s ease !important;
                        pointer-events: none !important;
                    }
                    .uc-player-controls.uc-hidden {
                        opacity: 0 !important;
                        pointer-events: none !important;
                    }
                    .uc-top-bar {
                        background: linear-gradient(to bottom, rgba(0,0,0,0.88) 0%, rgba(0,0,0,0) 100%) !important;
                        padding: 10px 14px 22px 14px !important;
                        display: flex !important;
                        align-items: center !important;
                        justify-content: space-between !important;
                        color: #ffffff !important;
                        pointer-events: auto !important;
                    }
                    .uc-title {
                        font-size: 13px !important;
                        font-weight: 500 !important;
                        color: #ffffff !important;
                        white-space: nowrap !important;
                        overflow: hidden !important;
                        text-overflow: ellipsis !important;
                        max-width: 72% !important;
                        text-shadow: 0 1px 3px rgba(0,0,0,0.85) !important;
                    }
                    .uc-btn-group {
                        display: flex !important;
                        align-items: center !important;
                        gap: 12px !important;
                    }
                    .uc-btn-circle {
                        background: transparent !important;
                        border: none !important;
                        color: #ffffff !important;
                        border-radius: 0 !important;
                        width: 38px !important;
                        height: 38px !important;
                        display: flex !important;
                        align-items: center !important;
                        justify-content: center !important;
                        cursor: pointer !important;
                        -webkit-tap-highlight-color: transparent !important;
                        padding: 0 !important;
                        box-shadow: none !important;
                        filter: drop-shadow(0 2px 5px rgba(0,0,0,0.85)) !important;
                        transition: transform 0.15s ease, opacity 0.15s ease !important;
                    }
                    .uc-btn-circle:active {
                        background: transparent !important;
                        opacity: 0.65 !important;
                        transform: scale(0.92) !important;
                    }
                    .uc-lock-side-btn {
                        position: absolute !important;
                        left: 14px !important;
                        top: 50% !important;
                        transform: translateY(-50%) !important;
                        background: transparent !important;
                        border: none !important;
                        color: #ffffff !important;
                        border-radius: 0 !important;
                        width: 44px !important;
                        height: 44px !important;
                        display: flex !important;
                        align-items: center !important;
                        justify-content: center !important;
                        cursor: pointer !important;
                        z-index: 2147483646 !important;
                        pointer-events: auto !important;
                        box-shadow: none !important;
                        filter: drop-shadow(0 2px 6px rgba(0,0,0,0.9)) !important;
                        -webkit-tap-highlight-color: transparent !important;
                        transition: transform 0.15s ease, opacity 0.15s ease !important;
                    }
                    .uc-lock-side-btn:active {
                        transform: translateY(-50%) scale(0.92) !important;
                        opacity: 0.65 !important;
                        background: transparent !important;
                    }
                    .uc-download-side-btn {
                        position: absolute !important;
                        right: 14px !important;
                        top: 50% !important;
                        transform: translateY(-50%) !important;
                        background: transparent !important;
                        border: none !important;
                        color: #ffffff !important;
                        border-radius: 0 !important;
                        width: 44px !important;
                        height: 44px !important;
                        display: flex !important;
                        align-items: center !important;
                        justify-content: center !important;
                        cursor: pointer !important;
                        z-index: 2147483646 !important;
                        pointer-events: auto !important;
                        box-shadow: none !important;
                        filter: drop-shadow(0 2px 6px rgba(0,0,0,0.9)) !important;
                        -webkit-tap-highlight-color: transparent !important;
                        transition: transform 0.15s ease, opacity 0.15s ease !important;
                    }
                    .uc-download-side-btn:active {
                        transform: translateY(-50%) scale(0.92) !important;
                        opacity: 0.65 !important;
                        background: transparent !important;
                    }
                    .uc-center-play-wrap {
                        position: absolute !important;
                        top: 50% !important;
                        left: 50% !important;
                        transform: translate(-50%, -50%) !important;
                        z-index: 2147483644 !important;
                        display: none !important;
                        align-items: center !important;
                        justify-content: center !important;
                        pointer-events: auto !important;
                    }
                    .uc-center-play-wrap.uc-visible {
                        display: flex !important;
                    }
                    .uc-center-play-btn {
                        width: 58px !important;
                        height: 58px !important;
                        border-radius: 50% !important;
                        background: rgba(0, 0, 0, 0.6) !important;
                        border: 2px solid rgba(255, 255, 255, 0.75) !important;
                        color: #ffffff !important;
                        display: flex !important;
                        align-items: center !important;
                        justify-content: center !important;
                        cursor: pointer !important;
                        box-shadow: 0 4px 16px rgba(0,0,0,0.6) !important;
                        backdrop-filter: blur(8px) !important;
                        -webkit-tap-highlight-color: transparent !important;
                        transition: transform 0.15s ease, opacity 0.2s ease !important;
                        padding: 0 !important;
                    }
                    .uc-center-play-btn svg {
                        margin-left: 3px !important;
                        pointer-events: none !important;
                    }
                    .uc-center-play-btn:active {
                        transform: scale(0.9) !important;
                        background: rgba(37, 99, 235, 0.8) !important;
                    }
                    .uc-btn-circle svg, .uc-play-btn svg, .uc-fs-btn svg, .uc-lock-side-btn svg, .uc-download-side-btn svg, .uc-lock-icon-only svg {
                        pointer-events: none !important;
                    }
                    .uc-speed-btn {
                        background: rgba(0,0,0,0.55) !important;
                        border: 1px solid rgba(255,255,255,0.25) !important;
                        color: #ffffff !important;
                        border-radius: 14px !important;
                        padding: 3px 8px !important;
                        font-size: 11px !important;
                        font-weight: 600 !important;
                        cursor: pointer !important;
                        -webkit-tap-highlight-color: transparent !important;
                    }
                    .uc-speed-btn:active {
                        background: rgba(37,99,235,0.8) !important;
                    }
                    .uc-bottom-bar {
                        background: linear-gradient(to top, rgba(0,0,0,0.88) 0%, rgba(0,0,0,0) 100%) !important;
                        padding: 22px 12px 10px 12px !important;
                        display: flex !important;
                        align-items: center !important;
                        gap: 12px !important;
                        color: #ffffff !important;
                        pointer-events: auto !important;
                    }
                    .uc-play-btn, .uc-fs-btn {
                        background: none !important;
                        border: none !important;
                        color: #ffffff !important;
                        font-size: 24px !important;
                        cursor: pointer !important;
                        padding: 4px 6px !important;
                        display: flex !important;
                        align-items: center !important;
                        justify-content: center !important;
                        -webkit-tap-highlight-color: transparent !important;
                        transition: transform 0.15s ease !important;
                    }
                    .uc-play-btn:active {
                        transform: scale(0.9) !important;
                    }
                    .uc-time {
                        font-size: 11px !important;
                        color: #e2e8f0 !important;
                        font-variant-numeric: tabular-nums !important;
                        white-space: nowrap !important;
                        text-shadow: 0 1px 2px rgba(0,0,0,0.8) !important;
                    }
                    .uc-progress-track {
                        flex: 1 !important;
                        height: 20px !important;
                        display: flex !important;
                        align-items: center !important;
                        position: relative !important;
                        cursor: pointer !important;
                        touch-action: none !important;
                    }
                    .uc-progress-bar-bg {
                        width: 100% !important;
                        height: 4px !important;
                        background: rgba(255,255,255,0.3) !important;
                        border-radius: 2px !important;
                        position: relative !important;
                        overflow: visible !important;
                    }
                    .uc-progress-buffered {
                        position: absolute !important;
                        left: 0 !important;
                        top: 0 !important;
                        height: 100% !important;
                        background: rgba(255,255,255,0.5) !important;
                        border-radius: 2px !important;
                        width: 0%;
                    }
                    .uc-progress-fill {
                        position: absolute !important;
                        left: 0 !important;
                        top: 0 !important;
                        height: 100% !important;
                        background: #2563eb !important;
                        border-radius: 2px !important;
                        width: 0%;
                    }
                    .uc-progress-thumb {
                        position: absolute !important;
                        top: 50% !important;
                        right: -5px !important;
                        transform: translateY(-50%) !important;
                        width: 12px !important;
                        height: 12px !important;
                        background: #ffffff !important;
                        border-radius: 50% !important;
                        box-shadow: 0 1px 4px rgba(0,0,0,0.6) !important;
                    }
                    .uc-hud {
                        position: absolute !important;
                        top: 50% !important;
                        left: 50% !important;
                        transform: translate(-50%, -50%) !important;
                        background: rgba(17,20,24,0.88) !important;
                        backdrop-filter: blur(10px) !important;
                        border: 1px solid rgba(255,255,255,0.18) !important;
                        border-radius: 12px !important;
                        padding: 10px 18px !important;
                        color: #ffffff !important;
                        display: none !important;
                        flex-direction: column !important;
                        align-items: center !important;
                        gap: 6px !important;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.5) !important;
                        pointer-events: none !important;
                        z-index: 2147483645 !important;
                        transition: opacity 0.2s ease !important;
                    }
                    .uc-hud.uc-visible {
                        display: flex !important;
                    }
                    .uc-hud-title {
                        font-size: 15px !important;
                        font-weight: 600 !important;
                        display: flex !important;
                        align-items: center !important;
                        gap: 6px !important;
                    }
                    .uc-hud-icon svg {
                        width: 22px !important;
                        height: 22px !important;
                    }
                    .uc-hud-bar {
                        width: 100px !important;
                        height: 5px !important;
                        background: rgba(255,255,255,0.25) !important;
                        border-radius: 3px !important;
                        overflow: hidden !important;
                    }
                    .uc-hud-bar-fill {
                        height: 100% !important;
                        background: #3b82f6 !important;
                        width: 50%;
                        border-radius: 3px !important;
                    }
                    .uc-speed-menu {
                        position: absolute !important;
                        bottom: 52px !important;
                        right: 36px !important;
                        background: rgba(17,20,24,0.95) !important;
                        backdrop-filter: blur(10px) !important;
                        border: 1px solid rgba(255,255,255,0.2) !important;
                        border-radius: 10px !important;
                        padding: 6px !important;
                        display: none !important;
                        flex-direction: column !important;
                        gap: 4px !important;
                        z-index: 2147483646 !important;
                        pointer-events: auto !important;
                    }
                    .uc-speed-menu.uc-visible {
                        display: flex !important;
                    }
                    .uc-speed-item {
                        padding: 6px 16px !important;
                        font-size: 12px !important;
                        color: #e2e8f0 !important;
                        border-radius: 6px !important;
                        cursor: pointer !important;
                        text-align: center !important;
                        -webkit-tap-highlight-color: transparent !important;
                    }
                    .uc-speed-item.active {
                        background: #2563eb !important;
                        color: #ffffff !important;
                        font-weight: bold !important;
                    }
                    .uc-lock-icon-only {
                        position: absolute !important;
                        left: 14px !important;
                        top: 50% !important;
                        transform: translateY(-50%) !important;
                        background: transparent !important;
                        border: none !important;
                        color: #38bdf8 !important;
                        border-radius: 0 !important;
                        width: 44px !important;
                        height: 44px !important;
                        display: none !important;
                        align-items: center !important;
                        justify-content: center !important;
                        cursor: pointer !important;
                        z-index: 2147483647 !important;
                        pointer-events: auto !important;
                        box-shadow: none !important;
                        filter: drop-shadow(0 2px 6px rgba(0,0,0,0.9)) !important;
                        -webkit-tap-highlight-color: transparent !important;
                    }
                    .uc-lock-icon-only.uc-visible {
                        display: flex !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            }

            function formatTime(secs) {
                if (!secs || isNaN(secs) || secs < 0) return "00:00";
                secs = Math.floor(secs);
                const m = Math.floor(secs / 60);
                const s = secs % 60;
                const mm = m < 10 ? "0" + m : "" + m;
                const ss = s < 10 ? "0" + s : "" + s;
                return mm + ":" + ss;
            }

            // High precision SVGs
            const SVG_PLAY = '<svg viewBox="0 0 24 24" width="34" height="34" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>';
            const SVG_PAUSE = '<svg viewBox="0 0 24 24" width="34" height="34" fill="currentColor"><rect x="6" y="4" width="4.5" height="16" rx="1"></rect><rect x="13.5" y="4" width="4.5" height="16" rx="1"></rect></svg>';
            const SVG_CENTER_PLAY = '<svg viewBox="0 0 24 24" width="34" height="34" fill="currentColor"><polygon points="7 4 21 12 7 20 7 4"></polygon></svg>';
            const SVG_PIP = '<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><rect x="11" y="8" width="9" height="7" rx="1.5" ry="1.5" fill="currentColor"></rect></svg>';
            const SVG_LOCK_OPEN = '<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 9.9-1"></path></svg>';
            const SVG_LOCK_CLOSED = '<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>';
            const SVG_DOWNLOAD = '<svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>';
            const SVG_FULLSCREEN = '<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"></path></svg>';
            const SVG_SUN = '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>';
            const SVG_SPEAKER = '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon><path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"></path></svg>';
            const SVG_FORWARD = '<svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><polygon points="13 19 22 12 13 5 13 19"></polygon><polygon points="2 19 11 12 2 5 2 19"></polygon></svg>';
            const SVG_BACKWARD = '<svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><polygon points="11 19 2 12 11 5 11 19"></polygon><polygon points="22 19 13 12 22 5 22 19"></polygon></svg>';
            const SVG_SPEED = '<svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>';

            function isMainVideo(video) {
                if (!video || !video.isConnected) return false;
                const rect = video.getBoundingClientRect();
                const w = rect.width || video.offsetWidth || 0;
                const h = rect.height || video.offsetHeight || 0;
                if (w < 180 || h < 100) {
                    if (video.parentElement) {
                        const pr = video.parentElement.getBoundingClientRect();
                        if (pr.width >= 180 && pr.height >= 100) return true;
                    }
                    return false;
                }
                return true;
            }

            // Find the immediate player container
            function findWebPlayerContainer(video) {
                if (!video || !video.parentElement) return null;
                const known = video.closest('.dplayer, .art-video-player, .xgplayer, .video-js, .prism-player');
                if (known) return known;
                return video.parentElement;
            }

            // Extract episode or clean video title from webpage before hiding controls
            function extractVideoTitle(container) {
                if (container) {
                    try {
                        const titleEl = container.querySelector('.dplayer-title, .art-title, .xgplayer-title');
                        if (titleEl && titleEl.textContent) {
                            const t = titleEl.textContent.trim();
                            if (t.length > 0 && t.length < 60) return t;
                        }
                    } catch(e) {}
                }
                const rawDt = document.title || '';
                const cleanDt = rawDt.split(/[-_—|]/)[0].trim();
                return cleanDt || rawDt || '网页视频';
            }

            // Target suppression for web player controls
            function suppressWebControls(video, container, overlay) {
                if (!container) return;
                try {
                    video.controls = false;
                    video.removeAttribute('controls');
                } catch(e) {}

                const selectors = [
                    '.dplayer-controller', '.dplayer-controller-mask', '.dplayer-top-fade', '.dplayer-top',
                    '.dplayer-title', '.dplayer-mobile-play', '.dplayer-bezel', '.dplayer-bar-wrap',
                    '.dplayer-icons', '.dplayer-notice', '.dplayer-info-panel', '.dplayer-subtitle',
                    '.art-controls', '.art-bottom', '.art-top', '.art-layers', '.art-mask', '.art-state',
                    '.art-loading', '.art-danmuku', '.art-controls-bottom',
                    '.xgplayer-controls', '.xgplayer-top-bar', '.xgplayer-start', '.xgplayer-poster',
                    '.xgplayer-skin-default .xgplayer-controls', '.xgplayer-controls-autohide',
                    '.vjs-control-bar', '.vjs-big-play-button', '.vjs-modal-dialog',
                    '.prism-player .prism-controlbar', '.prism-player .prism-top-bar', '.prism-player .prism-big-play-btn',
                    '.bpx-player-control-bottom', '.bpx-player-control-top', '.bpx-player-sending-bar'
                ];

                try {
                    const found = container.querySelectorAll(selectors.join(','));
                    for (let el of found) {
                        if (overlay && (el === overlay || overlay.contains(el))) continue;
                        el.style.setProperty('display', 'none', 'important');
                        el.style.setProperty('visibility', 'hidden', 'important');
                        el.style.setProperty('opacity', '0', 'important');
                        el.style.setProperty('pointer-events', 'none', 'important');
                    }
                } catch(e) {}
            }

            function setupUcPlayer(video) {
                if (!video || video._ucEnhanced) return;
                if (!isMainVideo(video)) return;
                video._ucEnhanced = true;

                // Ensure single active UC player overlay on page
                if (window._currentUcOverlay) {
                    try { window._currentUcOverlay.remove(); } catch(e) {}
                    window._currentUcOverlay = null;
                }

                // Ensure video plays inline without system popup
                video.setAttribute('playsinline', 'true');
                video.setAttribute('webkit-playsinline', 'true');
                video.setAttribute('x5-playsinline', 'true');
                video.controls = false;

                const playerRoot = findWebPlayerContainer(video);
                if (!playerRoot) return;

                if (window.getComputedStyle(playerRoot).position === 'static') {
                    playerRoot.style.position = 'relative';
                }

                const initialTitle = extractVideoTitle(playerRoot);

                const overlay = document.createElement('div');
                overlay.className = 'uc-player-overlay';
                window._currentUcOverlay = overlay;

                overlay.innerHTML = 
                    '<div class="uc-player-controls">' +
                        '<div class="uc-top-bar">' +
                            '<span class="uc-title">' + initialTitle + '</span>' +
                            '<div class="uc-btn-group">' +
                                '<button class="uc-btn-circle uc-pip-btn" title="小窗">' + SVG_PIP + '</button>' +
                            '</div>' +
                        '</div>' +
                        '<button class="uc-lock-side-btn" title="锁屏">' + SVG_LOCK_OPEN + '</button>' +
                        '<div class="uc-center-play-wrap">' +
                            '<button class="uc-center-play-btn" title="播放/暂停">' + SVG_CENTER_PLAY + '</button>' +
                        '</div>' +
                        '<button class="uc-download-side-btn" title="下载视频">' + SVG_DOWNLOAD + '</button>' +
                        '<div class="uc-bottom-bar">' +
                            '<button class="uc-play-btn">' + SVG_PLAY + '</button>' +
                            '<span class="uc-time">00:00 / 00:00</span>' +
                            '<div class="uc-progress-track">' +
                                '<div class="uc-progress-bar-bg">' +
                                    '<div class="uc-progress-buffered"></div>' +
                                    '<div class="uc-progress-fill"><div class="uc-progress-thumb"></div></div>' +
                                '</div>' +
                            '</div>' +
                            '<button class="uc-speed-btn">1.0X</button>' +
                            '<button class="uc-fs-btn">' + SVG_FULLSCREEN + '</button>' +
                        '</div>' +
                    '</div>' +
                    '<div class="uc-hud">' +
                        '<div class="uc-hud-title"><span class="uc-hud-icon"></span><span class="uc-hud-text"></span></div>' +
                        '<div class="uc-hud-bar"><div class="uc-hud-bar-fill"></div></div>' +
                    '</div>' +
                    '<div class="uc-speed-menu">' +
                        '<div class="uc-speed-item" data-speed="0.75">0.75X</div>' +
                        '<div class="uc-speed-item active" data-speed="1.0">1.0X</div>' +
                        '<div class="uc-speed-item" data-speed="1.25">1.25X</div>' +
                        '<div class="uc-speed-item" data-speed="1.5">1.5X</div>' +
                        '<div class="uc-speed-item" data-speed="2.0">2.0X</div>' +
                        '<div class="uc-speed-item" data-speed="3.0">3.0X</div>' +
                    '</div>' +
                    '<div class="uc-lock-icon-only">' + SVG_LOCK_CLOSED + '</div>';

                function syncOverlaySize() {
                    if (!video.isConnected) {
                        overlay.remove();
                        return;
                    }
                    overlay.style.setProperty('top', '0px', 'important');
                    overlay.style.setProperty('left', '0px', 'important');
                    overlay.style.setProperty('width', '100%', 'important');
                    overlay.style.setProperty('height', '100%', 'important');
                    
                    suppressWebControls(video, playerRoot, overlay);
                }

                syncOverlaySize();
                playerRoot.appendChild(overlay);

                // Observe resizing to stay strictly pinned to video
                if (window.ResizeObserver) {
                    try {
                        const ro = new ResizeObserver(() => syncOverlaySize());
                        ro.observe(video);
                        ro.observe(playerRoot);
                    } catch(e) {}
                }

                // Suppress web controls immediately and on DOM changes
                suppressWebControls(video, playerRoot, overlay);
                if (window.MutationObserver) {
                    try {
                        const mo = new MutationObserver(() => {
                            suppressWebControls(video, playerRoot, overlay);
                        });
                        mo.observe(playerRoot, { childList: true, subtree: true });
                    } catch(e) {}
                }

                const controls = overlay.querySelector('.uc-player-controls');
                const hud = overlay.querySelector('.uc-hud');
                const hudIcon = overlay.querySelector('.uc-hud-icon');
                const hudText = overlay.querySelector('.uc-hud-text');
                const hudBar = overlay.querySelector('.uc-hud-bar');
                const hudBarFill = overlay.querySelector('.uc-hud-bar-fill');
                const playBtn = overlay.querySelector('.uc-play-btn');
                const centerPlayWrap = overlay.querySelector('.uc-center-play-wrap');
                const centerPlayBtn = overlay.querySelector('.uc-center-play-btn');
                const timeLabel = overlay.querySelector('.uc-time');
                const progressTrack = overlay.querySelector('.uc-progress-track');
                const progressFill = overlay.querySelector('.uc-progress-fill');
                const progressBuffered = overlay.querySelector('.uc-progress-buffered');
                const speedBtn = overlay.querySelector('.uc-speed-btn');
                const speedMenu = overlay.querySelector('.uc-speed-menu');
                const fsBtn = overlay.querySelector('.uc-fs-btn');
                const pipBtn = overlay.querySelector('.uc-pip-btn');
                const lockSideBtn = overlay.querySelector('.uc-lock-side-btn');
                const downloadSideBtn = overlay.querySelector('.uc-download-side-btn');
                const lockIconOnly = overlay.querySelector('.uc-lock-icon-only');

                let isLocked = false;
                let controlsTimer = null;
                let hudTimer = null;
                let lockTimer = null;
                let normalSpeed = 1.0;
                let isSeekingProgress = false;

                function doPlay() {
                    try {
                        if (window.dp && typeof window.dp.play === 'function') {
                            window.dp.play();
                        }
                    } catch(e) {}
                    try {
                        if (window.art && typeof window.art.play === 'function') {
                            window.art.play();
                        }
                    } catch(e) {}
                    try {
                        const p = video.play();
                        if (p && typeof p.catch === 'function') p.catch(() => {});
                    } catch(e) {}
                }

                function doPause() {
                    try {
                        if (window.dp && typeof window.dp.pause === 'function') {
                            window.dp.pause();
                        }
                    } catch(e) {}
                    try {
                        if (window.art && typeof window.art.pause === 'function') {
                            window.art.pause();
                        }
                    } catch(e) {}
                    try {
                        video.pause();
                    } catch(e) {}
                }

                function updatePlayState() {
                    const isPaused = video.paused || video.ended;
                    if (isPaused) {
                        playBtn.innerHTML = SVG_PLAY;
                        if (centerPlayWrap) centerPlayWrap.classList.add('uc-visible');
                    } else {
                        playBtn.innerHTML = SVG_PAUSE;
                        if (centerPlayWrap) centerPlayWrap.classList.remove('uc-visible');
                    }
                }

                function showControls() {
                    if (isLocked) return;
                    syncOverlaySize();
                    controls.classList.remove('uc-hidden');
                    updatePlayState();
                    clearTimeout(controlsTimer);
                    if (!video.paused) {
                        controlsTimer = setTimeout(() => {
                            if (!video.paused) controls.classList.add('uc-hidden');
                            speedMenu.classList.remove('uc-visible');
                        }, 3500);
                    }
                }

                function hideControls() {
                    controls.classList.add('uc-hidden');
                    speedMenu.classList.remove('uc-visible');
                }

                function toggleControls() {
                    if (controls.classList.contains('uc-hidden')) {
                        showControls();
                    } else {
                        hideControls();
                    }
                }

                function showHud(iconSvg, text, percent, showBar) {
                    clearTimeout(hudTimer);
                    hudIcon.innerHTML = iconSvg;
                    hudText.textContent = text;
                    if (showBar && percent !== undefined) {
                        hudBar.style.display = 'block';
                        hudBarFill.style.width = Math.min(100, Math.max(0, percent)) + '%';
                    } else {
                        hudBar.style.display = 'none';
                    }
                    hud.classList.add('uc-visible');
                }

                function hideHud(delay) {
                    clearTimeout(hudTimer);
                    if (delay) {
                        hudTimer = setTimeout(() => { hud.classList.remove('uc-visible'); }, delay);
                    } else {
                        hud.classList.remove('uc-visible');
                    }
                }

                function lockPlayer() {
                    isLocked = true;
                    hideControls();
                    lockIconOnly.classList.add('uc-visible');
                    clearTimeout(lockTimer);
                    lockTimer = setTimeout(() => {
                        if (isLocked) lockIconOnly.classList.remove('uc-visible');
                    }, 3500);
                    showHud(SVG_LOCK_CLOSED, '', 0, false);
                    hideHud(500);
                }

                function unlockPlayer() {
                    isLocked = false;
                    lockIconOnly.classList.remove('uc-visible');
                    clearTimeout(lockTimer);
                    showHud(SVG_LOCK_OPEN, '', 0, false);
                    hideHud(500);
                    showControls();
                }

                // Fast tap handler to avoid touch conflicts
                function fastTap(element, handler) {
                    let handled = false;
                    element.addEventListener('touchend', (e) => {
                        e.stopPropagation();
                        e.preventDefault();
                        handled = true;
                        setTimeout(() => { handled = false; }, 300);
                        handler(e);
                    });
                    element.addEventListener('click', (e) => {
                        e.stopPropagation();
                        if (handled) return;
                        handler(e);
                    });
                }

                fastTap(lockSideBtn, () => {
                    lockPlayer();
                });

                fastTap(downloadSideBtn, () => {
                    // Resolve the media URL for THIS video first. Never reuse a
                    // stale manifest from another player/tab before checking the
                    // current element and known player instances.
                    let realSrc = '';
                    const currentSrc = video.currentSrc || video.src || '';
                    if (currentSrc && !currentSrc.startsWith('blob:') &&
                        (currentSrc.startsWith('http://') || currentSrc.startsWith('https://'))) {
                        realSrc = currentSrc;
                    }

                    if (!realSrc) {
                        try {
                            if (window.dp && window.dp.video && typeof window.dp.video.url === 'string') realSrc = window.dp.video.url;
                        } catch(e) {}
                    }
                    if (!realSrc) {
                        try {
                            if (window.art && window.art.url) realSrc = window.art.url;
                        } catch(e) {}
                    }
                    if (!realSrc) {
                        try {
                            if (window.hls && window.hls.url) realSrc = window.hls.url;
                        } catch(e) {}
                    }
                    if (!realSrc) {
                        try {
                            if (window.player && window.player.url) realSrc = window.player.url;
                        } catch(e) {}
                    }

                    // Blob/MSE playback has no directly downloadable URL. In
                    // that case use the most recently observed manifest/direct
                    // media request for the current page.
                    if (!realSrc && window._elephantLastManifestUrl) {
                        realSrc = window._elephantLastManifestUrl;
                    }
                    if (!realSrc && window._elephantLastDirectVideoUrl) {
                        realSrc = window._elephantLastDirectVideoUrl;
                    }

                    if (!realSrc && window.performance && window.performance.getEntriesByType) {
                        const resources = window.performance.getEntriesByType('resource');
                        for (let i = resources.length - 1; i >= 0; i--) {
                            const name = resources[i].name || '';
                            const lower = name.toLowerCase();
                            if (lower.includes('.m3u8') || lower.includes('.mpd') ||
                                lower.includes('.mp4') || lower.includes('.webm') ||
                                lower.includes('.mov') || lower.includes('.flv') ||
                                lower.includes('mime=video') || lower.includes('googlevideo.com')) {
                                realSrc = name;
                                break;
                            }
                        }
                    }

                    if (!realSrc || realSrc === window.location.href) {
                        if (window.ElephantBridge && window.ElephantBridge.showToast) {
                            window.ElephantBridge.showToast('没有找到当前视频的可下载地址');
                        }
                        return;
                    }

                    if (window.ElephantBridge && window.ElephantBridge.downloadVideo) {
                        window.ElephantBridge.downloadVideo(realSrc, document.title || '网页视频');
                    }
                });

                fastTap(lockIconOnly, () => {
                    unlockPlayer();
                });

                fastTap(playBtn, () => {
                    if (video.paused) {
                        doPlay();
                    } else {
                        doPause();
                    }
                    setTimeout(updatePlayState, 50);
                    showControls();
                });

                fastTap(centerPlayBtn, () => {
                    doPlay();
                    setTimeout(updatePlayState, 50);
                    showControls();
                });

                fastTap(fsBtn, () => {
                    if (video.webkitRequestFullscreen) {
                        video.webkitRequestFullscreen();
                    } else if (video.requestFullscreen) {
                        video.requestFullscreen();
                    }
                });

                fastTap(pipBtn, () => {
                    let realSrc = '';
                    if (window._elephantLastManifestUrl) realSrc = window._elephantLastManifestUrl;
                    if (!realSrc && window._elephantLastDirectVideoUrl) realSrc = window._elephantLastDirectVideoUrl;
                    if (!realSrc && video.currentSrc && !video.currentSrc.startsWith('blob:')) realSrc = video.currentSrc;
                    if (!realSrc && video.src && !video.src.startsWith('blob:')) realSrc = video.src;
                    if (!realSrc && window.performance && window.performance.getEntriesByType) {
                        const resources = window.performance.getEntriesByType('resource');
                        for (let i = resources.length - 1; i >= 0; i--) {
                            const name = resources[i].name || '';
                            if (name.includes('.m3u8') || name.includes('.mpd') || name.includes('.mp4') || name.includes('.webm') || name.includes('.flv') || name.includes('mime=video') || name.includes('googlevideo.com')) {
                                realSrc = name;
                                break;
                            }
                        }
                    }
                    if (!realSrc) {
                        if (window.hls && window.hls.url) realSrc = window.hls.url;
                        else if (window.dp && window.dp.video && window.dp.video.url) realSrc = window.dp.video.url;
                        else if (window.player && window.player.url) realSrc = window.player.url;
                    }
                    if (!realSrc) {
                        realSrc = video.currentSrc || video.src || '';
                    }

                    if (window.ElephantBridge && window.ElephantBridge.openFloatingPlayer) {
                        // Mark the exact HTML5 video being handed off. The
                        // native player may later return after the user has moved
                        // around the browser, so resuming every <video> is unsafe.
                        window._elephantLastVideoElement = video;
                        // Pause before the asynchronous Android bridge call.
                        // Permission/settings screens can otherwise give the
                        // webpage time to keep playing.
                        video.pause();
                        window.ElephantBridge.openFloatingPlayer(
                            realSrc,
                            document.title || '网页视频',
                            video.currentTime || 0,
                            video.duration || 0,
                            video.videoWidth || 16,
                            video.videoHeight || 9
                        );
                    }
                });

                fastTap(speedBtn, () => {
                    speedMenu.classList.toggle('uc-visible');
                });

                speedMenu.querySelectorAll('.uc-speed-item').forEach(item => {
                    fastTap(item, () => {
                        const sp = parseFloat(item.getAttribute('data-speed'));
                        video.playbackRate = sp;
                        normalSpeed = sp;
                        speedBtn.textContent = sp + 'X';
                        speedMenu.querySelectorAll('.uc-speed-item').forEach(it => it.classList.remove('active'));
                        item.classList.add('active');
                        speedMenu.classList.remove('uc-visible');
                        showHud(SVG_SPEED, sp + 'X', 0, false);
                        hideHud(500);
                    });
                });

                let touchStartX = 0;
                let touchStartY = 0;
                let touchStartTime = 0;
                let gestureType = '';
                let initialTime = 0;
                let seekDelta = 0;
                let longPressTimer = null;
                let lastTapTime = 0;

                overlay.addEventListener('touchstart', (e) => {
                    e.stopPropagation(); // Never leak touches to website controls underneath
                    if (isLocked) {
                        lockIconOnly.classList.add('uc-visible');
                        clearTimeout(lockTimer);
                        lockTimer = setTimeout(() => {
                            if (isLocked) lockIconOnly.classList.remove('uc-visible');
                        }, 3500);
                        return;
                    }
                    if (e.target.closest('.uc-btn-circle') || e.target.closest('.uc-speed-btn') || 
                        e.target.closest('.uc-speed-menu') || e.target.closest('.uc-progress-track') || 
                        e.target.closest('.uc-play-btn') || e.target.closest('.uc-center-play-btn') || 
                        e.target.closest('.uc-fs-btn') || e.target.closest('.uc-lock-icon-only')) {
                        return;
                    }
                    const touch = e.touches[0];
                    touchStartX = touch.clientX;
                    touchStartY = touch.clientY;
                    touchStartTime = Date.now();
                    gestureType = '';
                    initialTime = video.currentTime;
                    seekDelta = 0;

                    clearTimeout(longPressTimer);
                    longPressTimer = setTimeout(() => {
                        gestureType = 'press2x';
                        normalSpeed = video.playbackRate || 1.0;
                        video.playbackRate = 2.0;
                        showHud(SVG_SPEED, '2.0X', 0, false);
                    }, 450);
                });

                overlay.addEventListener('touchmove', (e) => {
                    e.stopPropagation();
                    if (isLocked) return;
                    if (e.target.closest('.uc-btn-circle') || e.target.closest('.uc-speed-btn') || 
                        e.target.closest('.uc-speed-menu') || e.target.closest('.uc-progress-track') ||
                        e.target.closest('.uc-center-play-btn')) {
                        return;
                    }
                    const touch = e.touches[0];
                    const dx = touch.clientX - touchStartX;
                    const dy = touch.clientY - touchStartY;

                    if (Math.hypot(dx, dy) > 10) {
                        clearTimeout(longPressTimer);
                    }

                    if (gestureType === 'press2x') {
                        if (e.cancelable) e.preventDefault();
                        return;
                    }

                    const rect = overlay.getBoundingClientRect();
                    if (!gestureType && Math.hypot(dx, dy) > 12) {
                        if (Math.abs(dx) > Math.abs(dy)) {
                            gestureType = 'seek';
                        } else {
                            const isLeft = (touchStartX - rect.left) < (rect.width * 0.5);
                            gestureType = isLeft ? 'brightness' : 'volume';
                        }
                    }

                    if (gestureType) {
                        if (e.cancelable) e.preventDefault();
                    }

                    if (gestureType === 'seek') {
                        const maxSeek = Math.min(90, (video.duration || 60) * 0.4);
                        seekDelta = (dx / rect.width) * maxSeek;
                        const targetTime = Math.min(video.duration || 0, Math.max(0, initialTime + seekDelta));
                        const sign = seekDelta >= 0 ? '+' : '';
                        showHud(
                            seekDelta >= 0 ? SVG_FORWARD : SVG_BACKWARD,
                            sign + Math.round(seekDelta) + 's',
                            (targetTime / (video.duration || 1)) * 100,
                            true
                        );
                    } else if (gestureType === 'brightness') {
                        const delta = -dy / rect.height * 0.4;
                        let curPercent = 50;
                        if (window.ElephantBridge && window.ElephantBridge.adjustBrightness) {
                            try {
                                const res = window.ElephantBridge.adjustBrightness(delta);
                                curPercent = Math.round(res * 100);
                            } catch(err) {}
                        }
                        showHud(SVG_SUN, curPercent + '%', curPercent, true);
                    } else if (gestureType === 'volume') {
                        const delta = -dy / rect.height * 0.4;
                        let curPercent = 50;
                        if (window.ElephantBridge && window.ElephantBridge.adjustVolume) {
                            try {
                                const res = window.ElephantBridge.adjustVolume(delta);
                                curPercent = Math.round(res * 100);
                            } catch(err) {}
                        }
                        showHud(SVG_SPEAKER, curPercent + '%', curPercent, true);
                    }
                });

                overlay.addEventListener('touchend', (e) => {
                    e.stopPropagation();
                    clearTimeout(longPressTimer);
                    if (isLocked) return;

                    if (e.target.closest('.uc-btn-circle') || e.target.closest('.uc-speed-btn') || 
                        e.target.closest('.uc-speed-menu') || e.target.closest('.uc-progress-track') || 
                        e.target.closest('.uc-play-btn') || e.target.closest('.uc-center-play-btn') || 
                        e.target.closest('.uc-fs-btn') || e.target.closest('.uc-lock-icon-only')) {
                        return;
                    }

                    if (gestureType === 'press2x') {
                        video.playbackRate = normalSpeed;
                        hideHud(0);
                        gestureType = '';
                        return;
                    }

                    if (gestureType === 'seek') {
                        const targetTime = Math.min(video.duration || 0, Math.max(0, initialTime + seekDelta));
                        video.currentTime = targetTime;
                        hideHud(200);
                        gestureType = '';
                        return;
                    }

                    if (gestureType === 'brightness' || gestureType === 'volume') {
                        hideHud(400);
                        gestureType = '';
                        return;
                    }

                    const duration = Date.now() - touchStartTime;
                    if (duration < 300) {
                        const now = Date.now();
                        if (now - lastTapTime < 320) {
                            lastTapTime = 0;
                            if (video.paused) {
                                doPlay();
                                showHud(SVG_PLAY, '', 0, false);
                            } else {
                                doPause();
                                showHud(SVG_PAUSE, '', 0, false);
                            }
                            hideHud(400);
                        } else {
                            lastTapTime = now;
                            setTimeout(() => {
                                if (lastTapTime === now) {
                                    toggleControls();
                                }
                            }, 280);
                        }
                    }
                });

                overlay.addEventListener('click', (e) => {
                    e.stopPropagation();
                });

                function seekFromProgress(e) {
                    const rect = progressTrack.getBoundingClientRect();
                    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
                    const pct = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
                    if (video.duration) {
                        video.currentTime = pct * video.duration;
                    }
                }

                progressTrack.addEventListener('touchstart', (e) => {
                    e.stopPropagation();
                    isSeekingProgress = true;
                    seekFromProgress(e);
                }, { passive: true });

                progressTrack.addEventListener('touchmove', (e) => {
                    e.stopPropagation();
                    if (isSeekingProgress) seekFromProgress(e);
                }, { passive: true });

                progressTrack.addEventListener('touchend', (e) => {
                    e.stopPropagation();
                    isSeekingProgress = false;
                });

                progressTrack.addEventListener('click', (e) => {
                    e.stopPropagation();
                    seekFromProgress(e);
                });

                video.addEventListener('play', () => {
                    updatePlayState();
                    suppressWebControls(video, playerRoot, overlay);
                    showControls();
                });

                video.addEventListener('playing', () => {
                    updatePlayState();
                });

                video.addEventListener('pause', () => {
                    updatePlayState();
                    suppressWebControls(video, playerRoot, overlay);
                    showControls();
                });

                video.addEventListener('ended', () => {
                    updatePlayState();
                    showControls();
                });

                video.addEventListener('timeupdate', () => {
                    // Keep the native playback session close to the live WebView position.
                    // Throttle bridge traffic because timeupdate can fire several times per second.
                    const now = Date.now();
                    if (!window._elephantLastNativeTimeReport || now - window._elephantLastNativeTimeReport >= 500) {
                        window._elephantLastNativeTimeReport = now;
                        if (window.ElephantBridge && window.ElephantBridge.onVideoPlaybackState) {
                            try { window.ElephantBridge.onVideoPlaybackState(video.currentTime || 0, !video.paused && !video.ended); } catch(err) {}
                        }
                    }
                    timeLabel.textContent = formatTime(video.currentTime) + ' / ' + formatTime(video.duration);
                    if (!isSeekingProgress && video.duration > 0) {
                        const pct = (video.currentTime / video.duration) * 100;
                        progressFill.style.width = pct + '%';
                    }
                    if (video.buffered && video.buffered.length > 0 && video.duration > 0) {
                        const bufEnd = video.buffered.end(video.buffered.length - 1);
                        progressBuffered.style.width = (bufEnd / video.duration) * 100 + '%';
                    }
                });

                window.addEventListener('resize', syncOverlaySize, { passive: true });
                window.addEventListener('scroll', syncOverlaySize, { passive: true });

                // Initial show of controls and suppression of web controls
                updatePlayState();
                showControls();
            }

            function scanIframes() {
                try {
                    const iframes = document.querySelectorAll('iframe');
                    for (let iframe of iframes) {
                        try {
                            const idoc = iframe.contentDocument || iframe.contentWindow.document;
                            if (idoc && !idoc._ucInjected) {
                                idoc._ucInjected = true;
                                if (!idoc.getElementById('uc-player-engine-styles-iframe')) {
                                    const s = idoc.createElement('style');
                                    s.id = 'uc-player-engine-styles-iframe';
                                    s.textContent = style.textContent;
                                    (idoc.head || idoc.documentElement).appendChild(s);
                                }
                                const vids = Array.from(idoc.querySelectorAll('video'));
                                for (let v of vids) {
                                    if (isMainVideo(v)) {
                                        setupUcPlayer(v);
                                    }
                                }
                            }
                        } catch(e) {}
                    }
                } catch(e) {}
            }

            window._ucScanVideos = function() {
                const videos = Array.from(document.querySelectorAll('video'));
                let bestVideo = null;
                let maxArea = 0;
                for (const v of videos) {
                    if (!isMainVideo(v)) continue;
                    const area = (v.offsetWidth || 0) * (v.offsetHeight || 0);
                    if (area > maxArea) {
                        maxArea = area;
                        bestVideo = v;
                    }
                }
                if (bestVideo) {
                    setupUcPlayer(bestVideo);
                }
                scanIframes();
            };

            window._ucScanVideos();

            window.addEventListener('play', (e) => {
                if (e.target && e.target.tagName === 'VIDEO' && isMainVideo(e.target)) {
                    setupUcPlayer(e.target);
                }
            }, true);

            const obs = new MutationObserver(() => {
                window._ucScanVideos();
            });
            obs.observe(document.documentElement || document.body, { childList: true, subtree: true });

            setTimeout(window._ucScanVideos, 500);
            setTimeout(window._ucScanVideos, 1500);
            setTimeout(window._ucScanVideos, 3000);
        })();
    """.trimIndent()
}

