package com.oflayn.app

import android.app.Application

class OfLaynApp : Application() {
    /** Created lazily: nothing heavy (no AI model, no GPS) runs at start-up. */
    val container: AppContainer by lazy { AppContainer(this) }
}
