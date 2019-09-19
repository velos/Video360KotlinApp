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
 * Modifications from original source file: Converted to Kotlin.
 */

package com.velosmobile.vr360kotlinapp.rendering

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import androidx.annotation.AnyThread
import com.velosmobile.vr360kotlinapp.rendering.Utils.checkGlError
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Controls and renders the GL Scene.
 *
 * This class is used by MonsocopicView. It renders the display mesh, UI
 * and controller reticle as required.
 */
class SceneRenderer constructor(
    // Used to notify clients that displayTexture has a new frame. This requires synchronized access.
    @AnyThread
    var externalFrameListener: OnFrameAvailableListener?
) {

    private var displayTexId: Int = 0
    private lateinit var displayTexture: SurfaceTexture

    // This is the primary interface between the Media Player and the GL Scene.
    private val frameAvailable = AtomicBoolean()

    // GL components for the mesh that display the media. displayMesh should only be accessed on the
    // GL Thread, but requestedDisplayMesh needs synchronization.
    private var displayMesh: Mesh? = null
    private var requestedDisplayMesh: Mesh? = null

    // Controller components.
    // This is accessed on the binder & GL Threads.
    private val controllerOrientationMatrix = FloatArray(16)

    /**
     * Performs initialization on the GL thread. The scene isn't fully initialized until
     * glConfigureScene() completes successfully.
     */
    fun glInit() {
        checkGlError()
        Matrix.setIdentityM(controllerOrientationMatrix, 0)

        // Set the background frame color. This is only visible if the display mesh isn't a full sphere.
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
        checkGlError()

        // Create the texture used to render each frame of video.
        displayTexId = Utils.glCreateExternalTexture()
        displayTexture = SurfaceTexture(displayTexId)
        checkGlError()

        // When the video decodes a new frame, tell the GL thread to update the image.
        displayTexture.setOnFrameAvailableListener { surfaceTexture ->
            frameAvailable.set(true)

            synchronized(this@SceneRenderer) {
                externalFrameListener?.onFrameAvailable(surfaceTexture)
            }
        }
    }

    /**
     * Creates the Surface & Mesh used by the MediaPlayer to render video.
     * Returns a Surface that can be passed to [android.media.MediaPlayer.setSurface]
     * @param width passed to [SurfaceTexture.setDefaultBufferSize]
     * @param height passed to [SurfaceTexture.setDefaultBufferSize]
     * @param mesh [Mesh] used to display video
     */
    @AnyThread
    @Synchronized
    fun createDisplay(width: Int, height: Int, mesh: Mesh): Surface? {
        if (displayTexture == null) {
            Log.e(SCENERENDERER_TAG, ".createDisplay called before GL Initialization completed.")
            return null
        }

        requestedDisplayMesh = mesh

        displayTexture.setDefaultBufferSize(width, height)
        return Surface(displayTexture)
    }

    /**
     * Configures any late-initialized components.
     *
     * Since the creation of the Mesh can depend on disk access, this configuration needs to run
     * during each drawFrame to determine if the Mesh is ready yet. This also supports replacing an
     * existing mesh while the app is running.
     *
     */
    @Synchronized
    private fun glConfigureScene(): Boolean {

        if (displayMesh == null && requestedDisplayMesh == null) {
            // The scene isn't ready and we don't have enough information to configure it.
            return false
        }

        // The scene is ready to be drawn and we don't need to change it so we can glDraw it.
        if (requestedDisplayMesh == null) {
            return true
        }

        // Configure or reconfigure the scene.

        displayMesh?.glShutdown()

        displayMesh = requestedDisplayMesh
        requestedDisplayMesh = null
        displayMesh?.glInit(displayTexId)

        return true
    }

    /**
     * Draws the scene with a given eye pose and type.
     *
     * @param viewProjectionMatrix 16 element GL matrix.
     * @param eyeType an [com.google.vr.sdk.base.Eye.Type] value
     */
    fun glDrawFrame(viewProjectionMatrix: FloatArray, eyeType: Int) {
        if (!glConfigureScene()) {
            // displayMesh isn't ready.
            return
        }

        // glClear isn't strictly necessary when rendering fully spherical panoramas, but it can improve
        // performance on tiled renderers by causing the GPU to discard previous data.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        checkGlError()

        // The uiQuad uses alpha.
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_BLEND)

        if (frameAvailable.compareAndSet(true, false)) {
            displayTexture.updateTexImage()
            checkGlError()
        }

        displayMesh?.glDraw(viewProjectionMatrix, eyeType)
    }

    companion object {
        private const val SCENERENDERER_TAG = "SceneRenderer"
    }
}
