package com.example.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

class ElephantDownloadManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("elephant_downloads_prefs", Context.MODE_PRIVATE)

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    init {
        loadSavedDownloads()
    }

    private fun loadSavedDownloads() {
        val raw = prefs.getString(KEY_DOWNLOAD_ITEMS, null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<DownloadItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val statusStr = obj.optString("status", DownloadStatus.COMPLETED.name)
                val rawStatus = try {
                    DownloadStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    DownloadStatus.COMPLETED
                }
                // If app was killed while downloading, mark as paused
                val status = if (rawStatus == DownloadStatus.DOWNLOADING) DownloadStatus.PAUSED else rawStatus

                list.add(
                    DownloadItem(
                        id = obj.getString("id"),
                        fileName = obj.getString("fileName"),
                        url = obj.getString("url"),
                        filePath = obj.optString("filePath", ""),
                        mimeType = obj.optString("mimeType", ""),
                        totalBytes = obj.optLong("totalBytes", 0L),
                        downloadedBytes = obj.optLong("downloadedBytes", 0L),
                        speedBytesPerSec = 0L,
                        status = status,
                        startTime = obj.optLong("startTime", System.currentTimeMillis()),
                        finishTime = if (obj.has("finishTime")) obj.getLong("finishTime") else null,
                        errorMessage = obj.optString("errorMessage", null)
                    )
                )
            }
            _downloads.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun saveDownloads() {
        try {
            val arr = JSONArray()
            for (item in _downloads.value) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("fileName", item.fileName)
                    put("url", item.url)
                    put("filePath", item.filePath)
                    put("mimeType", item.mimeType)
                    put("totalBytes", item.totalBytes)
                    put("downloadedBytes", item.downloadedBytes)
                    put("status", item.status.name)
                    put("startTime", item.startTime)
                    item.finishTime?.let { put("finishTime", it) }
                    item.errorMessage?.let { put("errorMessage", it) }
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_DOWNLOAD_ITEMS, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Enqueues a new download.
     */
    fun enqueueDownload(
        url: String,
        suggestedFileName: String? = null,
        mimeType: String? = null,
        contentLength: Long = 0L
    ): DownloadItem {
        val fileName = resolveFileName(url, suggestedFileName, mimeType)
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val destFile = getUniqueFile(downloadDir, fileName)
        val determinedMime = mimeType ?: getMimeTypeFromFileName(destFile.name)

        val item = DownloadItem(
            fileName = destFile.name,
            url = url,
            filePath = destFile.absolutePath,
            mimeType = determinedMime,
            totalBytes = contentLength,
            downloadedBytes = 0L,
            speedBytesPerSec = 0L,
            status = DownloadStatus.PENDING,
            startTime = System.currentTimeMillis()
        )

        val current = _downloads.value.toMutableList()
        current.add(0, item)
        _downloads.value = current
        saveDownloads()

        startDownloadJob(item)
        return item
    }

    /**
     * Starts or resumes a download job.
     */
    private fun startDownloadJob(item: DownloadItem) {
        activeJobs[item.id]?.cancel()

        val job = scope.launch {
            updateItemStatus(item.id, DownloadStatus.DOWNLOADING, speed = 0L)
            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                val file = File(item.filePath)
                var existingBytes = if (file.exists()) file.length() else 0L

                var currentUrl = item.url
                var redirectCount = 0
                while (redirectCount < 5) {
                    val urlObj = URL(currentUrl)
                    connection = (urlObj.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 20000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36 Elephant/2.0")
                        setRequestProperty("Accept-Encoding", "identity")
                        if (existingBytes > 0) {
                            setRequestProperty("Range", "bytes=$existingBytes-")
                        }
                    }
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode in 300..399) {
                        val newUrl = connection.getHeaderField("Location")
                        if (!newUrl.isNullOrBlank()) {
                            currentUrl = newUrl
                            connection.disconnect()
                            redirectCount++
                            continue
                        }
                    }
                    break
                }

                val responseCode = connection?.responseCode ?: -1
                val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
                val isSuccess = responseCode == HttpURLConnection.HTTP_OK || isPartial

                if (!isSuccess && responseCode != -1) {
                    throw Exception("HTTP 响应错误: $responseCode")
                }

                val serverContentLength = connection?.contentLengthLong ?: -1L
                val totalBytes = when {
                    isPartial -> existingBytes + (if (serverContentLength > 0) serverContentLength else 0L)
                    serverContentLength > 0 -> serverContentLength
                    item.totalBytes > 0 -> item.totalBytes
                    else -> 0L
                }

                val append = isPartial && existingBytes > 0
                if (!append && file.exists()) {
                    file.delete()
                    existingBytes = 0L
                }

                outputStream = FileOutputStream(file, append)
                inputStream = connection?.inputStream ?: throw Exception("无法获取数据流")

                val buffer = ByteArray(16384)
                var bytesRead: Int
                var totalDownloaded = existingBytes
                var lastTime = System.currentTimeMillis()
                var bytesSinceLastTime = 0L

                updateItemProgress(item.id, totalDownloaded, totalBytes, 0L)

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!isActive) {
                        throw CancellationException("Download cancelled or paused")
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalDownloaded += bytesRead
                    bytesSinceLastTime += bytesRead

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime
                    if (elapsed >= 500) {
                        val speed = (bytesSinceLastTime * 1000) / elapsed
                        updateItemProgress(item.id, totalDownloaded, totalBytes, speed)
                        lastTime = now
                        bytesSinceLastTime = 0L
                    }
                }

                outputStream.flush()

                // Finished successfully
                updateItemCompleted(item.id, totalDownloaded)
                saveDownloads()

            } catch (e: CancellationException) {
                // Was cancelled or paused
                updateItemStatus(item.id, DownloadStatus.PAUSED, speed = 0L)
                saveDownloads()
            } catch (e: Exception) {
                e.printStackTrace()
                updateItemFailed(item.id, e.message ?: "下载失败")
                saveDownloads()
            } finally {
                try { inputStream?.close() } catch (ignored: Exception) {}
                try { outputStream?.close() } catch (ignored: Exception) {}
                try { connection?.disconnect() } catch (ignored: Exception) {}
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    fun pauseDownload(id: String) {
        val job = activeJobs[id]
        if (job != null && job.isActive) {
            job.cancel()
        } else {
            updateItemStatus(id, DownloadStatus.PAUSED, speed = 0L)
            saveDownloads()
        }
    }

    fun resumeDownload(id: String) {
        val item = _downloads.value.find { it.id == id } ?: return
        startDownloadJob(item)
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        val item = _downloads.value.find { it.id == id }
        if (item != null) {
            val file = File(item.filePath)
            if (file.exists()) file.delete()
        }

        updateItemStatus(id, DownloadStatus.CANCELLED, speed = 0L)
        saveDownloads()
    }

    fun deleteDownload(id: String, deleteFile: Boolean = true) {
        cancelDownload(id)
        val item = _downloads.value.find { it.id == id }
        if (deleteFile && item != null) {
            try {
                val f = File(item.filePath)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val current = _downloads.value.toMutableList()
        current.removeAll { it.id == id }
        _downloads.value = current
        saveDownloads()
    }

    fun clearCompleted() {
        val current = _downloads.value.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
        _downloads.value = current
        saveDownloads()
    }

    private fun updateItemProgress(id: String, downloaded: Long, total: Long, speed: Long) {
        val current = _downloads.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val prev = current[index]
            current[index] = prev.copy(
                downloadedBytes = downloaded,
                totalBytes = if (total > 0) total else prev.totalBytes,
                speedBytesPerSec = speed,
                status = DownloadStatus.DOWNLOADING
            )
            _downloads.value = current
        }
    }

    private fun updateItemStatus(id: String, status: DownloadStatus, speed: Long = 0L) {
        val current = _downloads.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val prev = current[index]
            current[index] = prev.copy(
                status = status,
                speedBytesPerSec = speed
            )
            _downloads.value = current
        }
    }

    private fun updateItemCompleted(id: String, finalSize: Long) {
        val current = _downloads.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val prev = current[index]
            current[index] = prev.copy(
                downloadedBytes = finalSize,
                totalBytes = finalSize,
                speedBytesPerSec = 0L,
                status = DownloadStatus.COMPLETED,
                finishTime = System.currentTimeMillis(),
                errorMessage = null
            )
            _downloads.value = current
        }
    }

    private fun updateItemFailed(id: String, error: String) {
        val current = _downloads.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val prev = current[index]
            current[index] = prev.copy(
                status = DownloadStatus.FAILED,
                speedBytesPerSec = 0L,
                errorMessage = error
            )
            _downloads.value = current
        }
    }

    /**
     * Opens the downloaded file using Android FileProvider and Intent.ACTION_VIEW.
     */
    fun openDownloadedFile(context: Context, item: DownloadItem) {
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在或已被移除", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType = item.mimeType.ifBlank { getMimeTypeFromFileName(file.name) }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No app to handle this file type directly, try generic chooser
            try {
                val authority = "${context.packageName}.fileprovider"
                val uri: Uri = FileProvider.getUriForFile(context, authority, file)
                val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(genericIntent, "选择打开方式"))
            } catch (err: Exception) {
                Toast.makeText(context, "未找到可打开此类型文件的应用程序", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "打开文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares the downloaded file using Android Share Sheet.
     */
    fun shareDownloadedFile(context: Context, item: DownloadItem) {
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType = item.mimeType.ifBlank { getMimeTypeFromFileName(file.name) }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享文件"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "分享文件失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveFileName(url: String, suggested: String?, mime: String?): String {
        if (!suggested.isNullOrBlank()) {
            val clean = sanitizeFileName(suggested)
            if (clean.isNotBlank()) return clean
        }

        try {
            val path = URL(url).path
            val decoded = URLDecoder.decode(path, "UTF-8")
            val candidate = decoded.substringAfterLast('/')
            val sanitized = sanitizeFileName(candidate)
            if (sanitized.isNotBlank() && sanitized.contains('.')) {
                return sanitized
            }
        } catch (e: Exception) {}

        // Fallback with timestamp and extension based on mime
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
    }

    private fun getUniqueFile(dir: File, baseName: String): File {
        var file = File(dir, baseName)
        if (!file.exists()) return file

        val nameWithoutExt = baseName.substringBeforeLast('.', baseName)
        val ext = if (baseName.contains('.')) "." + baseName.substringAfterLast('.') else ""

        var count = 1
        while (file.exists()) {
            file = File(dir, "${nameWithoutExt}_($count)$ext")
            count++
        }
        return file
    }

    fun getMimeTypeFromFileName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "")
        return if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "*/*"
        } else {
            "*/*"
        }
    }

    companion object {
        private const val KEY_DOWNLOAD_ITEMS = "key_elephant_download_items"
    }
}
