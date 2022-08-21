package com.gtbluesky.channel.writer

class MainClass {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isNotEmpty() && args[0] == "-h") {
                println("Please input zip path!")
                return
            }
            val inIndex = args.indexOf("-i")
            val outIndex = args.indexOf("-o")
            val channelIndex = args.indexOf("-c")
            if (inIndex < 0 || inIndex + 1 >= args.size) {
                println("-i param error!")
                return
            }
            if (outIndex < 0 || outIndex + 1 >= args.size) {
                println("-o param error!")
                return
            }
            if (channelIndex < 0 || channelIndex + 1 >= args.size) {
                println("-c param error!")
                return
            }
            val srcPath = args[inIndex + 1]
            val destPath = args[outIndex + 1]
            val channel = args[channelIndex + 1]
            ChannelWriterV23(srcPath, destPath, channel).writeChannel()
        }
    }
}