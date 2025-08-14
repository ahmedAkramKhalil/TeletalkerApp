#include <jni.h>
#include <stdlib.h>
#include <unistd.h>
#include <android/log.h>

#define TAG "AudioInjectorJNI"

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_RootAudioInjector_initializeInjection(JNIEnv *env, jobject thiz) {
    // Execute Magisk module initialization script
    int result = system("/data/adb/modules/audio_injector/scripts/init_injection.sh");

    if (result != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Injection initialization failed");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_example_RootAudioInjector_injectAudioData(JNIEnv *env, jobject thiz,
                                                   jbyteArray audio_data, jint size) {
    jbyte* data = env->GetByteArrayElements(audio_data, NULL);

    // Write to named pipe created by Magisk module
    FILE* pipe = fopen("/data/local/tmp/audio_pipe", "wb");
    if (!pipe) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to open audio pipe");
        return -1;
    }

    int written = fwrite(data, 1, size, pipe);
    fclose(pipe);

    env->ReleaseByteArrayElements(audio_data, data, JNI_ABORT);
    return written;
}

JNIEXPORT void JNICALL
Java_com_example_RootAudioInjector_stopInjection(JNIEnv *env, jobject thiz) {
system("/data/adb/modules/audio_injector/scripts/stop_injection.sh");
}
}