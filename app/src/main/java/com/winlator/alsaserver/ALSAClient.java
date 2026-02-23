package com.winlator.alsaserver;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.winlator.container.Container;
import com.winlator.core.KeyValueSet;
import com.winlator.math.Mathf;
import com.winlator.sysvshm.SysVSharedMemory;
import com.winlator.xenvironment.ImageFs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public class ALSAClient {
    private static short framesPerBuffer = 256;
    private ByteBuffer auxBuffer;
    private int bufferCapacity;
    private int bufferSize;
    private byte frameBytes;
    protected final Options options;
    private int position;
    private ByteBuffer sharedBuffer;
    private DataType dataType = DataType.U8;
    private AudioTrack audioTrack = null;
    private byte channels = 2;
    private int sampleRate = 0;
    private short previousUnderrunCount = 0;
    private String containerVariant = null;

    private long streamPtr = 0;
    private long mirrorStreamPtr = 0;
    private boolean reflectorMode = false;
    private boolean playing = false;
    private final AtomicBoolean mirrorRebuildPending = new AtomicBoolean(false);

    static {
        System.loadLibrary("winlator");
    }

    public enum DataType {
        U8(1),
        S16LE(2),
        S16BE(2),
        FLOATLE(4),
        FLOATBE(4);

        public final byte byteCount;

        DataType(int byteCount) {
            this.byteCount = (byte) byteCount;
        }
    }

    public static class Options {
        public short latencyMillis = 40;
        public byte performanceMode = 0;
        public float volume = 1.0f;
        public boolean reflectorMode = false;

        public static Options fromKeyValueSet(KeyValueSet config) {
            Options options = new Options();
            if (config == null || config.isEmpty()) {
                return options;
            }
            switch (config.get("performanceMode", "0")) {
                case "0": options.performanceMode = (byte) 0; break;
                case "1": options.performanceMode = (byte) 1; break;
                case "2": options.performanceMode = (byte) 2; break;
            }
            options.volume = config.getFloat("volume", 1.0f);
            options.latencyMillis = (short) config.getInt("latencyMillis", 40);
            return options;
        }
    }

    public ALSAClient(Options options, String containerVariant) {
        this.options = options;
        this.containerVariant = containerVariant;
        this.reflectorMode = options.reflectorMode;
    }

    public void release() {
        if (this.sharedBuffer != null) {
            SysVSharedMemory.unmapSHMSegment(this.sharedBuffer, this.sharedBuffer.capacity());
            this.sharedBuffer = null;
        }

        if (reflectorMode) {
            if (streamPtr > 0) {
                simulatedStop(streamPtr);
                simulatedClose(streamPtr);
            }
            if (mirrorStreamPtr > 0) {
                stop(mirrorStreamPtr);
                close(mirrorStreamPtr);
            }
        } else {
            AudioTrack audioTrack = this.audioTrack;
            if (audioTrack != null) {
                audioTrack.pause();
                this.audioTrack.flush();
                this.audioTrack.release();
                this.audioTrack = null;
            }
        }
        playing = false;
        streamPtr = 0;
        mirrorStreamPtr = 0;
    }

    public static int getPCMEncoding(DataType dataType) {
        switch (dataType) {
            case U8: return AudioFormat.ENCODING_PCM_8BIT;
            case S16LE:
            case S16BE: return AudioFormat.ENCODING_PCM_16BIT;
            case FLOATLE:
            case FLOATBE: return AudioFormat.ENCODING_PCM_FLOAT;
            default: return AudioFormat.ENCODING_DEFAULT;
        }
    }

    public static int getChannelConfig(int channels) {
        return (channels <= 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
    }

    public void prepare() {
        this.position = 0;
        this.previousUnderrunCount = (short) 0;
        this.frameBytes = (byte) (this.channels * this.dataType.byteCount);
        release();

        if (isValidBufferSize()) {
            if (reflectorMode) {
                streamPtr = simulatedCreate(this.dataType.ordinal(), this.channels, this.sampleRate, this.bufferSize);
                mirrorStreamPtr = create(this.dataType.ordinal(), this.channels, this.sampleRate, this.bufferSize);
            } else {
                AudioFormat format = new AudioFormat.Builder().setEncoding(getPCMEncoding(this.dataType)).setSampleRate(this.sampleRate).setChannelMask(getChannelConfig(this.channels)).build();
                AudioTrack build = new AudioTrack.Builder().setPerformanceMode(this.options.performanceMode).setAudioFormat(format).setBufferSizeInBytes(getBufferSizeInBytes()).build();
                this.audioTrack = build;
                this.bufferCapacity = build.getBufferCapacityInFrames();
                float f = this.options.volume;
                if (f != 1.0f) {
                    this.audioTrack.setVolume(f);
                }
            }
            start();
        }
    }

    public void start() {
        if (reflectorMode) {
            if (streamPtr > 0 && !playing) {
                simulatedStart(streamPtr);
                if (mirrorStreamPtr > 0) start(mirrorStreamPtr);
                playing = true;
            }
        } else {
            AudioTrack audioTrack = this.audioTrack;
            if (audioTrack != null && audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                this.audioTrack.play();
            }
        }
    }

    public void stop() {
        if (reflectorMode) {
            if (streamPtr > 0 && playing) {
                simulatedStop(streamPtr);
                if (mirrorStreamPtr > 0) stop(mirrorStreamPtr);
                playing = false;
            }
        } else {
            AudioTrack audioTrack = this.audioTrack;
            if (audioTrack != null) {
                audioTrack.stop();
                this.audioTrack.flush();
            }
        }
    }

    public void pause() {
        if (reflectorMode) {
            if (streamPtr > 0) {
                simulatedPause(streamPtr);
                if (mirrorStreamPtr > 0) pause(mirrorStreamPtr);
                playing = false;
            }
        } else {
            AudioTrack audioTrack = this.audioTrack;
            if (audioTrack != null) {
                audioTrack.pause();
            }
        }
    }

    public void drain() {
        if (reflectorMode) {
            if (streamPtr > 0) {
                simulatedFlush(streamPtr);
                if (mirrorStreamPtr > 0) flush(mirrorStreamPtr);
            }
        } else {
            AudioTrack audioTrack = this.audioTrack;
            if (audioTrack != null) {
                audioTrack.flush();
            }
        }
    }

    public void writeDataToTrack(ByteBuffer data) {
        if (dataType == DataType.S16LE || dataType == DataType.FLOATLE) {
            data.order(ByteOrder.LITTLE_ENDIAN);
        } else if (dataType == DataType.S16BE || dataType == DataType.FLOATBE) {
            data.order(ByteOrder.BIG_ENDIAN);
        }

        if (reflectorMode) {
            if (streamPtr > 0 && playing) {
                ByteBuffer mirrorData = data.duplicate().order(data.order());
                int numFrames = data.remaining() / frameBytes;
                int framesWritten = simulatedWrite(streamPtr, data, numFrames);
                if (mirrorStreamPtr > 0) {
                    int writeResult = write(mirrorStreamPtr, mirrorData, numFrames);
                    if (writeResult < 0 && mirrorRebuildPending.compareAndSet(false, true)) {
                        Log.w("ALSAClient", "AAudio mirror stream error (" + writeResult + "), triggering rebuild");
                        new Thread(this::onAudioDeviceChanged).start();
                    }
                }
                if (framesWritten > 0) this.position += (framesWritten * frameBytes);
                data.rewind();
            }
        } else {
            if (this.audioTrack != null) {
                data.position(0);
                do {
                    try {
                        int bytesWritten = this.audioTrack.write(data, data.remaining(), AudioTrack.WRITE_BLOCKING);
                        if (bytesWritten < 0) {
                            break;
                        } else {
                            increaseBufferSizeIfUnderrunOccurs();
                        }
                    } catch (Exception e) {}
                } while (data.position() != data.limit());
                this.position += data.position();
                data.rewind();
            }
        }
    }

    public void onAudioDeviceChanged() {
        if (!reflectorMode) return;

        Log.i("ALSAClient", "Audio device change detected, rebuilding mirror stream...");
        try {
            long oldPtr = mirrorStreamPtr;
            mirrorStreamPtr = 0;
            if (oldPtr > 0) {
                stop(oldPtr);
                close(oldPtr);
            }

            final int MAX_RETRIES = 5;
            for (int i = 0; i < MAX_RETRIES; i++) {
                long newStreamPtr = create(dataType.ordinal(), channels, sampleRate, bufferSize);
                if (newStreamPtr > 0) {
                    if (playing) {
                        if (start(newStreamPtr) == 0) {
                            mirrorStreamPtr = newStreamPtr;
                            Log.i("ALSAClient", "Mirror stream rebuilt successfully.");
                            return;
                        }
                        close(newStreamPtr);
                    } else {
                        mirrorStreamPtr = newStreamPtr;
                        Log.i("ALSAClient", "Mirror stream rebuilt (not playing).");
                        return;
                    }
                }
                try { Thread.sleep(200); } catch (InterruptedException e) { break; }
            }
            Log.e("ALSAClient", "Failed to rebuild mirror stream after " + MAX_RETRIES + " retries.");
        } finally {
            mirrorRebuildPending.set(false);
        }
    }

    private void increaseBufferSizeIfUnderrunOccurs() {
        int i;
        int underrunCount = this.audioTrack.getUnderrunCount();
        if (underrunCount > this.previousUnderrunCount && (i = this.bufferSize) < this.bufferCapacity) {
            this.previousUnderrunCount = (short) underrunCount;
            int i2 = i + framesPerBuffer;
            this.bufferSize = i2;
            this.audioTrack.setBufferSizeInFrames(i2);
        }
    }

    public int pointer() {
        return this.position / this.frameBytes;
    }

    public void setDataType(DataType dataType) { this.dataType = dataType; }
    public void setContainerVariant(String containerVariant) { this.containerVariant = containerVariant; }
    public String getContainerVariant() { return containerVariant; }
    public boolean isGlibc() { return containerVariant != null && containerVariant.equals(Container.GLIBC); }
    public void setChannels(int channels) { this.channels = (byte) channels; }
    public void setSampleRate(int sampleRate) { this.sampleRate = sampleRate; }
    public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }
    public ByteBuffer getSharedBuffer() { return this.sharedBuffer; }

    public void setSharedBuffer(ByteBuffer sharedBuffer) {
        if (sharedBuffer != null) {
            ByteBuffer allocateDirect = ByteBuffer.allocateDirect(getBufferSizeInBytes());
            ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
            this.auxBuffer = allocateDirect.order(byteOrder);
            this.sharedBuffer = sharedBuffer.order(byteOrder);
            return;
        }
        this.auxBuffer = null;
        this.sharedBuffer = null;
    }

    public ByteBuffer getAuxBuffer() { return this.auxBuffer; }
    public int getBufferSizeInBytes() { return this.bufferSize * this.frameBytes; }

    public static int latencyMillisToBufferSize(int latencyMillis, int channels, DataType dataType, int sampleRate) {
        byte frameBytes = (byte) (dataType.byteCount * channels);
        int bufferSize = (int) Mathf.roundTo((latencyMillis * sampleRate) / 1000.0f, framesPerBuffer, false);
        return bufferSize * frameBytes;
    }

    private boolean isValidBufferSize() {
        int i = this.bufferSize;
        return i > 0 && (i * frameBytes) > 0;
    }

    public static void assignFramesPerBuffer(Context context) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            String framesPerBufferStr = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            short parseShort = Short.parseShort(framesPerBufferStr);
            framesPerBuffer = parseShort;
            if (parseShort == 0) framesPerBuffer = (short) 256;
        } catch (Exception e) {
            framesPerBuffer = (short) 256;
        }
    }

    private native long simulatedCreate(int format, byte channelCount, int sampleRate, int bufferSize);
    private native long create(int format, byte channelCount, int sampleRate, int bufferSize);
    private native int simulatedWrite(long streamPtr, ByteBuffer buffer, int numFrames);
    private native int write(long streamPtr, ByteBuffer buffer, int numFrames);
    private native void simulatedStart(long streamPtr);
    private native int start(long streamPtr);
    private native void simulatedStop(long streamPtr);
    private native void stop(long streamPtr);
    private native void simulatedPause(long streamPtr);
    private native void pause(long streamPtr);
    private native void simulatedFlush(long streamPtr);
    private native void flush(long streamPtr);
    private native void simulatedClose(long streamPtr);
    private native void close(long streamPtr);
}
