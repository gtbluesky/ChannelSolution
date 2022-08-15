package com.gtbluesky.writer

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * 基于V1签名的渠道号方案
 * 将渠道号写入到ZIP的EOCD中的Comment中
 */
class ChannelWriterV1ViaComment(
    srcPath: String,
    dstPath: String,
    channel: String
) : ChannelWriter(
    srcPath,
    dstPath,
    channel
) {
    override fun writeChannel(): Boolean {
        if (channel.isEmpty()) {
            return false
        }
        var zipFile: RandomAccessFile? = null
        var fos: FileOutputStream? = null
        var src: FileChannel? = null
        var dst: FileChannel? = null
        return try {
            zipFile = RandomAccessFile(File(srcPath), "r")
            src = zipFile.channel

            fos = FileOutputStream(dstPath)
            dst = fos.channel

            // 查找eocd
            val oldEocd = ZipUtil.findEocd(src) ?: return false
            // 往eocd插入渠道信息得到新的eocd
            val newEocd = addChannelInfo(
                oldEocd,
                channel
            )
            // eocd前面的数据是没有改到的,直接拷贝就好
            ZipUtil.copy(src, dst, zipFile.length() - oldEocd.capacity())
            // 往后插入新的eocd
            dst.write(newEocd)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            ZipUtil.safeClose(src, zipFile, dst, fos)
        }
    }

    private fun addChannelInfo(eocd: ByteBuffer, channel: String): ByteBuffer {
        // end of central directory record 的格式如下:
        //
        // end of central dir signature                                                    4 bytes  (0x06054b50)
        // number of this disk                                                             2 bytes
        // number of the disk with the start of the central directory                      2 bytes
        // total number of entries in the central directory on this disk                   2 bytes
        // total number of entries in the central directory                                2 bytes
        // size of the central directory                                                   4 bytes
        // offset of start of central directory with respect to the starting disk number   4 bytes
        // .ZIP file comment length                                                        2 bytes
        // .ZIP file comment                                                               (variable size)
        //
        // 我们可以在.ZIP file comment里面插入渠道信息块:
        //
        // 渠道信息      大小记录在[渠道信息长度]中
        // 渠道信息长度  2字节
        // 魔数         4字节
        //
        // 魔数放在最后面方便我们读取判断是否有渠道信息
        val channelSize = channel.toByteArray().size
        // 渠道信息 + 渠道信息长度 + 渠道信息魔数
        val channelBlockSize = channelSize + Short.SIZE_BYTES + Int.SIZE_BYTES
        val buffer = ByteBuffer.allocate(eocd.capacity() + channelBlockSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // eocd前面部分的数据我们没有改动,直接拷贝就好
        val bytes = ByteArray(ZipUtil.EOCD_MIN_LENGTH - ZipUtil.SIZE_OF_COMMENT_LENGTH)
        eocd.get(bytes)
        buffer.put(bytes)

        // 由于插入了渠道信息块,zip包的注释长度需要相应的增加
        buffer.putShort((eocd.short + channelBlockSize).toShort())

        // 拷贝原本的zip包注释
        eocd.position(ZipUtil.EOCD_MIN_LENGTH)
        buffer.put(eocd)

        // 插入渠道包信息块
        buffer.put(channel.toByteArray()) // 渠道信息
        buffer.putShort(channelSize.toShort()) // 渠道信息长度
        buffer.putInt(ZipUtil.CHANNEL_MAGIC) // 魔数
        buffer.flip()
        return buffer
    }
}