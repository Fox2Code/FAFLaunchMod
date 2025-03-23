package com.fox2code.faflaunchmod.loader;

import com.fox2code.faflaunchmod.launcher.Main;
import com.fox2code.faflaunchmod.utils.IOUtils;
import com.fox2code.faflaunchmod.utils.NetUtils;
import com.fox2code.faflaunchmod.utils.Platform;
import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.util.Objects;

public final class FAFLauncherHelper {
    private static final Gson gson = new GsonBuilder().create();
    private static final File fafDirectory = Platform.getFafDirectory();
    private static final File fafLaunchers = new File(fafDirectory, "faf-launchers");
    private static final File fafLaunchersMeta = new File(fafLaunchers, "metadata.bin");
    private static final long TIME_12_HOURS = 43200000;

    private FAFLauncherHelper() {}

    public static File updateAndGetFAFLauncher(String wantVersion) throws IOException {
        if (!fafLaunchers.isDirectory() && !fafLaunchers.mkdirs())
            throw new IOException("Failed to create launchers dirs!");
        long lastCheck = 0;
        String suggestedPath = null;
        String versionID = null;
        if (fafLaunchersMeta.exists()) {
            int platform;
            try (DataInputStream dataInputStream = new DataInputStream(new FileInputStream(fafLaunchersMeta))) {
                platform = dataInputStream.readUnsignedByte();
                if (platform == Platform.getPlatform().ordinal()) {
                    lastCheck = dataInputStream.readLong();
                    suggestedPath = dataInputStream.readUTF();
                    versionID = dataInputStream.readUTF();
                }
            } catch (IOException e) {
                platform = -1;
            }
            if (platform != Platform.getPlatform().ordinal()) {
                lastCheck = 0;
                suggestedPath = null;
            }
        }
        File proposedFaFLauncher = suggestedPath != null ?
                new File(fafLaunchers, suggestedPath) : null;
        if (lastCheck + TIME_12_HOURS > System.currentTimeMillis() &&
                proposedFaFLauncher != null && proposedFaFLauncher.isDirectory() &&
                (wantVersion == null || wantVersion.equals(versionID))) {
            return proposedFaFLauncher;
        }
        JsonArray releases = gson.fromJson(NetUtils.downloadAsString(
                "https://api.github.com/repos/FAForever/downlords-faf-client/releases"), JsonArray.class);
        String latestVersion = releases.get(0).getAsJsonObject().get("tag_name").getAsString();
        JsonObject selectedVersion = null;
        if (wantVersion == null) {
            selectedVersion = releases.get(0).getAsJsonObject();
            wantVersion = selectedVersion.get("tag_name").getAsString();
            if (wantVersion == null) {
                throw new RuntimeException("Cannot find latest version in json.");
            }
            if (proposedFaFLauncher != null && proposedFaFLauncher.isDirectory() &&
                    wantVersion.equals(versionID)) {
                saveVersionMetadata(proposedFaFLauncher.getAbsolutePath(), versionID);
                return proposedFaFLauncher;
            }
        } else {
            for (JsonElement jsonElement : releases) {
                if (!jsonElement.isJsonObject()) continue;
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                String versionId = jsonObject.get("tag_name").getAsString();
                if (versionId != null && versionId.equals(wantVersion)) {
                    selectedVersion = jsonObject;
                    break;
                }
            }
        }
        if (selectedVersion == null) {
            throw new RuntimeException("Failed to find FaF Launcher version: " + wantVersion);
        }
        return setupForSelectedVersion(selectedVersion, wantVersion,
                Objects.equals(wantVersion, latestVersion));
    }

    private static File setupForSelectedVersion(
            JsonObject selectedVersion, String versionId, boolean saveMetadata) throws IOException {
        if (!versionId.startsWith("v")) throw new IllegalArgumentException(versionId);
        JsonArray assets = selectedVersion.getAsJsonArray("assets");
        boolean windows = Platform.getPlatform() == Platform.WINDOWS;
        File extractEnd = new File(fafLaunchers, "faf-client-" + versionId.substring(1));
        IOUtils.deleteRecursively(extractEnd);
        String archiveUrl = null;
        File destination = null;
        for (JsonElement jsonElement : assets) {
            if (!jsonElement.isJsonObject()) continue;
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String name = jsonObject.get("name").getAsString();
            if (name.endsWith(windows ? ".zip" : ".tar.gz")) {
                destination = new File(fafLaunchers, name);
                archiveUrl = jsonObject.get("browser_download_url").getAsString();
                break;
            }
        }
        if (archiveUrl == null) {
            throw new RuntimeException("Failed to find FaF Launcher download: " + versionId);
        }
        try (BufferedOutputStream bufferedOutputStream =
                     new BufferedOutputStream(new FileOutputStream(destination))) {
            NetUtils.downloadTo(archiveUrl, bufferedOutputStream);
        }
        if (windows) {
            try (BufferedInputStream bufferedInputStream =
                         new BufferedInputStream(new FileInputStream(destination))) {
                IOUtils.unzip(bufferedInputStream, fafLaunchers.toPath());
            }
        } else {
            Process process = new ProcessBuilder(Main.unixTarBin.getAbsolutePath(),
                    "-xf", destination.getAbsolutePath()).directory(fafLaunchers).inheritIO().start();
            try {
                if (process.waitFor() != 0) {
                    throw new IOException("Extraction failed");
                }
            } catch (InterruptedException e) {
                throw new IOException("Extraction failed", e);
            }
        }
        File libs = new File(extractEnd, "lib");
        if (!extractEnd.isDirectory() || !libs.exists()) {
            throw new IOException("Failed to extract to launchers/faf-client-" + versionId.substring(1));
        }
        File natives = new File(extractEnd, "natives");
        for (String fileName : new String[]{"faf-uid", "faf-uid.exe", "faf-ice-adapter.jar"}) {
            File n = new File(natives, fileName);
            File l = new File(libs, fileName);
            if (n.isFile() && !l.exists()) {
                Files.copy(n.toPath(), l.toPath());
                if (!fileName.endsWith(".jar") &&
                        !l.canExecute() && !l.setExecutable(true)) {
                    throw new IOException("Failed to make " + fileName + " executable");
                }
            }
        }
        if (saveMetadata || !fafLaunchersMeta.exists()) {
            saveVersionMetadata(extractEnd.getName(), versionId);
        }
        return extractEnd;
    }

    private static void saveVersionMetadata(String path, String versionID) throws IOException {
        try (DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream(fafLaunchersMeta))) {
            dataOutputStream.writeByte(Platform.getPlatform().ordinal());
            dataOutputStream.writeLong(System.currentTimeMillis());
            dataOutputStream.writeUTF(path);
            dataOutputStream.writeUTF(versionID);
        }
    }

    public static boolean doNotUseAsLibrary(String fileName) {
        return "faf-ice-adapter.jar".equals(fileName);
    }
}
