package com.sokhan.chat.core

/**
 * Build-time flags resolved from the Gradle-generated [com.sokhan.chat.BuildConfig].
 */
object BuildFlags {
    val DEBUG: Boolean = com.sokhan.chat.BuildConfig.DEBUG
    val VERSION_NAME: String = com.sokhan.chat.BuildConfig.VERSION_NAME
}
