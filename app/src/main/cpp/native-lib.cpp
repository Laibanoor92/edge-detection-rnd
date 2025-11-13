#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <vector>
#include <cstring>

#define LOG_TAG "NativeBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

jbyteArray createByteArray(JNIEnv* env, const std::vector<uint8_t>& data) {
    jbyteArray array = env->NewByteArray(static_cast<jsize>(data.size()));
    if (!array) {
        LOGE("Failed to allocate jbyteArray of size %zu", data.size());
        return nullptr;
    }
    env->SetByteArrayRegion(array, 0, static_cast<jsize>(data.size()), reinterpret_cast<const jbyte*>(data.data()));
    return array;
}

bool copyByteArrayToMat(JNIEnv* env, jbyteArray input, cv::Mat& destination) {
    const jsize length = env->GetArrayLength(input);
    const size_t expected = destination.total() * destination.channels();
    if (static_cast<size_t>(length) != expected) {
        LOGE("Unexpected byte array size: %d expected: %zu", length, expected);
        return false;
    }

    jboolean isCopy = JNI_FALSE;
    jbyte* srcData = env->GetByteArrayElements(input, &isCopy);
    if (!srcData) {
        LOGE("Unable to access input byte array");
        return false;
    }

    std::memcpy(destination.data, srcData, expected);
    env->ReleaseByteArrayElements(input, srcData, JNI_ABORT);
    return true;
}

} // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_minimalnativeapp_nativebridge_NativeBridge_processFrame(
        JNIEnv* env,
        jobject /* thiz */,
        jbyteArray frameData,
        jint width,
        jint height) {
    if (!frameData || width <= 0 || height <= 0) {
        LOGE("Invalid input to processFrame: frameData=%p width=%d height=%d", frameData, width, height);
        return nullptr;
    }

    cv::Mat rgba(static_cast<int>(height), static_cast<int>(width), CV_8UC4);
    if (!copyByteArrayToMat(env, frameData, rgba)) {
        return nullptr;
    }

    cv::Mat gray;
    cv::cvtColor(rgba, gray, cv::COLOR_RGBA2GRAY);

    cv::Mat blurred;
    cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 1.5);

    cv::Mat edges;
    cv::Canny(blurred, edges, 80.0, 160.0);

    cv::Mat edgesRgba;
    cv::cvtColor(edges, edgesRgba, cv::COLOR_GRAY2RGBA);

    std::vector<uint8_t> output(edgesRgba.total() * static_cast<size_t>(edgesRgba.channels()));
    std::memcpy(output.data(), edgesRgba.data, output.size());

    return createByteArray(env, output);
}#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_minimalnativeapp_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string message = "Hello from native C++";
    return env->NewStringUTF(message.c_str());
}
