package com.gtbluesky.reader

abstract class ChannelReader(protected var zipPath: String) {
    open fun readChannel(): String? = null
}