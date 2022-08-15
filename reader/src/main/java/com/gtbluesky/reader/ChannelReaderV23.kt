package com.gtbluesky.reader

import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/**
 * 基于V2&V3签名的渠道号方案
 * 从ZIP签名块中读取渠道号
 */
class ChannelReaderV23(zipPath: String) : ChannelReader(zipPath) {
    override fun readChannel(): String? {
        if (zipPath.isEmpty()) {
            return null
        }
        var zipFile: RandomAccessFile? = null
        var zipChannel: FileChannel? = null
        return try {
            zipFile = RandomAccessFile(zipPath, "r")
            zipChannel = zipFile.channel
            val eocd = ZipUtil.findEocd(zipChannel) ?: return null
            // 获取APK签名块
            val signBlock = ZipUtil.findSignBlock(zipFile, ZipUtil.findSocdOffset(eocd)) ?: return null
            // APK签名块结构如下:
            //
            // 1. APK签名块大小(不包含自己的8个字节)        8字节
            // 2. ID-Value键值对(有多个键值对)            大小可变
            //      2.1 键值对长度(不包含自己的8个字节)     8字节
            //      2.2 ID                              4字节
            //      2.3 Value                           键值对长度 - ID的4字节
            // 3. APK签名块大小(和第1部分相等)             8字节
            // 4. 魔法数(固定为字符串"APK Sig Block 42")  16字节
            var id = 0
            var length = 0
            var realLength = 0
            var position = Long.SIZE_BYTES
            val posLimit = signBlock.second.capacity() - Long.SIZE_BYTES - ZipUtil.SIG_MAGIC.toByteArray().size
            while (id != ZipUtil.CHANNEL_MAGIC && position < posLimit) {
                signBlock.second.position(position)
                // 读取键值对长度(不包含自己的8个字节)
                length = signBlock.second.long.toInt()
                // 键值对长度是不包含长度信息的8个字节的,所以要加上这8个字节
                realLength = length + Long.SIZE_BYTES
                // 读取ID
                id = signBlock.second.int
                // 移动到下一个键值对
                position += realLength
            }
            if (id == ZipUtil.CHANNEL_MAGIC) {
                ZipUtil.readString(signBlock.second, length - Int.SIZE_BYTES)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            ZipUtil.safeClose(zipChannel, zipFile)
        }
    }
}