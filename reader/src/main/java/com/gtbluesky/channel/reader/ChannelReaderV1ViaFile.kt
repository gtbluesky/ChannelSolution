package com.gtbluesky.channel.reader

import java.util.zip.ZipFile

/**
 * 基于V1签名的渠道号方案
 * 从META-INF目录下的特定空文件的文件名中读取渠道号
 */
class ChannelReaderV1ViaFile(zipPath: String) : ChannelReader(zipPath) {
    override fun readChannel(): String? {
        if (zipPath.isEmpty()) {
            return null
        }
        var zipFile: ZipFile? = null
        var channel: String? = null
        try {
            zipFile = ZipFile(zipPath)
            for (entry in zipFile.entries()) {
                if (entry.name.startsWith("META-INF/channel_")) {
                    channel = entry.name.split("_")[1]
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                zipFile?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return channel
    }
}