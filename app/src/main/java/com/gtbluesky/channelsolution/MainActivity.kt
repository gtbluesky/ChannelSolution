package com.gtbluesky.channelsolution

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.gtbluesky.reader.ChannelReaderV1ViaComment
import com.gtbluesky.reader.ChannelReaderV1ViaFile
import com.gtbluesky.reader.ChannelReaderV23

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