package com.gtbluesky.writer

import java.io.Closeable
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object ZipUtil {
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
    // 前面22个字节是固定的,最后的[.ZIP file comment]长度是可变。可以为零,此时即为EOCD的最小长度22字节。
    // 又由于它的长度必须保存在[.ZIP file comment length]里面,所以它最长两个字节的最大值,即0xffff。
    // 再加上前面的22个字节就是EOCD的最大长度
    const val EOCD_MIN_LENGTH = 22
    const val SIZE_OF_COMMENT_LENGTH = 2
    private const val EOCD_MAX_LENGTH = 0xffff + EOCD_MIN_LENGTH
    private const val EOCD_MAGIC = 0x06054b50
    const val POSITION_OF_SOCD_OFFSET = 16
    const val CHANNEL_MAGIC = EOCD_MAGIC + 1
    const val SIG_MAGIC = "APK Sig Block 42"
    private val sReadBuffer = ByteBuffer.allocate(8).also { it.order(ByteOrder.LITTLE_ENDIAN) }

    fun readString(byteBuffer: ByteBuffer, length: Int): String {
        val byteArray = ByteArray(length)
        byteBuffer.get(byteArray)
        return String(byteArray)
    }

    @Throws(IOException::class)
    fun readString(file: RandomAccessFile, position: Long, length: Int): String? {
        val bytes = ByteArray(length)
        file.seek(position)
        file.read(bytes)
        return String(bytes)
    }

    @Throws(IOException::class)
    fun readLong(file: RandomAccessFile, position: Long): Long {
        sReadBuffer.clear()
        file.seek(position)
        file.read(sReadBuffer.array(), 0, Long.SIZE_BYTES)
        return sReadBuffer.long
    }

    @Throws(IOException::class)
    fun readInt(file: RandomAccessFile, position: Long): Int {
        sReadBuffer.clear()
        file.seek(position)
        file.read(sReadBuffer.array(), 0, Int.SIZE_BYTES)
        return sReadBuffer.int
    }

    @Throws(IOException::class)
    fun readShort(file: RandomAccessFile, position: Long): Short {
        sReadBuffer.clear()
        file.seek(position)
        file.read(sReadBuffer.array(), 0, Short.SIZE_BYTES)
        return sReadBuffer.short
    }

    fun safeClose(vararg closeables: Closeable?) {
        for (closeable in closeables) {
            try {
                closeable?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    fun findEocd(fileChannel: FileChannel): ByteBuffer? {
        // end of central directory record 是整个zip包的结尾
        // 而且它以0x06054b50这个魔数做起始,所以只需从后往前遍历找到这个魔数,即可截取整个EOCD
        //
        // [zip包其余内容]      ...
        //
        // [EOCD]              end of central dir signature (0x06054b50)
        //                     eocd其余部分
        return try {
            if (fileChannel.size() < EOCD_MIN_LENGTH) {
                return null
            }

            // .ZIP file comment length只有2字节,所以描述长度最多有0xffff
            // 然后加上eocd前固定的22个字节就得到eocd可能的最大长度
            val length =
                EOCD_MAX_LENGTH.toLong().coerceAtMost(fileChannel.size()).toInt()
            val buffer = ByteBuffer.allocate(length)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            fileChannel.read(buffer, fileChannel.size() - length)
            for (i in length - EOCD_MIN_LENGTH downTo 0) {
                if (buffer.getInt(i) == EOCD_MAGIC) {
                    buffer.position(i)
                    return buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                }
            }
            println("return null")
            null
        } finally {
            fileChannel.position(0)
        }
    }

    private fun extract(src: ByteBuffer, length: Int): ByteBuffer? {
        if (length > src.limit()) {
            return null
        }
        val bytes = ByteArray(length)
        src.get(bytes)
        val buffer = ByteBuffer.allocate(length)
        buffer.put(bytes)
        buffer.flip()
        return buffer
    }

    @Throws(IOException::class)
    fun copy(src: FileChannel, dst: FileChannel, length: Long) {
        val buffer = ByteBuffer.allocateDirect(4096)
        var total = 0L
        while (src.read(buffer) != -1) {
            buffer.flip()
            val totalReady = total + buffer.limit()
            if (totalReady < length) {
                dst.write(buffer)
            } else {
                val remainder = extract(buffer, (length - total).toInt())
                dst.write(remainder)
                break
            }
            buffer.clear()
            total = totalReady
        }
    }

    fun findSocdOffset(eocd: ByteBuffer): Int {
        eocd.position(POSITION_OF_SOCD_OFFSET)
        return eocd.int
    }

    @Throws(IOException::class)
    fun findSignBlock(file: RandomAccessFile, socdOffset: Int): Pair<Long, ByteBuffer>? {
        // [APK签名块]插入在[central directory]之前,而[central directory]的起始位置可以在[EOCD]的socdOffset部分读取
        //
        //   [zip包其余内容]      ...
        //
        //                       1. APK签名块大小(不包含自己的8个字节)        8字节
        //   [APK签名块]          2. ID-Value键值对                        大小可变
        //                       3. APK签名块大小(和第1部分相等)             8字节
        //                       4. 魔法数(固定为字符串"APK Sig Block 42")  16字节
        //                                      <--------------------
        // [central directory]   ...                                |
        //                                                          |
        //                       end of central dir signature       |
        //                       ...                                |
        //    [EOCD]             socdOffset  ------------------------
        //                       ...
        //
        val magicSize = SIG_MAGIC.toByteArray().size
        val magicPos = socdOffset.toLong() - magicSize
        val magic = readString(file, magicPos, magicSize)
        if (magic != SIG_MAGIC) {
            println("Not find APK SIG MAGIC")
            return null
        }
        // 再往前读8个字节应该可以读到APK签名块的大小
        val signBlockSize = readLong(file, magicPos - Long.SIZE_BYTES)
        // 由于APK签名块的大小不包含开头第1部分的8个字节
        // 所以再加上这8个字节才是APK签名块的真正大小
        val signBlockRealSize = signBlockSize + Long.SIZE_BYTES
        // 读取第1部分验证与前面读到的signBlockSize应该要相等
        val signBlockPos = socdOffset.toLong() - signBlockRealSize
        if (signBlockSize != readLong(file, signBlockPos)) {
            println("signBlockSize error")
            return null
        }
        val signBlock = ByteBuffer.allocate(signBlockRealSize.toInt())
        signBlock.order(ByteOrder.LITTLE_ENDIAN)
        file.seek(signBlockPos)
        file.read(signBlock.array())
        return Pair(signBlockPos, signBlock)
    }
}