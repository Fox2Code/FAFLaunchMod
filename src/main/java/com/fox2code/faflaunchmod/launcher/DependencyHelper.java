package com.fox2code.faflaunchmod.launcher;

import com.fox2code.faflaunchmod.utils.IOUtils;
import com.fox2code.faflaunchmod.utils.NetUtils;
import com.fox2code.faflaunchmod.utils.Platform;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;

public class DependencyHelper {
    private static final boolean DEBUG = false;
    private static final File librariesDir = new File(Platform.getFafDirectory(), "libraries");
    public static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    public static final String FABRIC_MC = "https://maven.fabricmc.net/";
    public static final String FOX2CODE = "https://cdn.fox2code.com/maven";
    private static boolean testMode = false;

    public static final Dependency[] commonDependencies = new Dependency[]{
            /*new Dependency("com.google.guava:guava:21.0", MAVEN_CENTRAL,
                    "com.google.common.io.Files", null, "972139718abc8a4893fa78cba8cf7b2c903f35c97aaf44fa3031b0669948b480"),*/
            new Dependency("com.google.code.gson:gson:2.10.1", MAVEN_CENTRAL,
                    "com.google.gson.Gson", null, "4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593"),
            new Dependency("org.ow2.asm:asm:" + BuildConfig.ASM_VERSION, MAVEN_CENTRAL,
                    "org.objectweb.asm.ClassVisitor", null, "8cadd43ac5eb6d09de05faecca38b917a040bb9139c7edeb4cc81c740b713281"),
            new Dependency("org.ow2.asm:asm-tree:" + BuildConfig.ASM_VERSION, MAVEN_CENTRAL,
                    "org.objectweb.asm.tree.ClassNode", null, "9929881f59eb6b840e86d54570c77b59ce721d104e6dfd7a40978991c2d3b41f"),
            new Dependency("org.ow2.asm:asm-analysis:" + BuildConfig.ASM_VERSION, MAVEN_CENTRAL,
                    "org.objectweb.asm.tree.analysis.Analyzer", null , "85b29371884ba31bb76edf22323c2c24e172c3267a67152eba3d1ccc2e041ef2"),
            new Dependency("org.ow2.asm:asm-commons:" + BuildConfig.ASM_VERSION, MAVEN_CENTRAL,
                    "org.objectweb.asm.commons.InstructionAdapter", null, "9a579b54d292ad9be171d4313fd4739c635592c2b5ac3a459bbd1049cddec6a0"),
            new Dependency("org.ow2.asm:asm-util:" + BuildConfig.ASM_VERSION, MAVEN_CENTRAL,
                    "org.objectweb.asm.util.CheckClassAdapter", null, "f885be71b5c90556f5f1ad1c4f9276b29b96057c497d46666fe4ddbec3cb43c6"),
            new Dependency("net.fabricmc:sponge-mixin:" + BuildConfig.FABRIC_MIXIN_VERSION, FABRIC_MC,
                    "org.spongepowered.asm.mixin.Mixins", null, "1dd2b778ed5283bce6b6b07d9690d86d956b17a7103efafad47073db1599584"),
            new Dependency("io.github.llamalad7:mixinextras-common:" + BuildConfig.MIXIN_EXTRAS_VERSION, MAVEN_CENTRAL,
                    "com.llamalad7.mixinextras.MixinExtrasBootstrap", null, "6a2c6f39f285348635ba1e0e914d066fe718c207e220a49012e8b347cb27fbda"),
            new Dependency("com.bawnorton.mixinsquared:mixinsquared-common:" + BuildConfig.MIXIN_SQUARED_VERSION, FOX2CODE,
                    "com.bawnorton.mixinsquared.MixinSquaredBootstrap", null, "d80619866e6d8c00bdeeaf6484357c1bbd4006fe0154537e9137213ab82bdaca"),
            new Dependency("com.fox2code:ReBuild:" + BuildConfig.REBUILD_VERSION, FOX2CODE,
                    "com.fox2code.rebuild.ClassDataProvider", null, "6bb4ac7ae84ec752e505c4dc885c003e2550baf4c327a5c9f9c44379a756e43d"),
            new Dependency("com.fox2code:FoxEvents:" + BuildConfig.FOX_EVENTS_VERSION, FOX2CODE,
                    "com.fox2code.foxevents.FoxEvents", null, "97b267bc5b9d8b3e55ced5ee2b080dbeb30f83a1125d960c60407e90796da5e3"),/**/
            /*new Dependency("com.fox2code:FoxFlexVer:" + BuildConfig.FOX_FLEX_VER_VERSION, FOX2CODE,
                    "com.fox2code.flexver.FlexVer", null, "4cf356d6c05c1a7008d90500945df21e4bac32e3a09309efc012a5a373431c0b"),/**/
            new Dependency("io.github.karlatemp:unsafe-accessor:" + BuildConfig.UNSAFE_ACCESSOR_VERSION, MAVEN_CENTRAL,
                    "io.github.karlatemp.unsafeaccessor.Root", null, "3446c3cedcaf57fe8d969c414754edcce9baab2e4ec412ff52c9d7052a29a768")
    };

    public static void loadCommonDependencies() {
        for (Dependency dependency : commonDependencies) {
            loadDependency(dependency);
        }
    }

    public static void loadDependency(Dependency dependency) {
        if (!testMode && hasClass(dependency.classCheck)) return;
        if (!librariesDir.isDirectory() && !librariesDir.mkdirs())
            throw new IOError(new IOException("Failed to create libraries directory"));
        String postURL = resolvePostURL(dependency.name);
        File file = new File(librariesDir, fixUpPath(postURL));
        if (!file.isAbsolute()) file = file.getAbsoluteFile();
        boolean justDownloaded = false;
        checkHashOrDelete(file, dependency, testMode);
        if (!file.exists()) {
            File parentFile = file.getParentFile();
            if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
                throw new RuntimeException("Cannot create dependency directory for " + dependency.name);
            }
            IOException fallBackIoe = null;
            try (OutputStream os = Files.newOutputStream(file.toPath())) {
                justDownloaded = true;
                NetUtils.downloadTo(URI.create(dependency.repository.endsWith(".jar") ?
                        dependency.repository : dependency.repository + "/" + postURL).toURL(), os);
            } catch (IOException ioe) {
                if (dependency.fallbackUrl != null) {
                    fallBackIoe = ioe;
                } else {
                    if (file.exists() && !file.delete()) file.deleteOnExit();
                    throw new RuntimeException("Cannot download " + dependency.name, ioe);
                }
            }
            if (fallBackIoe != null) {
                try (OutputStream os = Files.newOutputStream(file.toPath())) {
                    justDownloaded = true;
                    NetUtils.downloadTo(URI.create(dependency.fallbackUrl).toURL(), os);
                } catch (IOException ioe) {
                    if (file.exists() && !file.delete()) file.deleteOnExit();
                    throw new RuntimeException("Cannot download " + dependency.name, fallBackIoe);
                }
            }
        }
        checkHashOrDelete(file, dependency, true);
        if (testMode) return;
        try {
            Main.launchClassLoader.addURL(file.toURI().toURL());
            if (hasClass(dependency.classCheck)) {
                if (DEBUG) {
                    System.out.println("Loaded " +
                            dependency.name + " -> " + file.getPath());
                }
            } else {
                if (!justDownloaded) {
                    // Assume file is corrupted if load failed.
                    if (file.exists() && !file.delete()) file.deleteOnExit();
                    loadDependency(dependency);
                    return;
                }
                throw new RuntimeException("Failed to load " +
                        dependency.name + " -> " + file.getPath());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String fixUpPath(String path) {
        return File.separatorChar == '\\' ?
                path.replace('/', '\\') : path;
    }

    public static boolean hasClass(String cls) {
        return Main.launchClassLoader.hasClass(cls);
    }

    private static String resolvePostURL(String string) {
        String[] depKeys = string.split(":");
        // "org.joml:rrrr:${jomlVersion}"      => ${repo}/org/joml/rrrr/1.9.12/rrrr-1.9.12.jar
        // "org.joml:rrrr:${jomlVersion}:rrrr" => ${repo}/org/joml/rrrr/1.9.12/rrrr-1.9.12-rrrr.jar
        if (depKeys.length == 3) {
            return depKeys[0].replace('.','/')+"/"+depKeys[1]+"/"+depKeys[2]+"/"+depKeys[1]+"-"+depKeys[2]+".jar";
        }
        if (depKeys.length == 4) {
            return depKeys[0].replace('.','/')+"/"+depKeys[1]+"/"+depKeys[2]+"/"+depKeys[1]+"-"+depKeys[2]+"-"+depKeys[3]+".jar";
        }
        throw new RuntimeException("Invalid Dep");
    }

    private static void checkHashOrDelete(File file, Dependency dependency, boolean errorOut) {
        if (dependency.sha256Sum == null || !file.exists()) return;
        String hashString;
        try {
            hashString = new BigInteger(1, IOUtils.sha256Of(file)).toString(16);
        } catch (IOException e) {
            hashString = "";
        }
        if (!dependency.sha256Sum.equals(hashString)) {
            boolean deleteSuccessful = file.delete();
            if (errorOut) {
                throw new RuntimeException("Remote dependency " + dependency.name + " checksum mismatch " +
                        "(got: " + hashString + ", expected: " + dependency.sha256Sum + ")");
            }
            if (!deleteSuccessful) {
                throw new RuntimeException("Can't delete dependency with checksum mismatch " + dependency.name);
            }
        }
    }

    public static void main(String[] args) {
        if (Main.launchClassLoader != null) {
            throw new IllegalStateException("Cannot run DependencyHelper test in ModLoader mode!");
        }
        testMode = true;
        try {
            loadCommonDependencies();
        } finally {
            testMode = false;
        }
    }

    public static class Dependency {
        public final String name, repository, classCheck, fallbackUrl, sha256Sum;

        public Dependency(String name, String repository, String classCheck) {
            this(name, repository, classCheck, null, null);
        }

        public Dependency(String name, String repository, String classCheck, String fallbackUrl) {
            this(name, repository, classCheck, fallbackUrl, null);
        }

        public Dependency(String name, String repository, String classCheck, String fallbackUrl, String sha256Sum) {
            this.name = name;
            this.repository = repository;
            this.classCheck = classCheck;
            this.fallbackUrl = fallbackUrl;
            this.sha256Sum = sha256Sum;
        }
    }
}
