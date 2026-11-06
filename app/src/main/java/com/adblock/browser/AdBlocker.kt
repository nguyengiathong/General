package com.adblock.browser

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bộ lọc quảng cáo dựa trên tên miền (giống file hosts / uBlock "network filter").
 *
 * - Danh sách mặc định nằm trong assets/adblock_hosts.txt.
 * - Có thể tải thêm danh sách lớn (StevenBlack hosts) và lưu vào bộ nhớ trong.
 * - Một request bị chặn nếu host của nó hoặc bất kỳ tên miền cha nào nằm trong danh sách.
 */
object AdBlocker {

    const val REMOTE_LIST_URL =
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private const val REMOTE_FILE = "remote_hosts.txt"

    @Volatile
    private var blockedHosts: Set<String> = emptySet()

    /** Các từ khóa trong URL thường là quảng cáo/theo dõi (áp dụng thêm ngoài danh sách host). */
    private val urlKeywords = listOf(
        "/ads/", "/ad/", "/adserver", "/adsystem", "/pagead/", "/banners/",
        "/doubleclick/", "/googleads", "/prebid", "/adframe", "/adview",
        "ad_banner", "adsbygoogle", "/popunder", "/popads"
    )

    val blockedCount = AtomicInteger(0)

    /** Các tên miền bị chặn gần đây (mới nhất ở đầu), dùng để hiển thị trên màn hình chính. */
    val recentBlocked = java.util.concurrent.ConcurrentLinkedDeque<String>()

    fun recordBlocked(domain: String) {
        blockedCount.incrementAndGet()
        recentBlocked.addFirst(domain)
        while (recentBlocked.size > 100) recentBlocked.pollLast()
    }

    fun isLoaded(): Boolean = blockedHosts.isNotEmpty()

    fun hasRemoteList(context: Context): Boolean = File(context.filesDir, REMOTE_FILE).exists()

    /** Dùng cho bộ lọc DNS toàn hệ thống. */
    fun isDomainBlocked(domain: String): Boolean =
        isHostBlocked(domain.lowercase().trimEnd('.'))

    fun load(context: Context) {
        val hosts = HashSet<String>(4096)
        context.assets.open("adblock_hosts.txt").bufferedReader().useLines { lines ->
            lines.forEach { parseLine(it)?.let(hosts::add) }
        }
        val remote = File(context.filesDir, REMOTE_FILE)
        if (remote.exists()) {
            remote.bufferedReader().useLines { lines ->
                lines.forEach { parseLine(it)?.let(hosts::add) }
            }
        }
        blockedHosts = hosts
    }

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (!isLoaded()) load(context)
    }

    fun size(): Int = blockedHosts.size

    /**
     * Tải danh sách hosts mới từ Internet. Chạy trên thread nền.
     * @return số tên miền sau khi cập nhật.
     */
    fun updateFromInternet(context: Context): Int {
        val conn = URL(REMOTE_LIST_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            val tmp = File(context.filesDir, "$REMOTE_FILE.tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(File(context.filesDir, REMOTE_FILE))
        } finally {
            conn.disconnect()
        }
        load(context)
        return size()
    }

    /** Hỗ trợ định dạng "0.0.0.0 domain", "127.0.0.1 domain" và "domain". */
    private fun parseLine(raw: String): String? {
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) return null
        val parts = line.split(Regex("\\s+"))
        val host = (if (parts.size >= 2) parts[1] else parts[0]).lowercase()
        if (host == "localhost" || host == "0.0.0.0" || host.startsWith("localhost.") ||
            host == "broadcasthost" || host == "local" || !host.contains('.')
        ) return null
        return host
    }

    fun isAd(url: Uri): Boolean {
        val host = url.host?.lowercase() ?: return false
        if (isHostBlocked(host)) return true
        val path = (url.path ?: "").lowercase()
        return urlKeywords.any { path.contains(it) }
    }

    private fun isHostBlocked(host: String): Boolean {
        val set = blockedHosts
        var h = host
        while (true) {
            if (set.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /** CSS ẩn các khung quảng cáo còn sót lại (cosmetic filtering). */
    val HIDE_ADS_JS: String = """
        (function() {
          if (document.getElementById('__adblock_style')) return;
          var css = [
            'ins.adsbygoogle', '.adsbygoogle', '[id^="google_ads"]', '[id^="div-gpt-ad"]',
            'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
            'iframe[id^="google_ads_iframe"]', '[class*="ad-banner"]', '[class*="ads-banner"]',
            '[id*="ad-banner"]', '.ad-container', '.ads-container', '.advertisement',
            '.adsbox', '.ad-slot', '.ad-unit', '.sponsored-ad', '[data-ad-slot]',
            '[data-ad-unit]', '[aria-label="Advertisement"]', '.banner-ads', '.qc', '.quangcao',
            '[class*="quang-cao"]', '[id*="quangcao"]'
          ].join(',') + '{display:none !important; visibility:hidden !important; height:0 !important;}';
          var s = document.createElement('style');
          s.id = '__adblock_style';
          s.textContent = css;
          (document.head || document.documentElement).appendChild(s);
          window.open = function() { return null; };
        })();
    """.trimIndent()
}
