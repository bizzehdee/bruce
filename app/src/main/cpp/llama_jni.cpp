#include <jni.h>

#include <android/log.h>

#include <string>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

namespace {

constexpr const char *kLogTag = "BruceLlama";

llama_model *asModel(jlong handle) { return reinterpret_cast<llama_model *>(handle); }

llama_context *asContext(jlong handle) { return reinterpret_cast<llama_context *>(handle); }

void forwardLog(ggml_log_level level, const char *text, void *) {
    if (level < GGML_LOG_LEVEL_WARN) {
        return;
    }
    const int priority = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN;
    __android_log_write(priority, kLogTag, text);
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_initBackend(JNIEnv *, jobject) {
    llama_log_set(forwardLog, nullptr);
    llama_backend_init();
}

JNIEXPORT jstring JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_version(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_version());
}

JNIEXPORT jlong JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_loadModel(JNIEnv *env, jobject, jstring path) {
    const char *nativePath = env->GetStringUTFChars(path, nullptr);
    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(nativePath, params);
    env->ReleaseStringUTFChars(path, nativePath);
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT void JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_freeModel(JNIEnv *, jobject, jlong model) {
    llama_model_free(asModel(model));
}

JNIEXPORT jlong JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_newContext(
        JNIEnv *, jobject, jlong model, jint contextLength, jint threads) {
    llama_context_params params = llama_context_default_params();
    params.n_ctx = static_cast<uint32_t>(contextLength);
    params.n_threads = threads;
    params.n_threads_batch = threads;
    return reinterpret_cast<jlong>(llama_init_from_model(asModel(model), params));
}

JNIEXPORT void JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_freeContext(JNIEnv *, jobject, jlong context) {
    llama_free(asContext(context));
}

JNIEXPORT jstring JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_modelDescription(JNIEnv *env, jobject, jlong model) {
    std::vector<char> buffer(256);
    const int32_t written = llama_model_desc(asModel(model), buffer.data(), buffer.size());
    return env->NewStringUTF(written < 0 ? "" : buffer.data());
}

JNIEXPORT jlong JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_modelParameterCount(JNIEnv *, jobject, jlong model) {
    return static_cast<jlong>(llama_model_n_params(asModel(model)));
}

JNIEXPORT jlong JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_modelSizeBytes(JNIEnv *, jobject, jlong model) {
    return static_cast<jlong>(llama_model_size(asModel(model)));
}

JNIEXPORT jint JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_modelTrainedContextLength(JNIEnv *, jobject, jlong model) {
    return llama_model_n_ctx_train(asModel(model));
}

JNIEXPORT jintArray JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_deviceTypes(JNIEnv *env, jobject) {
    const size_t count = ggml_backend_dev_count();
    std::vector<jint> types(count);
    for (size_t i = 0; i < count; ++i) {
        types[i] = static_cast<jint>(ggml_backend_dev_type(ggml_backend_dev_get(i)));
    }
    jintArray result = env->NewIntArray(static_cast<jsize>(count));
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(count), types.data());
    return result;
}

}  // extern "C"
