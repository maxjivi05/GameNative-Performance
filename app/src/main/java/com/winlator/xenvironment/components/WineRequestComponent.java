package com.winlator.xenvironment.components;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.winlator.PrefManager;
import com.winlator.xenvironment.EnvironmentComponent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.gamenative.ui.screen.auth.EpicOAuthActivity;

public class WineRequestComponent extends EnvironmentComponent {
    abstract class RequestCodes {
        static final int OPEN_URL = 1;
    }

    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;
    private ExecutorService executor;

    private boolean openWithAndroidBrowser = false;

    public WineRequestComponent() {
        this.openWithAndroidBrowser = PrefManager.getBoolean("open_web_links_externally", false);
    }

    public void start() {
        isRunning = true;
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(20000, 50, InetAddress.getByName("127.0.0.1"));
                while (isRunning) {
                    try (Socket socket = serverSocket.accept();
                         DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                         DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
                        int requestCode = inputStream.readInt();
                        handleRequest(inputStream, outputStream, requestCode);
                    } catch (IOException e) {
                        if (isRunning) Log.e("WineRequestComponent", "Error handling request", e);
                    }
                }
            } catch (IOException e) {
                if (isRunning) Log.e("WineRequestComponent", "Server error", e);
            }
        });
    }

    public void stop() {
        isRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
            }
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    public void handleRequest(DataInputStream inputStream, DataOutputStream outputStream, int requestCode) throws IOException {
        switch(requestCode) {
            case RequestCodes.OPEN_URL:
                openURL(inputStream, outputStream);
                break;
        }
    }

    private void openURL(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        Context context = environment.getContext();

        int messageLength = inputStream.readInt();
        byte[] data = new byte[messageLength];
        inputStream.readFully(data);
        String url = new String(data, "UTF-8");

        if (url.startsWith(EpicOAuthActivity.EPIC_AUTH_URL_PREFIX)) {
            Intent intent = new Intent(context, EpicOAuthActivity.class);
            intent.putExtra(EpicOAuthActivity.EXTRA_GAME_AUTH_URL, url);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        if (openWithAndroidBrowser) {
            Log.d("WineRequestComponent", "Received request code OPEN_URL with url " + url.substring(0, Math.min(url.length(), 20)));
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
