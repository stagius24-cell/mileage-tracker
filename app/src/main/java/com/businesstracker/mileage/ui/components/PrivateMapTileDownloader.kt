package com.businesstracker.mileage.ui.components

import android.graphics.drawable.Drawable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.TileDownloader
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/** Avoid the SDK's default error logging, which includes location-revealing tile URLs/indices. */
internal class PrivateMapTileDownloader(
    private val client: OkHttpClient = httpClient
) : TileDownloader() {
    override fun downloadTile(
        tileIndex: Long,
        redirectCount: Int,
        targetUrl: String,
        cache: IFilesystemCache?,
        source: OnlineTileSourceBase
    ): Drawable? = try {
        val request = Request.Builder().url(targetUrl)
            .header("User-Agent", Configuration.getInstance().userAgentValue).build()
        if (request.url.scheme != "https" || request.url.host != "tile.openstreetmap.org") null
        else client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.body == null) null
            else {
                val bytes = response.body!!.bytes()
                val expires = source.tileSourcePolicy.computeExpirationTime(
                    response.header("Expires"), response.header("Cache-Control"), System.currentTimeMillis()
                )
                // Honor the tile service's cache lifetime. No bulk/prefetch requests.
                ByteArrayInputStream(bytes).use { cache?.saveFile(source, tileIndex, it, expires) }
                source.getDrawable(ByteArrayInputStream(bytes))
            }
        }
    } catch (_: Exception) {
        null // Map UI reports availability without logging locations or request details.
    }

    companion object {
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    }
}
