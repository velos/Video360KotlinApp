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

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLU.gluErrorString
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * GL utility methods.
 */
object Utils {
    private const val UTILS_TAG = "Video360.Utils"

    const val BYTES_PER_FLOAT = 4

    /**
     * Debug builds should fail quickly. Release versions of the app should have this disabled.
     */
    private const val HALT_ON_GL_ERROR = true

    /**
     * Checks GLES20.glGetError and fails quickly if the state isn't GL_NO_ERROR.
     */
    fun checkGlError() {
        var error = GLES20.glGetError()
        var lastError: Int
        if (error != GLES20.GL_NO_ERROR) {
            do {
                lastError = error
                Log.e(UTILS_TAG, "glError ${gluErrorString(lastError)}")
                error = GLES20.glGetError()
            } while (error != GLES20.GL_NO_ERROR)

            if (HALT_ON_GL_ERROR) {
                throw RuntimeException("glError ${gluErrorString(lastError)}")
            }
        }
    }

    /**
     * Builds a GL shader program from vertex & fragment shader code. The vertex and fragment shaders
     * are passed as arrays of strings in order to make debugging compilation issues easier.
     *
     * @param vertexCode   GLES20 vertex shader program.
     * @param fragmentCode GLES20 fragment shader program.
     */
    fun compileProgram(vertexCode: Array<String>, fragmentCode: Array<String>): Int {
        checkGlError()
        // prepare shaders and OpenGL program
        val vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        GLES20.glShaderSource(vertexShader, vertexCode.joinToString(separator = "\n"))
        GLES20.glCompileShader(vertexShader)
        checkGlError()

        val fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        GLES20.glShaderSource(fragmentShader, fragmentCode.joinToString(separator = "\n"))
        GLES20.glCompileShader(fragmentShader)
        checkGlError()

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)

        // Link and check for errors.
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val errorMsg =
                "Unable to link shader program: \n ${GLES20.glGetProgramInfoLog(program)}"
            Log.e(UTILS_TAG, errorMsg)
            if (HALT_ON_GL_ERROR) {
                throw RuntimeException(errorMsg)
            }
        }
        checkGlError()

        return program
    }

    /**
     * Allocates a FloatBuffer with the given data.
     */
    fun createBuffer(data: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(data.size * BYTES_PER_FLOAT)
        bb.order(ByteOrder.nativeOrder())
        val buffer = bb.asFloatBuffer()
        buffer.put(data)
        buffer.position(0)

        return buffer
    }

    /**
     * Creates a GL_TEXTURE_EXTERNAL_OES with default configuration of GL_LINEAR filtering and
     * GL_CLAMP_TO_EDGE wrapping.
     */
    fun glCreateExternalTexture(): Int {
        val texId = IntArray(1)
        GLES20.glGenTextures(1, IntBuffer.wrap(texId))
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
        )
        checkGlError()
        return texId[0]
    }
}
