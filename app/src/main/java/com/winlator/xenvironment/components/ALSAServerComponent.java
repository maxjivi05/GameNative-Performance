package com.winlator.xenvironment.components;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winlator.alsaserver.ALSAClientConnectionHandler;
import com.winlator.alsaserver.ALSARequestHandler;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xconnector.XConnectorEpoll;
import com.winlator.xconnector.Client;
import com.winlator.xenvironment.EnvironmentComponent;
import com.winlator.alsaserver.ALSAClient;
import com.winlator.xenvironment.ImageFs;

public class ALSAServerComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    private final ALSAClient.Options options;
    private final UnixSocketConfig socketConfig;
    private volatile boolean isPaused = false;
    private AudioDeviceCallback audioDeviceCallback;

    public ALSAServerComponent(UnixSocketConfig socketConfig, ALSAClient.Options options) {
        this.socketConfig = socketConfig;
        this.options = options;
    }

    @Override // com.winlator.xenvironment.EnvironmentComponent
    public void start() {
        if (this.connector != null) {
            return;
        }
        ALSAClient.assignFramesPerBuffer(this.environment.getContext());
        ImageFs imagefs = ImageFs.find(this.environment.getContext());

        XConnectorEpoll xConnectorEpoll = new XConnectorEpoll(this.socketConfig, new ALSAClientConnectionHandler(this.options, imagefs.getVariant()), new ALSARequestHandler());
        this.connector = xConnectorEpoll;
        xConnectorEpoll.setMultithreadedClients(true);
        this.connector.start();
        isPaused = false;

        if (options.reflectorMode) {
            registerAudioDeviceCallback();
        }
    }

    @Override // com.winlator.xenvironment.EnvironmentComponent
    public void stop() {
        if (options.reflectorMode) {
            unregisterAudioDeviceCallback();
        }

        XConnectorEpoll xConnectorEpoll = this.connector;
        if (xConnectorEpoll != null) {
            xConnectorEpoll.stop();
            this.connector = null;
        }
        isPaused = false;
    }

    private void registerAudioDeviceCallback() {
        Context context = this.environment.getContext();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioDeviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                notifyAudioDeviceChanged();
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                notifyAudioDeviceChanged();
            }
        };
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, new Handler(Looper.getMainLooper()));
    }

    private void unregisterAudioDeviceCallback() {
        if (audioDeviceCallback != null) {
            Context context = this.environment.getContext();
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
            audioDeviceCallback = null;
        }
    }

    public void pause() {
        if (isPaused) return;
        XConnectorEpoll xConnectorEpoll = this.connector;
        if (xConnectorEpoll != null) {
            for (int i = 0; i < xConnectorEpoll.getConnectedClientsCount(); i++) {
                Client client = xConnectorEpoll.getConnectedClientAt(i);
                if (client != null && client.getTag() instanceof ALSAClient) {
                    ((ALSAClient) client.getTag()).pause();
                }
            }
        }
        isPaused = true;
    }

    public void resume() {
        if (!isPaused) return;
        XConnectorEpoll xConnectorEpoll = this.connector;
        if (xConnectorEpoll != null) {
            for (int i = 0; i < xConnectorEpoll.getConnectedClientsCount(); i++) {
                Client client = xConnectorEpoll.getConnectedClientAt(i);
                if (client != null && client.getTag() instanceof ALSAClient) {
                    ((ALSAClient) client.getTag()).start();
                }
            }
        }
        isPaused = false;
    }

    public void notifyAudioDeviceChanged() {
        XConnectorEpoll xConnectorEpoll = this.connector;
        if (xConnectorEpoll != null) {
            for (int i = 0; i < xConnectorEpoll.getConnectedClientsCount(); i++) {
                Client client = xConnectorEpoll.getConnectedClientAt(i);
                if (client != null && client.getTag() instanceof ALSAClient) {
                    ((ALSAClient) client.getTag()).onAudioDeviceChanged();
                }
            }
        }
    }
}
