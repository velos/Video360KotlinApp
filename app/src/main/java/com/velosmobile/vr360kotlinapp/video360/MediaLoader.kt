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
 * Modifications from original source file: Removed the need for intent and hardcoded sample file.
 * Also removed options to show VR Image. Converted file to Kotlin.
 */

package com.velosmobile.vr360kotlinapp.video360

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RawRes
import com.velosmobile.vr360kotlinapp.rendering.Mesh
import com.velosmobile.vr360kotlinapp.rendering.SceneRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaLoader currently loads a hardcoded VR Sample video.
 *
 *
 * The process to load media requires multiple threads since the media is read from a
 * background thread, but it needs to be loaded into the GL scene only after GL initialization is
 * complete.
 *
 * Modifications from original source file: Removed the need for intent and hardcoded sample file.
 * Also removed options to show VR Image.
 *
 */
class MediaLoader(private val context: Context) {
    // This can be replaced by any media player that renders to a Surface. In a real app, this
    // media player would be separated from the rendering code. It is left in this class for
    // simplicity.
    // This should be set or cleared in a synchronized manner.
    private var mediaPlayer: MediaPlayer? = null
    // If the video or image fails to load, a placeholder panorama is rendered with error text.
    private var errorText: String = ""

    // Due to the slow loading media times, it's possible to tear down the app before mediaPlayer is
    // ready. In that case, abandon all the pending work.
    // This should be set or cleared in a synchronized manner.
    private var isDestroyed = false

    // The type of mesh created depends on the type of media.
    private var mesh = Mesh.createUvSphere(
        SPHERE_RADIUS_METERS,
        DEFAULT_SPHERE_ROWS,
        DEFAULT_SPHERE_COLUMNS,
        DEFAULT_SPHERE_VERTICAL_DEGREES,
        DEFAULT_SPHERE_HORIZONTAL_DEGREES,
        STEREO_FORMAT
    )
    // The sceneRenderer is set after GL initialization is complete.
    private var sceneRenderer: SceneRenderer? = null
    // The displaySurface is configured after both GL initialization and media loading.
    private var displaySurface: Surface? = null

    private val paint = Paint()

    /**
     * Loads a sample VR video in 2D.
     */
    suspend fun loadVrVideo(
        uiView: VideoUiView,
        @RawRes video: Int,
        horizontalDegrees: Float
    ) {
        withContext(Dispatchers.IO) {
            loadMedia(video, horizontalDegrees)
        }
        withContext(Dispatchers.Main) {
            uiView.setMediaPlayer(mediaPlayer)
        }
    }

    /**
     * Notifies MediaLoader that GL components have initialized.
     */
    fun onGlSceneReady(sceneRenderer: SceneRenderer) {
        this.sceneRenderer = sceneRenderer
        displayWhenReady()
    }

    /**
     * Helper class to media loading. Should run in the background.
     */
    private fun loadMedia(video: Int, horizontalDegrees: Float) {
        try {
            val mp = MediaPlayer.create(context, video)
            synchronized(this@MediaLoader) {
                // This needs to be synchronized with the methods that could clear mediaPlayer.
                mediaPlayer = mp
            }
        } catch (e: Exception) {
            errorText = "Error loading 360 video: $e"
            Log.e(MEDIALOADER_TAG, errorText)
        }

        displayWhenReady(horizontalDegrees = horizontalDegrees)
    }

    /**
     * Creates the 3D scene and load the media after sceneRenderer & mediaPlayer are ready. This can
     * run on the GL Thread or a background thread.
     */
    @AnyThread
    @Synchronized
    private fun displayWhenReady(horizontalDegrees: Float = DEFAULT_SPHERE_HORIZONTAL_DEGREES) {

        mesh = Mesh.createUvSphere(
            SPHERE_RADIUS_METERS,
            DEFAULT_SPHERE_ROWS,
            DEFAULT_SPHERE_COLUMNS,
            DEFAULT_SPHERE_VERTICAL_DEGREES,
            horizontalDegrees,
            Mesh.MEDIA_MONOSCOPIC
        )

        if (isDestroyed) {
            // This only happens when the Activity is destroyed immediately after creation.
            mediaPlayer?.let {
                it.release()
                mediaPlayer = null
            }
            return
        }

        if (displaySurface != null) {
            // Avoid double initialization caused by sceneRenderer & mediaPlayer being initialized before
            // displayWhenReady is executed.
            return
        }

        if (errorText.isEmpty() && mediaPlayer == null || sceneRenderer == null) {
            // Wait for everything to be initialized.
            return
        }

        val player = mediaPlayer

        val renderer = sceneRenderer

        // The important methods here are the setSurface & lockCanvas calls. These will have to happen
        // after the GLView is created.
        if (player != null) {
            // For videos, attach the displaySurface and mediaPlayer.

            if (renderer != null) {
                displaySurface = renderer.createDisplay(
                    player.videoWidth, player.videoHeight, mesh
                )

                with(player) {
                    setSurface(displaySurface)
                    // Start playback.
                    isLooping = true
                    start()
                }
            }

        } else {
            // Handle the error case by creating a placeholder panorama.
            mesh = Mesh.createUvSphere(
                SPHERE_RADIUS_METERS,
                DEFAULT_SPHERE_ROWS,
                DEFAULT_SPHERE_COLUMNS,
                DEFAULT_SPHERE_VERTICAL_DEGREES,
                DEFAULT_SPHERE_HORIZONTAL_DEGREES,
                Mesh.MEDIA_MONOSCOPIC
            )

            // 4k x 2k is a good default resolution for monoscopic panoramas.
            displaySurface = sceneRenderer?.createDisplay(
                2 * DEFAULT_SURFACE_HEIGHT_PX,
                DEFAULT_SURFACE_HEIGHT_PX, mesh
            )

            // Render placeholder grid and error text.
            displaySurface?.apply {
                val c = lockCanvas(null)
                renderEquirectangularGrid(
                    c,
                    errorText,
                    paint
                )
                unlockCanvasAndPost(c)
            }

        }
    }

    @MainThread
    @Synchronized
    fun pause() {
        mediaPlayer?.pause()
    }

    @MainThread
    @Synchronized
    fun resume() {
        mediaPlayer?.start()
    }

    /**
     * Tears down MediaLoader and prevents further work from happening.
     */
    @MainThread
    @Synchronized
    fun destroy() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isDestroyed = true
    }

    companion object {

        private const val MEDIALOADER_TAG = "MediaLoader"

        private const val DEFAULT_SURFACE_HEIGHT_PX = 2048

        /**
         * A spherical mesh for video should be large enough that there are no stereo artifacts.
         */
        private const val SPHERE_RADIUS_METERS = 50f

        /**
         * These should be configured based on the video type. But this sample assumes 360 video.
         */
        private const val DEFAULT_SPHERE_VERTICAL_DEGREES = 180f
        private const val DEFAULT_SPHERE_HORIZONTAL_DEGREES = 360f

        /**
         * The 360 x 180 sphere has 15 degree quads. Increase these if lines in your video look wavy.
         */
        private const val DEFAULT_SPHERE_ROWS = 12
        private const val DEFAULT_SPHERE_COLUMNS = 24
        private const val STEREO_FORMAT = 0

        /**
         * Renders a placeholder grid with optional error text.
         */
        private fun renderEquirectangularGrid(
            canvas: Canvas,
            message: String?,
            paint: Paint
        ) {
            // Configure the grid. Each square will be 15 x 15 degrees.
            val width = canvas.width
            val height = canvas.height
            // This assumes that video was shot in 4k.
            val majorWidth = width / 256f
            val minorWidth = width / 1024f

            // Draw a black ground & gray sky background
            paint.color = Color.BLACK
            canvas.drawRect(0f, (height / 2).toFloat(), width.toFloat(), height.toFloat(), paint)
            paint.color = Color.GRAY
            canvas.drawRect(0f, 0f, width.toFloat(), (height / 2).toFloat(), paint)

            // Render the grid lines.
            paint.color = Color.WHITE

            for (i in 0 until DEFAULT_SPHERE_COLUMNS) {
                val x = width * i / DEFAULT_SPHERE_COLUMNS
                paint.strokeWidth = (if (i % 3 == 0) majorWidth else minorWidth)
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
            }

            for (i in 0 until DEFAULT_SPHERE_ROWS) {
                val y = height * i / DEFAULT_SPHERE_ROWS
                paint.strokeWidth = (if (i % 3 == 0) majorWidth else minorWidth)
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
            }

            // Render optional text.
            if (message != null) {
                paint.textSize = (height / 64).toFloat()
                paint.color = Color.RED
                val textWidth = paint.measureText(message)

                canvas.drawText(
                    message,
                    width / 2 - textWidth / 2, // Horizontally center the text.
                    (9 * height / 16).toFloat(), // Place it slightly below the horizon for better contrast.
                    paint
                )
            }
        }
    }
}
