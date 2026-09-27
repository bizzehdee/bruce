#include <jni.h>

#include "llama.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_version(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_version());
}
