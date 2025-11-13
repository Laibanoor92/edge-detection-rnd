package com.example.minimalnativeapp

object NativeBridge {

    // JNI: Java_com_example_minimalnativeapp_NativeBridge_processFrame
    external fun processFrame(data: ByteArray, width: Int, height: Int): ByteArray

    init {
        System.loadLibrary("native-lib")
    }
}
