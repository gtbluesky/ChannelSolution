package com.gtbluesky.channel.writer

import com.gtbluesky.channel.util.ZipUtil
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 基于V1签名的渠道号方案
 * 将渠道号写入META-INF目录下的空文件的文件名中
 */
class ChannelWriterV1ViaFile(
    srcPath: String,
    destPath: String,
    channel: String
) : ChannelWriter(
    srcPath,
    destPath,
    channel
) {
    override fun writeChannel(): Boolean {
        if (channel.isEmpty()) {
            return false
        }
        var zipFile: ZipFile? = null
        var fos: FileOutputStream? = null
        var zos: ZipOutputStream? = null
        return try {
            zipFile = ZipFile(srcPath)
            fos = FileOutputStream(dstPath)
            zos = ZipOutputStream(fos)
            val channelEntry = ZipEntry("META-INF/channel_$channel")
            val mfEntry = zipFile.getEntry("AndroidManifest.xml")
            channelEntry.time = mfEntry.time
            channelEntry.timeLocal = mfEntry.timeLocal
            channelEntry.lastModifiedTime = mfEntry.lastModifiedTime
            zos.putNextEntry(channelEntry)
            zos.closeEntry()
            for (entry in zipFile.entries()) {
                val newEntry = ZipEntry(entry.name)
                newEntry.comment = entry.comment
                newEntry.method = entry.method
                if (entry.method == ZipEntry.STORED) {
                    newEntry.crc = entry.crc
                    newEntry.size = entry.size
                    newEntry.compressedSize = entry.compressedSize
                }
                newEntry.time = entry.time
                newEntry.timeLocal = entry.timeLocal
                newEntry.lastModifiedTime = entry.lastModifiedTime
                zos.putNextEntry(newEntry)
                zos.write(zipFile.getInputStream(entry).readAllBytes())
                zos.closeEntry()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            ZipUtil.safeClose(zos, fos, zipFile)
        }
    }
}