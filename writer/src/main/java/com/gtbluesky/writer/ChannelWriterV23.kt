package com.gtbluesky.writer

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * 基于V2&V3签名的渠道号方案
 * 将渠道号写入ZIP签名块中
 */
class ChannelWriterV23(
    srcPath: String,
    dstPath: String,
    channel: String
) : ChannelWriter(
    srcPath,
    dstPath,
    channel
) {
    override fun writeChannel(): Boolean {
        // [APK签名块]插入在[central directory]之前,而[central directory]的起始位置可以在[EOCD]的socdOffset部分读取
        // 我们在[APK签名块]里面插入渠道信息,会影响到[central directory]的位置,
        // 所以需要同步修改[EOCD]里面的socdOffset
        //
        // [zip包其余内容](不变)          ...
        //
        //                              1. APK签名块大小(不包含自己的8个字节)        8字节
        // [APK签名块](需要插入渠道信息)    2. ID-Value键值对                        大小可变
        //                              3. APK签名块大小(和第1部分相等)             8字节
        //                              4. 魔法数(固定为字符串"APK Sig Block 42")  16字节
        //                                      <--------------------------
        // [central directory](不变)    ...                                |
        //                                                                 |
        //                              end of central dir signature       |
        //                              ...                                |
        // [EOCD](需要修改socdOffset)    socdOffset  ------------------------
        //                              ...
        //
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
            val eocd = ZipUtil.findEocd(src) ?: return false
            // 获取旧的APK签名块
            val socdOffset = ZipUtil.findSocdOffset(eocd)
            val signBlock = ZipUtil.findSignBlock(zipFile, socdOffset) ?: return false
            // 往APK签名块插入渠道信息,得到新的APK签名块
            val newSignBlock = addChannelInfo(signBlock.second, channel)
            // 修改eocd中的socd
            adjustSocdOffset(eocd, channel)
            // APK签名块前的数据是没有改过的,可以直接拷贝
            src.position(0)
            ZipUtil.copy(src, dst, signBlock.first)
            // 往后插入新的APK签名块的数据
            dst.write(newSignBlock)
            // 往后插入[central directory]的数据,这部分也是没有修改的
            src.position(socdOffset.toLong())
            ZipUtil.copy(src, dst, src.size() - socdOffset - eocd.capacity())
            // 往后插入修改后的eocd
            eocd.position(0)
            dst.write(eocd)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            ZipUtil.safeClose(src, zipFile, dst, fos)
        }
    }

    private fun addChannelInfo(oldSignBlock: ByteBuffer, channel: String): ByteBuffer {
        // ID-Value键值对的格式如下:
        //
        // 键值对长度(不包含自己的8个字节)   8字节
        // ID                            4字节
        // Value                         键值对长度 - ID的4字节
        val channelSize = channel.toByteArray().size
        val channelBlockSize = Long.SIZE_BYTES + Int.SIZE_BYTES + channelSize
        //包含开头的签名块大小
        val newSignBlockRealSize = oldSignBlock.capacity() + channelBlockSize
        val newSignBlock = ByteBuffer.allocate(newSignBlockRealSize)
        newSignBlock.order(ByteOrder.LITTLE_ENDIAN)
        // 复制原签名块
        oldSignBlock.position(0)
        newSignBlock.put(oldSignBlock)
        // 读取原本的APK签名块长度
        oldSignBlock.position(0)
        val oldSignBlockSize = oldSignBlock.long
        // 该长度要加上插入的渠道信息键值对长度
        newSignBlock.position(0)
        //不包含开头的签名块大小
        val newSignBlockSize = oldSignBlockSize + channelBlockSize
        newSignBlock.putLong(newSignBlockSize)
        // APK签名块结构如下:
        //
        // 1. APK签名块大小(不包含自己的8个字节)        8字节
        // 2. ID-Value键值对                        大小可变
        // 3. APK签名块大小(和第1部分相等)             8字节
        // 4. 魔法数(固定为字符串"APK Sig Block 42")  16字节

        // 我们把渠道包键值对放到ID-Value键值对块的最后
        // 所以从后往前减去魔法数的16字节,减去APK签名块大小的8字节
        // 定位到渠道包键值的起始位置，即原ID-Value键值对块的最后
        val magicSize = ZipUtil.SIG_MAGIC.toByteArray().size
        newSignBlock.position(oldSignBlock.capacity() - magicSize - Long.SIZE_BYTES)
        // 插入渠道包键值对数据
        newSignBlock.putLong((Int.SIZE_BYTES + channelSize).toLong())
        newSignBlock.putInt(ZipUtil.CHANNEL_MAGIC)
        newSignBlock.put(channel.toByteArray())
        // 插入APK签名块长度
        newSignBlock.putLong(newSignBlockSize)
        // 插入魔法数
        newSignBlock.put(ZipUtil.SIG_MAGIC.toByteArray())
        newSignBlock.flip()
        return newSignBlock
    }

    /**
     * 由于APK签名块在socd offset的前面
     * 而我们又在APK签名块里面插入了渠道信息
     * 所以socd offset应该再往后移动插入的渠道信息键值对的大小
     */
    private fun adjustSocdOffset(eocd: ByteBuffer, channel: String) {
        // 读取原本的socd offset
        eocd.position(ZipUtil.POSITION_OF_SOCD_OFFSET)
        val socdOffset = eocd.int

        // 键值对格式如下:
        //
        // 键值对长度(不包含自己的8个字节)   8字节
        // ID                            4字节
        // Value                         键值对长度
        //
        eocd.position(ZipUtil.POSITION_OF_SOCD_OFFSET)
        eocd.putInt(socdOffset + Long.SIZE_BYTES + Int.SIZE_BYTES + channel.toByteArray().size)
    }
}