#include <jni.h>

#include <android/log.h>
#include <sys/auxv.h>

#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#include "cpu_features.h"
#include "vulkan_devices.h"
#include "ggml-backend.h"
#include "llama.h"

namespace {

constexpr const char *kLogTag = "BruceLlama";

llama_model *asModel(jlong handle) { return reinterpret_cast<llama_model *>(handle); }

llama_context *asContext(jlong handle) { return reinterpret_cast<llama_context *>(handle); }

// Status codes shared with LlamaApi on the Kotlin side.
constexpr jint kPromptTooLong = -1;
constexpr jint kDecodeFailed = -2;
constexpr jint kToken = 0;
constexpr jint kEndOfGeneration = 1;
constexpr jint kContextFull = 2;

struct Generation {
    llama_context *context;
    const llama_vocab *vocab;
    llama_sampler *sampler;
    std::vector<char> piece;
};

Generation *asGeneration(jlong handle) { return reinterpret_cast<Generation *>(handle); }

int32_t usedPositions(llama_context *context) {
    return llama_memory_seq_pos_max(llama_get_memory(context), 0) + 1;
}

llama_sampler *newSampler(float temperature, uint32_t seed) {
    llama_sampler *chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(chain, llama_sampler_init_dist(seed));
    }
    return chain;
}

// Token pieces are raw bytes; a multi-byte UTF-8 character can span two pieces, so text
// decoding happens on the Kotlin side rather than through JNI's modified UTF-8.
void storePiece(Generation *generation, llama_token token) {
    generation->piece.resize(64);
    int32_t length = llama_token_to_piece(
            generation->vocab, token, generation->piece.data(),
            static_cast<int32_t>(generation->piece.size()), 0, false);
    if (length < 0) {
        generation->piece.resize(static_cast<size_t>(-length));
        length = llama_token_to_piece(
                generation->vocab, token, generation->piece.data(),
                static_cast<int32_t>(generation->piece.size()), 0, false);
    }
    generation->piece.resize(static_cast<size_t>(length));
}

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
Java_com_bizzeh_bruce_inference_LlamaNative_loadBackends(JNIEnv *env, jobject, jstring libraryDir) {
    static std::once_flag loaded;
    const char *dir = env->GetStringUTFChars(libraryDir, nullptr);
    std::call_once(loaded, [dir] {
        llama_log_set(forwardLog, nullptr);
        ggml_backend_load_all_from_path(dir);
        llama_backend_init();
    });
    env->ReleaseStringUTFChars(libraryDir, dir);
}

JNIEXPORT jstring JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_version(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_version());
}

JNIEXPORT jlong JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_loadModel(
        JNIEnv *env, jobject, jstring path, jintArray deviceIndices, jint gpuLayers) {
    // Always pass an explicit, NULL-terminated device list: with none, llama.cpp allocates
    // on every registered GPU even with zero offloaded layers, and some drivers crash there.
    // An empty list keeps the model on the CPU.
    const jsize deviceCount = env->GetArrayLength(deviceIndices);
    std::vector<jint> indices(static_cast<size_t>(deviceCount));
    env->GetIntArrayRegion(deviceIndices, 0, deviceCount, indices.data());
    std::vector<ggml_backend_dev_t> devices;
    for (jint index : indices) {
        devices.push_back(ggml_backend_dev_get(static_cast<size_t>(index)));
    }
    devices.push_back(nullptr);

    const char *nativePath = env->GetStringUTFChars(path, nullptr);
    llama_model_params params = llama_model_default_params();
    params.devices = devices.data();
    params.n_gpu_layers = gpuLayers;
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
        JNIEnv *, jobject, jlong model, jint contextLength, jint threads, jint batchSize) {
    llama_context_params params = llama_context_default_params();
    params.n_ctx = static_cast<uint32_t>(contextLength);
    params.n_batch = static_cast<uint32_t>(batchSize);
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

JNIEXPORT jint JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_deviceCount(JNIEnv *, jobject) {
    return static_cast<jint>(ggml_backend_dev_count());
}

// Returns {backend name, device name, device description}.
JNIEXPORT jobjectArray JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_deviceStrings(JNIEnv *env, jobject, jint index) {
    ggml_backend_dev_t device = ggml_backend_dev_get(static_cast<size_t>(index));
    const char *values[] = {
            ggml_backend_reg_name(ggml_backend_dev_backend_reg(device)),
            ggml_backend_dev_name(device),
            ggml_backend_dev_description(device),
    };
    jobjectArray result = env->NewObjectArray(3, env->FindClass("java/lang/String"), nullptr);
    for (jsize i = 0; i < 3; ++i) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(values[i]));
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_deviceType(JNIEnv *, jobject, jint index) {
    return static_cast<jint>(ggml_backend_dev_type(ggml_backend_dev_get(static_cast<size_t>(index))));
}

JNIEXPORT jlong JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_deviceMemoryBytes(JNIEnv *, jobject, jint index) {
    size_t free = 0;
    size_t total = 0;
    ggml_backend_dev_memory(ggml_backend_dev_get(static_cast<size_t>(index)), &free, &total);
    return static_cast<jlong>(total);
}

JNIEXPORT jint JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_vulkanDeviceApiVersion(JNIEnv *env, jobject, jstring deviceName) {
    const char *name = env->GetStringUTFChars(deviceName, nullptr);
    const uint32_t version = bruce::vulkanDeviceApiVersion(name);
    env->ReleaseStringUTFChars(deviceName, name);
    return static_cast<jint>(version);
}

// Formats a conversation with the model's own chat template, or ChatML when the model has none or
// llama.cpp does not recognise it. Returns {usedFallback (1 byte), formatted UTF-8 bytes...}, or
// null if even ChatML fails. Message text crosses JNI as UTF-8 bytes (.learnings/jni-text-as-utf8-bytes.md).
JNIEXPORT jbyteArray JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_formatChat(
        JNIEnv *env, jobject, jlong model, jobjectArray roles, jobjectArray contents, jboolean addAssistant) {
    const jsize count = env->GetArrayLength(roles);
    std::vector<std::string> roleText(static_cast<size_t>(count));
    std::vector<std::string> contentText(static_cast<size_t>(count));
    size_t totalBytes = 0;
    for (jsize i = 0; i < count; ++i) {
        auto role = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        const char *chars = env->GetStringUTFChars(role, nullptr);
        roleText[i] = chars;
        env->ReleaseStringUTFChars(role, chars);
        auto content = static_cast<jbyteArray>(env->GetObjectArrayElement(contents, i));
        const jsize length = env->GetArrayLength(content);
        contentText[i].resize(static_cast<size_t>(length));
        env->GetByteArrayRegion(content, 0, length, reinterpret_cast<jbyte *>(contentText[i].data()));
        totalBytes += contentText[i].size();
    }
    std::vector<llama_chat_message> messages(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        messages[i] = {roleText[i].c_str(), contentText[i].c_str()};
    }

    auto apply = [&](const char *tmpl, std::vector<char> &out) -> int32_t {
        out.resize(2 * totalBytes + 1024);
        int32_t length = llama_chat_apply_template(
                tmpl, messages.data(), messages.size(), addAssistant, out.data(), static_cast<int32_t>(out.size()));
        if (length > static_cast<int32_t>(out.size())) {
            out.resize(static_cast<size_t>(length));
            length = llama_chat_apply_template(
                    tmpl, messages.data(), messages.size(), addAssistant, out.data(), static_cast<int32_t>(out.size()));
        }
        return length;
    };

    std::vector<char> formatted;
    bool usedFallback = false;
    const char *modelTemplate = llama_model_chat_template(asModel(model), nullptr);
    int32_t length = modelTemplate == nullptr ? -1 : apply(modelTemplate, formatted);
    if (length < 0) {
        usedFallback = true;
        length = apply("chatml", formatted);
    }
    if (length < 0) {
        return nullptr;
    }
    jbyteArray result = env->NewByteArray(length + 1);
    const jbyte flag = usedFallback ? 1 : 0;
    env->SetByteArrayRegion(result, 0, 1, &flag);
    env->SetByteArrayRegion(result, 1, length, reinterpret_cast<const jbyte *>(formatted.data()));
    return result;
}

// The features the loaded CPU variant was compiled with, e.g. "DOTPROD", as "NAME=value".
JNIEXPORT jobjectArray JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_cpuBackendFeatures(JNIEnv *env, jobject) {
    std::vector<std::string> features;
    ggml_backend_reg_t cpu = ggml_backend_reg_by_name("CPU");
    auto getFeatures = cpu == nullptr ? nullptr : reinterpret_cast<ggml_backend_get_features_t>(
            ggml_backend_reg_get_proc_address(cpu, "ggml_backend_get_features"));
    if (getFeatures != nullptr) {
        for (ggml_backend_feature *feature = getFeatures(cpu); feature->name != nullptr; ++feature) {
            features.push_back(std::string(feature->name) + "=" + feature->value);
        }
    }
    jobjectArray result = env->NewObjectArray(
            static_cast<jsize>(features.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < features.size(); ++i) {
        env->SetObjectArrayElement(result, static_cast<jsize>(i), env->NewStringUTF(features[i].c_str()));
    }
    return result;
}

JNIEXPORT jlong JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_beginGeneration(
        JNIEnv *, jobject, jlong context, jfloat temperature, jint seed) {
    llama_context *ctx = asContext(context);
    llama_memory_clear(llama_get_memory(ctx), true);
    auto *generation = new Generation{
            ctx,
            llama_model_get_vocab(llama_get_model(ctx)),
            newSampler(temperature, static_cast<uint32_t>(seed)),
            {},
    };
    return reinterpret_cast<jlong>(generation);
}

JNIEXPORT jint JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_evaluatePrompt(
        JNIEnv *env, jobject, jlong handle, jbyteArray promptUtf8) {
    Generation *generation = asGeneration(handle);
    const jsize promptLength = env->GetArrayLength(promptUtf8);
    std::vector<char> prompt(static_cast<size_t>(promptLength));
    env->GetByteArrayRegion(promptUtf8, 0, promptLength, reinterpret_cast<jbyte *>(prompt.data()));

    const int32_t contextLength = static_cast<int32_t>(llama_n_ctx(generation->context));
    std::vector<llama_token> tokens(static_cast<size_t>(contextLength));
    const int32_t count = llama_tokenize(
            generation->vocab, prompt.data(), promptLength, tokens.data(), contextLength, true, true);
    if (count < 0 || count >= contextLength) {
        return kPromptTooLong;
    }
    // llama_decode aborts the process, rather than failing, if a batch exceeds n_batch.
    const int32_t batchSize = static_cast<int32_t>(llama_n_batch(generation->context));
    for (int32_t start = 0; start < count; start += batchSize) {
        const int32_t length = std::min(batchSize, count - start);
        if (llama_decode(generation->context, llama_batch_get_one(tokens.data() + start, length)) != 0) {
            return kDecodeFailed;
        }
    }
    return count;
}

JNIEXPORT jint JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_nextToken(JNIEnv *, jobject, jlong handle) {
    Generation *generation = asGeneration(handle);
    if (usedPositions(generation->context) >= static_cast<int32_t>(llama_n_ctx(generation->context))) {
        return kContextFull;
    }
    llama_token token = llama_sampler_sample(generation->sampler, generation->context, -1);
    if (llama_vocab_is_eog(generation->vocab, token)) {
        return kEndOfGeneration;
    }
    storePiece(generation, token);
    if (llama_decode(generation->context, llama_batch_get_one(&token, 1)) != 0) {
        return kDecodeFailed;
    }
    return kToken;
}

JNIEXPORT jbyteArray JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_takePiece(JNIEnv *env, jobject, jlong handle) {
    const std::vector<char> &piece = asGeneration(handle)->piece;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(piece.size()));
    env->SetByteArrayRegion(
            result, 0, static_cast<jsize>(piece.size()), reinterpret_cast<const jbyte *>(piece.data()));
    return result;
}

JNIEXPORT void JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_endGeneration(JNIEnv *, jobject, jlong handle) {
    Generation *generation = asGeneration(handle);
    llama_sampler_free(generation->sampler);
    delete generation;
}

JNIEXPORT jint JNICALL
Java_com_bizzeh_bruce_hardware_CpuFeaturesNative_detect(JNIEnv *, jobject) {
#if defined(__aarch64__)
    constexpr bool kArm64 = true;
#else
    constexpr bool kArm64 = false;
#endif
    return static_cast<jint>(bruce::decodeCpuFeatures(kArm64, getauxval(AT_HWCAP), getauxval(AT_HWCAP2)));
}

}  // extern "C"
