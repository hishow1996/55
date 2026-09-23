package com.example.data

import com.example.model.PluginItem

object DefaultPlugins {
    fun getBuiltInPlugins(): List<PluginItem> = listOf(
        PluginItem(
            id = "video_sniffer",
            name = "网页视频嗅探与提取",
            description = "自动监听页面内所有HTML5视频流，支持一键调起悬浮窗播放及全屏播放",
            author = "大象内核团队",
            version = "3.1",
            isEnabled = true,
            matchPattern = "*",
            runAt = "document_start",
            scriptCode = """
                (function() {
                    function reportVideo(video) {
                        const src = video.currentSrc || video.src;
                        if (!src || src.startsWith('blob:')) {
                            const sources = video.getElementsByTagName('source');
                            for (let s of sources) {
                                if (s.src) { report(s.src, video); return; }
                            }
                        }
                        if (src) report(src, video);
                    }
                    function report(src, video) {
                        if (window.ElephantBridge && window.ElephantBridge.onVideoDetected) {
                            window.ElephantBridge.onVideoDetected(
                                src,
                                document.title || '网页视频',
                                video.duration || 0,
                                video.currentTime || 0,
                                video.videoWidth || 16,
                                video.videoHeight || 9
                            );
                        }
                    }
                    function scanVideos() {
                        const videos = document.querySelectorAll('video');
                        videos.forEach(v => {
                            reportVideo(v);
                            v.addEventListener('play', () => reportVideo(v));
                            v.addEventListener('loadeddata', () => reportVideo(v));
                        });
                    }
                    document.addEventListener('DOMContentLoaded', scanVideos);
                    window.addEventListener('load', scanVideos);
                    setInterval(scanVideos, 2500);
                })();
            """.trimIndent(),
            isBuiltIn = true
        ),
        PluginItem(
            id = "dark_reader",
            name = "深色网页渲染增强",
            description = "智能反转过亮色彩，保留图片与视频真实色彩，夜间浏览更柔和",
            author = "大象内核团队",
            version = "1.8",
            isEnabled = false,
            matchPattern = "*",
            runAt = "document_end",
            scriptCode = """
                (function() {
                    if (document.getElementById('elephant-dark-css')) return;
                    const style = document.createElement('style');
                    style.id = 'elephant-dark-css';
                    style.textContent = `
                        html {
                            filter: invert(90%) hue-rotate(180deg) !important;
                            background: #181A1B !important;
                        }
                        img, video, canvas, [style*="background-image"], svg {
                            filter: invert(100%) hue-rotate(180deg) !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                })();
            """.trimIndent(),
            isBuiltIn = true
        ),
        PluginItem(
            id = "eye_care",
            name = "夜间防蓝光护眼滤镜",
            description = "覆盖舒适的暖黄色温护眼滤镜，缓解弱光环境下的眼部疲劳",
            author = "大象实验室",
            version = "1.2",
            isEnabled = false,
            matchPattern = "*",
            runAt = "document_end",
            scriptCode = """
                (function() {
                    let overlay = document.getElementById('elephant-eye-care');
                    if (!overlay) {
                        overlay = document.createElement('div');
                        overlay.id = 'elephant-eye-care';
                        overlay.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background:rgba(255,170,40,0.16);pointer-events:none;z-index:2147483647;mix-blend-mode:multiply;';
                        (document.body || document.documentElement).appendChild(overlay);
                    }
                })();
            """.trimIndent(),
            isBuiltIn = true
        ),
        PluginItem(
            id = "reader_mode",
            name = "极简正文阅读模式",
            description = "自动提取新闻与博客正文，隐藏侧边栏与无关杂项",
            author = "大象阅读",
            version = "1.5",
            isEnabled = false,
            matchPattern = "*",
            runAt = "document_end",
            scriptCode = """
                (function() {
                    const article = document.querySelector('article') || document.querySelector('.article') || document.querySelector('.post-content') || document.querySelector('#content');
                    if (article) {
                        article.style.maxWidth = '800px';
                        article.style.margin = '0 auto';
                        article.style.fontSize = '18px';
                        article.style.lineHeight = '1.8';
                    }
                })();
            """.trimIndent(),
            isBuiltIn = true
        ),
        PluginItem(
            id = "video_speed",
            name = "网页倍速播放控制器",
            description = "突破网站倍速限制，支持长按或自定义调整任意网页视频播放倍速",
            author = "大象工具箱",
            version = "2.0",
            isEnabled = true,
            matchPattern = "*",
            runAt = "document_start",
            scriptCode = """
                (function() {
                    window.setElephantVideoSpeed = function(rate) {
                        document.querySelectorAll('video').forEach(v => v.playbackRate = rate);
                    };
                })();
            """.trimIndent(),
            isBuiltIn = true
        )
    )
}
