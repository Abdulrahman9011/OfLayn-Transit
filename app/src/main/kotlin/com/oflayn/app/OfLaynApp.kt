package com.oflayn.app

import android.app.Application

class OfLaynApp : Application() {
    /** Built lazily; the only thing that runs at start-up is loading the bundled offline data. */
    val container: AppContainer by lazy { AppContainer(this) }
}
