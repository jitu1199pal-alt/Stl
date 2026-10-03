#include <jni.h>
#include <android/log.h>
#include "CadModelReader.h"

#define TAG "CadJniBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static CadModelReader* gReader = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_cad_native_CadNativeEngine_loadCadFileFromFd(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint fileDescriptor,
    jboolean isDwg
) {
    delete gReader;
    gReader = new CadModelReader();

    bool success = gReader->loadFromFileDescriptor(fileDescriptor, isDwg);
    if (!success) {
        LOGE("Failed to parse CAD file via native bridge (fd: %d, isDwg: %d)", fileDescriptor, isDwg);
        return JNI_FALSE;
    }

    LOGI("Parsed %zu geometry primitives successfully", gReader->getSummary().primitives.size());
    return JNI_TRUE;
}

JNIEXPORT jobject JNICALL
Java_com_example_cad_native_CadNativeEngine_getDirectGeometryBuffer(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!gReader || gReader->getSummary().primitives.empty()) {
        return nullptr;
    }

    void* dataPtr = gReader->getSummary().primitives.data();
    jlong capacity = gReader->getSummary().primitives.size() * sizeof(RenderPrimitive);

    return env->NewDirectByteBuffer(dataPtr, capacity);
}

JNIEXPORT jint JNICALL
Java_com_example_cad_native_CadNativeEngine_getPrimitiveCount(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return gReader ? static_cast<jint>(gReader->getSummary().primitives.size()) : 0;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_cad_native_CadNativeEngine_getModelExtents(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!gReader) return nullptr;
    auto& s = gReader->getSummary();
    float scale = s.scaleFactorToMm;

    float bounds[6] = {
        static_cast<float>(s.minX * scale),
        static_cast<float>(s.maxX * scale),
        static_cast<float>(s.minY * scale),
        static_cast<float>(s.maxY * scale),
        static_cast<float>(s.insUnits),
        s.scaleFactorToMm
    };

    jfloatArray result = env->NewFloatArray(6);
    env->SetFloatArrayRegion(result, 0, 6, bounds);
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_cad_native_CadNativeEngine_destroyModel(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    delete gReader;
    gReader = nullptr;
}

}
