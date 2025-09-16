package com.example.dlna

import android.content.Context
import android.util.Log
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object MediaCacheFactory {
    private const val TAG = "MediaCacheFactory"
    private var cacheFactory: DataSource.Factory? = null
    private val sDatabaseProvider: DatabaseProvider? = null

    @Synchronized
    fun getCacheFactory(ctx: Context): DataSource.Factory {
        if (cacheFactory == null) {
            var downDirectory = File(ctx.cacheDir, "videos")
            var cache = SimpleCache(
                downDirectory,
                LeastRecentlyUsedCacheEvictor(1024 * 1024 * 512),
            )
            cacheFactory = CacheDataSource.Factory().setCache(cache)
                .setCacheReadDataSourceFactory(
                    DefaultDataSource.Factory(
                        ctx,
                        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false)
                            .setConnectTimeoutMs(8000)
                            .setReadTimeoutMs(8000)
                            .setUserAgent("MY_Exoplayer")
                    )
                )
                .setUpstreamDataSourceFactory(
                    DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false)
                        .setConnectTimeoutMs(8000)
                        .setReadTimeoutMs(8000)
                        .setUserAgent("MY_Exoplayer")
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .setEventListener(object : CacheDataSource.EventListener {
                    override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                        Log.d(TAG, "onCachedBytesRead $cacheSizeBytes  >> $cachedBytesRead")
                    }

                    override fun onCacheIgnored(reason: Int) {
                        Log.d(TAG, "onCacheIgnored $reason")
                    }

                })
        }
        return cacheFactory!!
    }
}
