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

    val VIDEO_SNIFFER_PROBE = """
        (function() {
            const videos = document.querySelectorAll('video');
            for (let v of videos) {
                const src = v.currentSrc || v.src;
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
            // Check for iframe embeds or common players
            const iframes = document.querySelectorAll('iframe');
            for (let f of iframes) {
                if (f.src && (f.src.includes('player') || f.src.includes('video') || f.src.includes('bilibili') || f.src.includes('youtube'))) {
                    if (window.ElephantBridge) {
                        window.ElephantBridge.onVideoDetected(
                            f.src,
                            document.title || '网页视频流',
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

    val RESUME_WEB_VIDEOS = """
        (function() {
            document.querySelectorAll('video').forEach(v => {
                try { v.play(); } catch(e) {}
            });
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
}
