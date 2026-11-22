package net.venipa.stashling

import android.app.Application

class StashlingApp : Application() {
    val container by lazy { AppContainer(this) }
}
