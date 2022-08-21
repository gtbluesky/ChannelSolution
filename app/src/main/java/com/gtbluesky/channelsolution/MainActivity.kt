package com.gtbluesky.channelsolution

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.gtbluesky.channel.reader.ChannelReaderV23

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "GT_JAVA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "channel=${ChannelReaderV23(this.applicationInfo.sourceDir).readChannel()}")
    }
}