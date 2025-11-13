package com.example.minimalnativeapp.nativebridge

object NativeBridge {

    // JNI: Java_com_example_minimalnativeapp_nativebridge_NativeBridge_processFrame
    external fun processFrame(data: ByteArray, width: Int, height: Int): ByteArray

    init {
        System.loadLibrary("native-lib")
    }
}
