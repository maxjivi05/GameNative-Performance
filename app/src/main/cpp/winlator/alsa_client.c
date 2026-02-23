#include <aaudio/AAudio.h>
#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <malloc.h>
#include <android/log.h>
#include <unistd.h>
#include <string.h>
#include <sched.h>

#define WAIT_COMPLETION_TIMEOUT 100 * 1000000L
#define LOG_TAG "AlsaClientJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, "System.out", __VA_ARGS__);

enum Format {U8, S16LE, S16BE, FLOATLE, FLOATBE};

typedef struct {
    uint8_t *buffer;
    size_t capacity_bytes;
    size_t write_pos_bytes;
    size_t read_pos_bytes;
    size_t available_bytes;
    int sample_rate;
    int frame_size_bytes;
    pthread_t consumer_thread;
    pthread_mutex_t mutex;
    pthread_cond_t cond_not_full;
    pthread_cond_t cond_not_empty;
    volatile int running;
} PacerContext;

static inline jlong ptr_to_jlong(void* ptr) {
    return (jlong)(uintptr_t)ptr;
}
static inline void* jlong_to_ptr(jlong handle) {
    return (void*)(uintptr_t)handle;
}

static aaudio_format_t toAAudioFormat(int format) {
    switch (format) {
        case FLOATLE:
        case FLOATBE:
            return AAUDIO_FORMAT_PCM_FLOAT;
        case U8:
            return AAUDIO_FORMAT_UNSPECIFIED;
        case S16LE:
        case S16BE:
        default:
            return AAUDIO_FORMAT_PCM_I16;
    }
}

static int get_bytes_per_frame(int format, int channelCount) {
    int bytes_per_sample = 0;
    switch (format) {
        case U8: bytes_per_sample = 1; break;
        case S16LE:
        case S16BE: bytes_per_sample = 2; break;
        case FLOATLE:
        case FLOATBE: bytes_per_sample = 4; break;
    }
    return bytes_per_sample * channelCount;
}

void *pacer_consumer_thread_func(void *arg) {
    PacerContext *ctx = (PacerContext*)arg;
    struct sched_param schedParams;
    schedParams.sched_priority = sched_get_priority_max(SCHED_FIFO);
    pthread_setschedparam(pthread_self(), SCHED_FIFO, &schedParams);

    LOGI("Pacer consumer thread started with high-precision timing and real-time priority.");

    struct timespec next_wakeup_time;
    clock_gettime(CLOCK_MONOTONIC, &next_wakeup_time);

    while (ctx->running != 0) {
        pthread_mutex_lock(&ctx->mutex);
        while (ctx->available_bytes == 0 && ctx->running == 1) {
            pthread_cond_wait(&ctx->cond_not_empty, &ctx->mutex);
            if (ctx->running != 1) break;
        }
        if (ctx->running != 1) {
            pthread_mutex_unlock(&ctx->mutex);
            continue;
        }

        int consume_chunk_frames = ctx->sample_rate / 100; // 10ms
        int consume_chunk_bytes = consume_chunk_frames * ctx->frame_size_bytes;
        if (consume_chunk_bytes == 0) consume_chunk_bytes = ctx->frame_size_bytes;
        if (consume_chunk_bytes > (int)ctx->available_bytes) consume_chunk_bytes = (int)ctx->available_bytes;

        ctx->read_pos_bytes = (ctx->read_pos_bytes + consume_chunk_bytes) % ctx->capacity_bytes;
        ctx->available_bytes -= consume_chunk_bytes;

        long duration_ns = (long)(((double)consume_chunk_bytes / ctx->frame_size_bytes / ctx->sample_rate) * 1000000000.0);
        next_wakeup_time.tv_nsec += duration_ns;
        if (next_wakeup_time.tv_nsec >= 1000000000) {
            next_wakeup_time.tv_sec++;
            next_wakeup_time.tv_nsec -= 1000000000;
        }

        pthread_cond_broadcast(&ctx->cond_not_full);
        pthread_mutex_unlock(&ctx->mutex);

        struct timespec current_time;
        clock_gettime(CLOCK_MONOTONIC, &current_time);
        if ((current_time.tv_sec > next_wakeup_time.tv_sec) ||
            (current_time.tv_sec == next_wakeup_time.tv_sec && current_time.tv_nsec > next_wakeup_time.tv_nsec)) {
            next_wakeup_time = current_time;
        }
        long sleep_ns = (next_wakeup_time.tv_sec - current_time.tv_sec) * 1000000000L + (next_wakeup_time.tv_nsec - current_time.tv_nsec);
        if (sleep_ns > 0) {
            struct timespec sleep_duration = {0, sleep_ns};
            nanosleep(&sleep_duration, NULL);
        }
    }
    LOGI("Pacer consumer thread exiting.");
    return NULL;
}

static AAudioStream *aaudioCreate(int32_t format, int8_t channelCount, int32_t sampleRate, int32_t bufferSize) {
    AAudioStreamBuilder *builder;
    AAudioStream *stream;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return NULL;
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setFormat(builder, toAAudioFormat(format));
    AAudioStreamBuilder_setChannelCount(builder, channelCount);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    if (AAudioStreamBuilder_openStream(builder, &stream) != AAUDIO_OK) {
        AAudioStreamBuilder_delete(builder);
        return NULL;
    }
    AAudioStream_setBufferSizeInFrames(stream, bufferSize);
    AAudioStreamBuilder_delete(builder);
    return stream;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_alsaserver_ALSAClient_simulatedCreate(JNIEnv *env, jobject obj, jint format,
                                                             jbyte channelCount, jint sampleRate, jint bufferSize) {
    PacerContext *ctx = (PacerContext*)calloc(1, sizeof(PacerContext));
    if (!ctx) return 0;
    ctx->frame_size_bytes = get_bytes_per_frame(format, channelCount);
    ctx->capacity_bytes = bufferSize * ctx->frame_size_bytes;
    ctx->sample_rate = sampleRate;
    ctx->buffer = (uint8_t*)malloc(ctx->capacity_bytes);
    if (!ctx->buffer) { free(ctx); return 0; }
    pthread_mutex_init(&ctx->mutex, NULL);
    pthread_cond_init(&ctx->cond_not_full, NULL);
    pthread_cond_init(&ctx->cond_not_empty, NULL);
    return ptr_to_jlong(ctx);
}

JNIEXPORT jlong JNICALL
Java_com_winlator_alsaserver_ALSAClient_create(JNIEnv *env, jobject obj, jint format,
                                                    jbyte channelCount, jint sampleRate, jint bufferSize) {
    void* stream = aaudioCreate(format, channelCount, sampleRate, bufferSize);
    return stream ? ptr_to_jlong(stream) : 0;
}

JNIEXPORT jint JNICALL
Java_com_winlator_alsaserver_ALSAClient_simulatedWrite(JNIEnv *env, jobject obj, jlong streamPtr, jobject buffer,
                                                            jint numFrames) {
    PacerContext *ctx = (PacerContext*)jlong_to_ptr(streamPtr);
    if (!ctx || ctx->running != 1) return -1;
    void *data = (*env)->GetDirectBufferAddress(env, buffer);
    int bytes_to_write = numFrames * ctx->frame_size_bytes;
    pthread_mutex_lock(&ctx->mutex);
    while ((ctx->capacity_bytes - ctx->available_bytes) < (size_t)bytes_to_write && ctx->running == 1) {
        pthread_cond_wait(&ctx->cond_not_full, &ctx->mutex);
    }
    if (ctx->running != 1) { pthread_mutex_unlock(&ctx->mutex); return -1; }
    size_t remaining_capacity = ctx->capacity_bytes - ctx->write_pos_bytes;
    if (remaining_capacity >= (size_t)bytes_to_write) {
        memcpy(ctx->buffer + ctx->write_pos_bytes, data, bytes_to_write);
        ctx->write_pos_bytes = (ctx->write_pos_bytes + bytes_to_write) % ctx->capacity_bytes;
    } else {
        memcpy(ctx->buffer + ctx->write_pos_bytes, data, remaining_capacity);
        memcpy(ctx->buffer, (uint8_t*)data + remaining_capacity, bytes_to_write - remaining_capacity);
        ctx->write_pos_bytes = bytes_to_write - remaining_capacity;
    }
    ctx->available_bytes += bytes_to_write;
    pthread_cond_signal(&ctx->cond_not_empty);
    pthread_mutex_unlock(&ctx->mutex);
    return numFrames;
}

JNIEXPORT jint JNICALL
Java_com_winlator_alsaserver_ALSAClient_write(JNIEnv *env, jobject obj, jlong streamPtr, jobject buffer,
                                                   jint numFrames) {
    AAudioStream *aaudioStream = (AAudioStream*)jlong_to_ptr(streamPtr);
    if (aaudioStream) return AAudioStream_write(aaudioStream, (*env)->GetDirectBufferAddress(env, buffer), numFrames, WAIT_COMPLETION_TIMEOUT);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_winlator_alsaserver_ALSAClient_simulatedStart(JNIEnv *env, jobject obj, jlong streamPtr) {
    PacerContext *ctx = (PacerContext*)jlong_to_ptr(streamPtr);
    if (!ctx) return;
    pthread_mutex_lock(&ctx->mutex);
    if (ctx->running == 0) {
        ctx->running = 1;
        pthread_create(&ctx->consumer_thread, NULL, pacer_consumer_thread_func, ctx);
    } else if (ctx->running == 2) {
        ctx->running = 1;
        pthread_cond_broadcast(&ctx->cond_not_empty);
    }
    pthread_mutex_unlock(&ctx->mutex);
}

JNIEXPORT jint JNICALL
Java_com_winlator_alsaserver_ALSAClient_start(JNIEnv *env, jobject obj, jlong streamPtr) {
    AAudioStream *aaudioStream = (AAudioStream*)jlong_to_ptr(streamPtr);
    return aaudioStream ? AAudioStream_requestStart(aaudioStream) : -1;
}

JNIEXPORT void JNICALL
Java_com_winlator_alsaserver_ALSAClient_simulatedFlush(JNIEnv *env, jobject obj, jlong streamPtr) {
    PacerContext *ctx = (PacerContext*)jlong_to_ptr(streamPtr);
    if (!ctx) return;
    pthread_mutex_lock(&ctx->mutex);
    ctx->read_pos_bytes = ctx->write_pos_bytes = ctx->available_bytes = 0;
    pthread_cond_broadcast(&ctx->cond_not_full);
    pthread_mutex_unlock(&ctx->mutex);
}

JNIEXPORT void JNICALL
Java_com_winlator_alsaserver_ALSAClient_flush(JNIEnv *env, jobject obj, jlong streamPtr) {
    AAudioStream *aaudioStream = (AAudioStream*)jlong_to_ptr(streamPtr);
    if (aaudioStream) {
        AAudioStream_requestFlush(aaudioStream);
        AAudioStream_waitForStateChange(aaudioStream, AAUDIO_STREAM_STATE_FLUSHING, NULL, WAIT_COMPLETION_TIMEOUT);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_alsaserver_ALSAClient_simulatedPause(JNIEnv *env, jobject obj, jlong streamPtr) {
    PacerContext *ctx = (PacerContext*)jlong_to_ptr(streamPtr);
    if (!ctx || ctx->running != 1) return;
    pthread_mutex_lock(&ctx->mutex);
    ctx->running = 2;
    pthread_cond_broadcast(&ctx->cond_not_full);
    pthread_cond_broadcast(&ctx->cond_not_empty);
    pthread_mutex_unlock(&ctx->mutex);
}

JNIEXPORT void JNICALL
Java_com_winlator_alsaserver_ALSAClient_pause(JNIEnv *env, jobject obj, jlong streamPtr) {
    AAudioStream *aaudioStream = (AAudioStream*)jlong_to_ptr(streamPtr);
    if (aaudioStream) {
        AAudioStream_requestPause(aaudioStream);
        AAudioStream_waitForStateChange(aaudioStream, AAUDIO_STREAM_STATE_PAUSING, NULL, WAIT_COMPLETION_TIMEOUT);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_alsaserver_ALSAClient_simulatedStop(JNIEnv *env, jobject obj, jlong streamPtr) {
    Java_com_winlator_alsaserver_ALSAClient_simulatedPause(env, obj, streamPtr);
}

JNIEXPORT void JNICALL
Java_com_winlator_alsaserver_ALSAClient_stop(JNIEnv *env, jobject obj, jlong streamPtr) {
    AAudioStream *aaudioStream = (AAudioStream*)jlong_to_ptr(streamPtr);
    if (aaudioStream) {
        AAudioStream_requestStop(aaudioStream);
        AAudioStream_waitForStateChange(aaudioStream, AAUDIO_STREAM_STATE_STOPPING, NULL, WAIT_COMPLETION_TIMEOUT);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_alsaserver_ALSAClient_simulatedClose(JNIEnv *env, jobject obj, jlong streamPtr) {
    PacerContext *ctx = (PacerContext*)jlong_to_ptr(streamPtr);
    if (!ctx) return;
    pthread_mutex_lock(&ctx->mutex);
    if (ctx->running == 0) { pthread_mutex_unlock(&ctx->mutex); return; }
    ctx->running = 0;
    pthread_cond_broadcast(&ctx->cond_not_empty);
    pthread_cond_broadcast(&ctx->cond_not_full);
    pthread_mutex_unlock(&ctx->mutex);
    if (ctx->consumer_thread) {
        pthread_join(ctx->consumer_thread, NULL);
        ctx->consumer_thread = 0;
    }
    pthread_mutex_destroy(&ctx->mutex);
    pthread_cond_destroy(&ctx->cond_not_full);
    pthread_cond_destroy(&ctx->cond_not_empty);
    free(ctx->buffer);
    free(ctx);
}

JNIEXPORT void JNICALL
Java_com_winlator_alsaserver_ALSAClient_close(JNIEnv *env, jobject obj, jlong streamPtr) {
    AAudioStream *aaudioStream = (AAudioStream*)jlong_to_ptr(streamPtr);
    if (aaudioStream) AAudioStream_close(aaudioStream);
}
