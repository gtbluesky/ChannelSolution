package com.gtbluesky.channel.writer

abstract class ChannelWriter(
    protected val srcPath: String,
    protected val dstPath: String,
    protected val channel: String
) {
    open fun writeChannel() = false
}