package com.fox2code.faflaunchmod.launcher.protocols.flm;

import com.fox2code.faflaunchmod.launcher.Main;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;

final class FLMURLConnection extends URLConnection {
    private byte[] data;
    private URL override;
    private InputStream inputStream;
    private URLConnection urlConnection;
    private boolean stealthConnected = false;

    FLMURLConnection(URL url) {
        super(Objects.requireNonNull(url, "url"));
        if (!"flm".equals(url.getProtocol())) {
            throw new AssertionError(url.toString());
        }
    }

    private void stealthConnect() {
        if (!this.stealthConnected) {
            this.stealthConnected = true;
            this.data = Main.getLaunchClassLoader().getResourceOverride(this.url.getPath());
            if (this.data != null) {
                this.connected = true;
            } else {
                URL resourceURL = Main.getLaunchClassLoader().findResource(this.url.getPath());
                if (resourceURL != null && !"flm".equals(resourceURL.getProtocol())) {
                    this.override = resourceURL;
                }
            }
        }
    }

    @Override
    public void connect() throws IOException {
        this.stealthConnect();
        if (this.override != null) {
            if (this.urlConnection == null) {
                this.urlConnection = this.override.openConnection();
            }
            this.urlConnection.connect();
            this.connected = true;
        } else {
            if (this.data == null) {
                throw new IOException("Resource no longer exists: " + this.url.getPath());
            }
            this.inputStream = new ByteArrayInputStream(this.data);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (this.urlConnection == null && this.inputStream == null) {
            this.connect(); // Try to connect if needed
        }
        if (this.urlConnection != null) {
            return this.urlConnection.getInputStream();
        }
        return this.inputStream;
    }

    @Override
    public int getContentLength() {
        if (this.urlConnection != null) {
            return this.urlConnection.getContentLength();
        }
        this.stealthConnect();
        return this.data == null ? -1 : this.data.length;
    }

    @Override
    public long getContentLengthLong() {
        if (this.urlConnection != null) {
            return this.urlConnection.getContentLengthLong();
        }
        this.stealthConnect();
        return this.data == null ? -1 : this.data.length;
    }
}
