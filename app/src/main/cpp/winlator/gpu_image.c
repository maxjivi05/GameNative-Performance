#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/native_window.h>

#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <jni.h>
#include <string.h>
#include <stdbool.h>
#include <sys/mman.h>
#include <unistd.h>
#include <sys/syscall.h>

typedef struct native_handle {
    int version;        /* sizeof(native_handle_t) */
    int numFds;         /* number of file-descriptors at &data[0] */
    int numInts;        /* number of ints at &data[numFds] */
    int data[0];        /* numFds + numInts ints */
} native_handle_t;

#define HAL_PIXEL_FORMAT_RGBA_8888 1
#define HAL_PIXEL_FORMAT_BGRA_8888 5
#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, "System.out", __VA_ARGS__);

extern const native_handle_t* AHardwareBuffer_getNativeHandle(const AHardwareBuffer* buffer);

EGLImageKHR createImageKHR(AHardwareBuffer* hardwareBuffer, int textureId) {
    if (!hardwareBuffer) {
        printf("createImageKHR: Invalid AHardwareBuffer pointer\n");
        return NULL;
    }

    const EGLint attribList[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};

    AHardwareBuffer_acquire(hardwareBuffer);

    EGLClientBuffer clientBuffer = eglGetNativeClientBufferANDROID(hardwareBuffer);
    if (!clientBuffer) {
        printf("Failed to get native client buffer\n");
        AHardwareBuffer_release(hardwareBuffer);
        return NULL;
    }

    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        printf("Invalid EGLDisplay\n");
        AHardwareBuffer_release(hardwareBuffer);
        return NULL;
    }

    EGLImageKHR imageKHR = eglCreateImageKHR(eglDisplay, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attribList);
    if (!imageKHR) {
        printf("Failed to create EGLImageKHR\n");
        AHardwareBuffer_release(hardwareBuffer);
        return NULL;
    }

    glBindTexture(GL_TEXTURE_2D, textureId);
    if (glGetError() != GL_NO_ERROR) {
        printf("Failed to bind texture\n");
        eglDestroyImageKHR(eglDisplay, imageKHR);
        AHardwareBuffer_release(hardwareBuffer);
        return NULL;
    }

    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, imageKHR);
    if (glGetError() != GL_NO_ERROR) {
        printf("Failed to bind EGLImage to texture\n");
        eglDestroyImageKHR(eglDisplay, imageKHR);
        AHardwareBuffer_release(hardwareBuffer);
        return NULL;
    }

    glBindTexture(GL_TEXTURE_2D, 0);

    return imageKHR;
}

// Function to create a hardware buffer
AHardwareBuffer* createHardwareBuffer(int width, int height, bool cpuAccess, bool useHALPixelFormatBGRA8888) {
    AHardwareBuffer_Desc buffDesc = {0};
    buffDesc.width = width;
    buffDesc.height = height;
    buffDesc.layers = 1;
    buffDesc.usage = cpuAccess ? AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN : AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;
    buffDesc.format = useHALPixelFormatBGRA8888 ? HAL_PIXEL_FORMAT_BGRA_8888 : HAL_PIXEL_FORMAT_RGBA_8888;

    AHardwareBuffer *hardwareBuffer = NULL;
    if (AHardwareBuffer_allocate(&buffDesc, &hardwareBuffer) != 0) {
        printf("Failed to allocate AHardwareBuffer\n");
        return NULL;
    }

    return hardwareBuffer;
}

// JNI method to create a hardware buffer
JNIEXPORT jlong JNICALL
Java_com_winlator_renderer_GPUImage_createHardwareBuffer(JNIEnv *env,
                                                         jobject  obj,     // ‘this’
                                                         jshort   width,
                                                         jshort   height,
                                                         jboolean cpuAccess,
                                                         jboolean useBGRA8888)
{
    /* Allocate the buffer */
    AHardwareBuffer* buffer = createHardwareBuffer(
            (int)width,
            (int)height,
            cpuAccess  == JNI_TRUE,
            useBGRA8888 == JNI_TRUE);

    if (!buffer) {                       // allocation failed → null pointer
        return 0;
    }

    /* ------------------------------------------------------------------
     * Pass metadata back to Java
     * ------------------------------------------------------------------ */
    jclass cls = (*env)->GetObjectClass(env, obj);

    /* 1. Stride */
    AHardwareBuffer_Desc desc;
    AHardwareBuffer_describe(buffer, &desc);

    jmethodID midStride = (*env)->GetMethodID(env, cls, "setStride", "(S)V");
    (*env)->CallVoidMethod(env, obj, midStride, (jshort)desc.stride);

    /* 2. Native handle (fd 0) if present */
    const native_handle_t* handle = AHardwareBuffer_getNativeHandle(buffer);
    if (handle && handle->numFds > 0 && handle->data[0] != -1) {
        jmethodID midHandle = (*env)->GetMethodID(env, cls, "setNativeHandle", "(I)V");
        (*env)->CallVoidMethod(env, obj, midHandle, handle->data[0]);
    }

    /* Return the native pointer as jlong */
    return (jlong)buffer;
}

// JNI method to create an EGL image
JNIEXPORT jlong JNICALL
Java_com_winlator_renderer_GPUImage_createImageKHR(JNIEnv *env, jclass obj, jlong hardwareBufferPtr, jint textureId) {
    AHardwareBuffer* hardwareBuffer = (AHardwareBuffer*)hardwareBufferPtr;
    if (!hardwareBuffer) {
        printf("Invalid AHardwareBuffer pointer\n");
        return 0;
    }
    return (jlong)createImageKHR(hardwareBuffer, textureId);
}

// JNI method to destroy a hardware buffer
JNIEXPORT void JNICALL
Java_com_winlator_renderer_GPUImage_destroyHardwareBuffer(JNIEnv *env, jclass obj,
                                                          jlong hardwareBufferPtr, jboolean locked) {
    AHardwareBuffer* hardwareBuffer = (AHardwareBuffer*)hardwareBufferPtr;
    if (hardwareBuffer) {
        if (locked) {
            AHardwareBuffer_unlock(hardwareBuffer, NULL);
            locked = false;
        }
        AHardwareBuffer_release(hardwareBuffer);
    }
}

// JNI method to lock a hardware buffer
JNIEXPORT jobject JNICALL
Java_com_winlator_renderer_GPUImage_lockHardwareBuffer(JNIEnv *env, jclass obj,
                                                       jlong hardwareBufferPtr) {
    AHardwareBuffer* hardwareBuffer = (AHardwareBuffer*)hardwareBufferPtr;
    if (!hardwareBuffer) {
        printf("Invalid AHardwareBuffer pointer\n");
        return NULL;
    }

    void *virtualAddr;
    if (AHardwareBuffer_lock(hardwareBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, NULL, &virtualAddr) != 0) {
        printf("Failed to lock AHardwareBuffer\n");
        return NULL;
    }

    AHardwareBuffer_Desc buffDesc;
    AHardwareBuffer_describe(hardwareBuffer, &buffDesc);

    jlong size = buffDesc.stride * buffDesc.height * 4;
    jobject buffer = (*env)->NewDirectByteBuffer(env, virtualAddr, size);
    if (buffer == NULL) {
        printf("Failed to create Java ByteBuffer\n");
        AHardwareBuffer_unlock(hardwareBuffer, NULL);
    }

    return buffer;
}

// JNI method to destroy an EGL image
JNIEXPORT void JNICALL
Java_com_winlator_renderer_GPUImage_destroyImageKHR(JNIEnv *env, jclass obj, jlong imageKHRPtr) {
    EGLImageKHR imageKHR = (EGLImageKHR)imageKHRPtr;
    if (imageKHR) {
        EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        eglDestroyImageKHR(eglDisplay, imageKHR);
    }
}

int32_t createMemoryFd(const char *name, off_t size)
{
    int32_t fd = (int32_t)syscall(__NR_memfd_create, name, MFD_CLOEXEC);
    if (fd == -1) return -1;

    if (ftruncate(fd, size) == -1) {
        close(fd);
        return -1;
    }
    return fd;
}
