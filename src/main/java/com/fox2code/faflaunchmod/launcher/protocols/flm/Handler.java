package com.fox2code.faflaunchmod.launcher.protocols.flm;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class Handler extends URLStreamHandler {
    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        if (!"flm".equals(u.getProtocol())) {
            throw new IOException("Invalid protocol: " + u.getProtocol());
        }
        return new FLMURLConnection(u);
    }
}
