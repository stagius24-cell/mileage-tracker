package com.businesstracker.mileage.ui.components

import android.content.ContentValues
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import java.io.InputStream

/** Map cache only: suppress the SDK's location-bearing error logs on failed/late writes. */
internal class PrivateMapTileCache : SqlTileWriter() {
    private var detached = false
    private var lastCleanup = 0L

    @Synchronized
    override fun saveFile(source: ITileSource, tileIndex: Long, stream: InputStream, expiration: Long?): Boolean {
        if (detached) return false
        return try {
            val database = getDb() ?: return false
            if (!database.isOpen) return false
            val values = ContentValues().apply {
                put("provider", source.name())
                put("key", SqlTileWriter.getIndex(tileIndex))
                put("tile", stream.readBytes())
                put("expires", expiration)
            }
            val saved = database.replaceOrThrow("tiles", null, values) != -1L
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastCleanup > 60_000) {
                lastCleanup = now
                runCleanupOperation()
            }
            saved
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    override fun onDetach() {
        detached = true
        super.onDetach()
    }
}
