package com.bykea.gpstracking.util

import android.util.Log
import com.bykea.gpstracking.BuildConfig

/**
 * Debug-only logging. Location data is sensitive and log writes cost battery on a service that
 * runs for hours, so release builds log nothing.
 */
object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, message, error)
    }
}
