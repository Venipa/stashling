package net.venipa.stashling

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import net.venipa.stashling.work.DownloadWatchScheduler
import okhttp3.OkHttpClient

class StashlingApp :
    Application(),
    SingletonImageLoader.Factory {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        DownloadWatchScheduler.bind(this, container.secrets.whisparr, container.appScope)
    }

    // Its own OkHttp client: StashDB images need no key (stash-box serves /images anonymously), so the key never reaches Coil.
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader
        .Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() })) }
        .diskCache {
            DiskCache
                .Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }.build()
}
