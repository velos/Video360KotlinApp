/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications from original source file: Removed the need for intent, and option to view in VR.
 * Converted file to Kotlin.
 */

package com.velosmobile.vr360kotlinapp.video360

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import kotlinx.android.synthetic.main.video_ui.view.*

/**
 * Contains a UI that can be part of a standard 2D Android Activity.
 *
 *
 * For 2D Activities, this View behaves like any other Android View. It receives events from the
 * media player, updates the UI, and forwards user input to the appropriate component.
 */
class VideoUiView
    (context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    // These UI elements are only useful when the app is displaying a video.
    private val uiUpdater = UiUpdater()

    // Since MediaPlayer lacks synchronization for internal events, it should only be accessed on the
    // main thread.
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Gets the listener used to update the seek bar's position on each new video frame.
     */
    val frameListener: SurfaceTexture.OnFrameAvailableListener
        get() = uiUpdater

    /**
     * Binds the media player in order to update video position if the Activity is showing a video.
     * This is also used to clear the bound mediaPlayer when the Activity exits to avoid trying to
     * access the mediaPlayer while it is in an invalid state.
     */
    @MainThread
    fun setMediaPlayer(mediaPlayer: MediaPlayer?) {
        this.mediaPlayer = mediaPlayer
        postInvalidate()
    }

    /** Installs the View's event handlers.  */
    public override fun onFinishInflate() {
        super.onFinishInflate()

        seekBar.setOnSeekBarChangeListener(SeekBarListener())
    }

    /** Updates the seek bar and status text.  */
    private inner class UiUpdater : SurfaceTexture.OnFrameAvailableListener {
        private var videoDurationMs = 0

        // onFrameAvailable is called on an arbitrary thread, but we can only access mediaPlayer on the
        // main thread.
        private val uiThreadUpdater = Runnable {

            val player = mediaPlayer ?: return@Runnable

            if (videoDurationMs == 0) {
                videoDurationMs = player.duration
                seekBar.max = videoDurationMs
            }
            val positionMs = player.currentPosition
            seekBar.progress = positionMs


            val seconds = "%.2f".format(positionMs / 1000f)
            val totalDurationSeconds = videoDurationMs / 1000

            val status = "$seconds / $totalDurationSeconds"
            statusText.text = status
        }

        @AnyThread
        override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
            post(uiThreadUpdater)
        }
    }

    /** Handles the user seeking to a new position in the video.  */
    private inner class SeekBarListener : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

            mediaPlayer?.let {
                if (fromUser) it.seekTo(progress) // else this was from the ActivityEventHandler.onNewFrame()'s seekBar.setProgress update.
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }
}
