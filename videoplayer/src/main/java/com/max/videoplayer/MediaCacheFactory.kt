package com.example.dlna

import android.content.Context
import android.util.Log
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
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
