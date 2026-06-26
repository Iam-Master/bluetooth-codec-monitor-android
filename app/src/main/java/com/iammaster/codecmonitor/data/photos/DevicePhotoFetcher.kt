package com.iammaster.codecmonitor.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Port of monitor.py's DuckDuckGo image search + cache mechanism (lines 549-784), with
 * relevance scoring layered on top: results are ranked by brand-domain match + how many
 * device-name tokens appear in the result title, instead of just taking the first hit
 * from an allowed domain (which let through wrong/irrelevant photos).
 */
class DevicePhotoFetcher(private val context: Context) {

    private val client = OkHttpClient()
    private val backgroundRemover = BackgroundRemover()
    private val photosDir: File by lazy {
        File(context.filesDir, "device_photos").apply { mkdirs() }
    }

    /** Releases the ML Kit segmenter. Call when the owning component is destroyed. */
    fun close() {
        backgroundRemover.close()
    }

    // Only the official brand site and major e-commerce listings are trusted for photos —
    // generic review/blog sites are excluded since they often show a reviewer's specific
    // color variant rather than the canonical product photo.
    private val ecommerceDomains = listOf("amazon.", "flipkart.com", "walmart.com", "bestbuy.com")

    // Last-resort tier: only used when the official site and the four primary e-commerce
    // platforms above yield literally nothing (e.g. the official CDN's cached URL has gone
    // stale/404, which happens — DuckDuckGo's index isn't always fresh). Without this, a
    // brand can end up with no photo at all instead of a reasonable fallback.
    private val fallbackRetailerDomains = listOf(
        "croma.com", "reliancedigital.in", "91mobiles.com", "gsmarena.com", "soundguys.com"
    )

    // Some brands sell through a parent company's domain rather than "<brand>.com" (e.g. CMF
    // is a Nothing sub-brand sold via nothing.tech, with per-region storefront subdomains like
    // "us.nothing.tech"). Without this, the substring-on-brand-name check would also match
    // unrelated blog/news articles whose URL slug just mentions the brand (e.g.
    // "giznext.com/oppo-earbuds-review"), since that's a path match, not a real domain match.
    private val officialDomainOverrides = mapOf(
        "cmf" to Regex("^([a-z]{2}\\.)?nothing\\.tech$")
    )

    private val vqdPattern = Pattern.compile("vqd=(['\"])([^'\"]+)\\1")

    fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    fun getCachedPhotoFile(deviceName: String): File? {
        val slugged = slug(deviceName)
        return listOf("jpg", "jpeg", "png", "webp")
            .map { File(photosDir, "$slugged.$it") }
            .firstOrNull { it.exists() && it.length() > 0 }
    }

    suspend fun fetchIfMissing(deviceName: String): File? = withContext(Dispatchers.IO) {
        getCachedPhotoFile(deviceName)?.let { return@withContext it }
        try {
            val brand = deviceName.split(" ").firstOrNull()?.lowercase().orEmpty()
            val candidateUrls = searchImageUrls(deviceName, brand)
            // Try candidates in priority order — some official/CDN hosts block hotlinked
            // downloads (return an HTML error page instead of the image), so a failure must
            // fall through to the next tier rather than aborting the whole fetch.
            for (url in candidateUrls) {
                val result = downloadAndCutout(url, deviceName)
                if (result != null) return@withContext result
            }
            null
        } catch (e: Exception) {
            Log.e("DevicePhotoFetcher", "Photo fetch failed for $deviceName: ${e.message}")
            null
        }
    }

    private fun hostOf(url: String): String = try {
        URI(url).host?.lowercase().orEmpty()
    } catch (e: Exception) {
        ""
    }

    // The domain allowlist above is only checked against the search result's *source page*
    // URL — a malicious/compromised search result could report a trusted source page while
    // pointing the actual "image" field at an attacker-controlled or internal-network host.
    // Before ever downloading, independently verify the image URL itself is http(s) and does
    // not resolve to a private/loopback/link-local address, to close that SSRF gap.
    private fun isSafeImageUrl(url: String): Boolean {
        val uri = try { URI(url) } catch (e: Exception) { return false }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        return try {
            InetAddress.getAllByName(host).all { addr ->
                !addr.isLoopbackAddress && !addr.isLinkLocalAddress &&
                    !addr.isSiteLocalAddress && !addr.isAnyLocalAddress && !addr.isMulticastAddress
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isOfficialHost(host: String, brand: String): Boolean {
        val override = officialDomainOverrides[brand]
        if (override != null) return override.matches(host)
        // Deliberately strict: only the apex/www domain, not arbitrary subdomains. A site
        // like "community.oppo.com" or "forum.oppo.com" hosts user-generated forum content,
        // not official product photos, even though it's technically on the brand's domain.
        return host == "$brand.com" || host == "www.$brand.com"
    }

    private fun searchImageUrls(deviceName: String, brand: String): List<String> {
        val query = "\"$deviceName\" product photo white background"
        val encoded = URLEncoder.encode(query, "UTF-8")
        val tokenRequest = Request.Builder()
            .url("https://duckduckgo.com/?q=$encoded")
            .header("User-Agent", "CodecMonitor/1.0")
            .build()
        val html = client.newCall(tokenRequest).execute().use { it.body?.string().orEmpty() }
        val matcher = vqdPattern.matcher(html)
        if (!matcher.find()) {
            Log.w("DevicePhotoFetcher", "$deviceName: no vqd token found (html len=${html.length})")
            return emptyList()
        }
        val vqd = matcher.group(2) ?: return emptyList()

        val imgRequest = Request.Builder()
            .url("https://duckduckgo.com/i.js?q=$encoded&vqd=$vqd&o=json&p=1&s=0")
            .header("User-Agent", "CodecMonitor/1.0")
            .header("Referer", "https://duckduckgo.com/")
            .build()
        val json = client.newCall(imgRequest).execute().use { it.body?.string().orEmpty() }
        if (json.isBlank()) {
            Log.w("DevicePhotoFetcher", "$deviceName: empty i.js response")
            return emptyList()
        }
        val results = JSONObject(json).optJSONArray("results")
        if (results == null) {
            Log.w("DevicePhotoFetcher", "$deviceName: no results array in i.js response: ${json.take(200)}")
            return emptyList()
        }
        Log.d("DevicePhotoFetcher", "$deviceName: got ${results.length()} results")

        val deviceTokens = deviceName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
        // Variant qualifiers that change which physical product a title refers to. If the
        // device name doesn't mention one of these, a title that does is a different SKU
        // (e.g. "Buds Air7 Pro" must not be matched for plain "Buds Air7") and must be rejected.
        val variantQualifiers = listOf("pro", "max", "plus", "lite", "mini", "ultra", "neo", "se")
        val deviceQualifiers = variantQualifiers.filter { deviceTokens.contains(it) }

        fun isRelevant(title: String): Boolean {
            val allTokensMatch = deviceTokens.isNotEmpty() && deviceTokens.all { title.contains(it) }
            if (!allTokensMatch) return false
            val titleQualifiers = variantQualifiers.filter { title.contains(it) }
            return titleQualifiers.all { it in deviceQualifiers }
        }

        data class Candidate(val imageUrl: String, val sourceHost: String, val title: String)
        val candidates = mutableListOf<Candidate>()
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            val imageUrl = result.optString("image")
            val sourceUrl = result.optString("url")
            val title = result.optString("title").lowercase()
            val width = result.optInt("width", 0)
            val height = result.optInt("height", 0)
            if (!imageUrl.startsWith("http")) continue
            if (!isRelevant(title)) continue
            // Hero product shots are close to square. Wide/short images are usually spec
            // diagrams, banners, or multi-item comparison strips rather than a clean photo.
            if (width > 0 && height > 0) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio < 0.6f || ratio > 1.7f) continue
            }
            candidates += Candidate(imageUrl, hostOf(sourceUrl), title)
        }

        // CDN image paths usually embed an upload date (e.g. ".../general/20230712/...").
        // Brand sites often have many regional/seasonal colorway photos tagged with the exact
        // same title, so among same-tier matches we prefer the EARLIEST dated upload — that's
        // almost always the original launch photo / default colorway, with later dates being
        // limited-edition or regional color additions.
        fun dateScore(url: String): Long = Regex("(20\\d{6})").find(url)?.value?.toLongOrNull() ?: Long.MAX_VALUE

        val ordered = mutableListOf<String>()

        // Tier 1: the brand's own official site (real hostname match, not a substring match
        // against the full URL — otherwise a blog article like "giznext.com/oppo-earbuds"
        // would be wrongly treated as OPPO's official site).
        if (brand.length >= 3) {
            candidates.filter { isOfficialHost(it.sourceHost, brand) }
                .sortedBy { dateScore(it.imageUrl) }
                .forEach { ordered += it.imageUrl }
        }

        // Tier 2: major e-commerce listings, in priority order, since their main listing
        // image is the canonical product photo for that exact model/color.
        for (domain in ecommerceDomains) {
            candidates.filter { it.sourceHost.contains(domain) }
                .sortedBy { dateScore(it.imageUrl) }
                .forEach { ordered += it.imageUrl }
        }

        // Tier 3: always appended after tiers 1 and 2, not just when they're empty — a tier
        // 1/2 URL can still be present in the list but fail to actually download (e.g. a
        // stale/404 CDN link), so the caller's try-each-in-order loop needs tier 3 candidates
        // available to fall through to, not just when search-time selection found zero.
        for (domain in fallbackRetailerDomains) {
            candidates.filter { it.sourceHost.contains(domain) }
                .sortedBy { dateScore(it.imageUrl) }
                .forEach { ordered += it.imageUrl }
        }

        Log.d("DevicePhotoFetcher", "$deviceName: ${ordered.size} candidate(s), trying in order")
        return ordered
    }

    private suspend fun downloadAndCutout(url: String, deviceName: String): File? {
        if (!isSafeImageUrl(url)) {
            Log.w("DevicePhotoFetcher", "$deviceName: rejected unsafe image URL $url")
            return null
        }
        val request = Request.Builder().url(url).header("User-Agent", "CodecMonitor/1.0").build()
        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("DevicePhotoFetcher", "$deviceName: download HTTP ${response.code} for $url")
                return null
            }
            val body = response.body ?: return null
            val b = body.bytes()
            if (b.size > 4 * 1024 * 1024 || b.size < 500) {
                Log.w("DevicePhotoFetcher", "$deviceName: download size ${b.size} out of range for $url")
                return null
            }
            b
        }

        // Decode bounds first, before the full pixel decode — a small compressed file can
        // still claim huge dimensions (a "decompression bomb"), which would otherwise OOM the
        // app when BitmapFactory allocates the full ARGB buffer.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val pixelCount = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || pixelCount > 4000L * 4000L) {
            Log.w("DevicePhotoFetcher", "$deviceName: rejected image with bounds ${bounds.outWidth}x${bounds.outHeight} from $url")
            return null
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) {
            Log.w("DevicePhotoFetcher", "$deviceName: failed to decode bitmap from $url")
            return null
        }
        val cutout = backgroundRemover.removeBackground(bitmap)
        val dest = File(photosDir, "${slug(deviceName)}.png")
        val toSave = cutout ?: bitmap
        FileOutputStream(dest).use { out ->
            toSave.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return dest
    }
}
