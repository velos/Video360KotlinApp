package com.velosmobile.vr360kotlinapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.video_ui.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Configures the MonoscopicView and launches 2D 360 video
 */
class MainActivity : AppCompatActivity() {

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configure the MonoscopicView which will render the video and UI.
        videoView.initialize(videoUiView)

        // Remember to place your 360 video file into res/raw/vrsample.mp4
        MainScope().launch {
            videoView.loadMedia(video = R.raw.vrsample, horizontalDegrees = 360f)
        }
    }

    override fun onResume() {
        super.onResume()
        videoView.onResume()
    }

    override fun onPause() {
        // MonoscopicView is a GLSurfaceView so it needs to pause & resume rendering. It's also
        // important to pause MonoscopicView's sensors & the video player.
        videoView.onPause()
        super.onPause()
    }

    /**
     * Remember to do videoView.destroy() in onDestroyView() instead if you are using a fragment.
     */
    override fun onDestroy() {
        videoView.destroy()
        super.onDestroy()
    }
}