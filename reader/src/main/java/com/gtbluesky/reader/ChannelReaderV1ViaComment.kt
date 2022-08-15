package com.gtbluesky.reader

import java.io.RandomAccessFile

/**
 * 基于V1签名的渠道号方案
 * 从ZIP的EOCD中的Comment中读取渠道号
 */
class ChannelReaderV1ViaComment(zipPath: String) : ChannelReader(zipPath) {
    override fun readChannel(): String? {
        if (zipPath.isEmpty()) {
            return null
        }
        var zipFile: RandomAccessFile? = null
        return try {
            zipFile = RandomAccessFile(zipPath, "r")
            // 读取apk的结尾4字节看看是否为渠道信息魔数判断是否有渠道信息
            val channelMagicPos = zipFile.length() - Int.SIZE_BYTES
            val channelMagic = ZipUtil.readInt(zipFile, channelMagicPos)
            if (channelMagic != ZipUtil.CHANNEL_MAGIC) {
                return null
            }
            // 再往前读两个字节获取渠道信息的长度
            val channelLenPos = channelMagicPos - Short.SIZE_BYTES
            val channelLen = ZipUtil.readShort(zipFile, channelLenPos)
            if (channelLen <= 0) {
                return null
            }
            val channelPos = channelLenPos - channelLen
            ZipUtil.readString(zipFile, channelPos, channelLen.toInt())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            ZipUtil.safeClose(zipFile)
        }
    }
}