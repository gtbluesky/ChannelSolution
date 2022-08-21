package com.gtbluesky.channel.reader

abstract class ChannelReader(protected var zipPath: String) {
    open fun readChannel(): String? = null
}