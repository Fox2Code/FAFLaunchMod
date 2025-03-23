package com.fox2code.faflaunchmod.launcher;

import com.fox2code.faflaunchmod.utils.Enumerations;
import com.fox2code.faflaunchmod.utils.Platform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.Files;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Function;

public final class LaunchClassLoader extends URLClassLoader {
    private static final CodeSigner[] NO_CodeSigners = new CodeSigner[0];
    private static final Function<URL, byte[]> readUrl = url -> {
        try (InputStream inputStream = url.openStream()){
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final Object findResourceLock = new Object();
    private final HashMap<String, Function<URL, byte[]>> resourcesLoadPatcher = new HashMap<>();
    private final HashMap<String, byte[]> resourcesOverrides = new HashMap<>();
    private final LinkedList<String> exclusions = new LinkedList<>();
    private final HashMap<String, CodeSource> codeSourceCache;
    private boolean didPrintedTransformFail = false;
    private WrappedExtensions wrappedExtensions;

    public LaunchClassLoader() {
        super("faf-launch-mod", new URL[]{LaunchClassLoader.class
                .getProtectionDomain().getCodeSource().getLocation()},
                LaunchClassLoader.class.getClassLoader());
        this.codeSourceCache = new HashMap<>();
    }

    @Override
    public void addURL(URL url) {
        super.addURL(Objects.requireNonNull(url, "url"));
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> c;
        if (isFAFLaunchModClass(name)) {
            // Load classes from the parent class loader
            c = super.loadClass(name, resolve);
        } else if (name.startsWith("com.fox2code.faflaunchmod.") ||
                // Check mixins to fix them in development environment.
                isLoaderClassName(name)) {
            c = findLoadedClass(name);
            if (c == null) {
                synchronized (getClassLoadingLock(name)) {
                    c = findClassImpl(name, null);
                }
            }
        } else {
            c = findLoadedClass(name);
            if (c == null) {
                URL resource = findResource(name.replace('.', '/') + ".class");
                if (resource != null) {
                    synchronized (getClassLoadingLock(name)) {
                        c = findClassImpl(name, resource);
                    }
                } else try {
                    c = super.loadClass(name, false);
                } catch (SecurityException securityException) {
                    throw new ClassNotFoundException(name, securityException);
                }
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }

    private Class<?> findClassImpl(String name, URL resource) throws ClassNotFoundException {
        Class<?> clas = this.findLoadedClass(name);
        if (clas != null) return clas;
        byte[] bytes = null;
        try {
            final String packageName = name.lastIndexOf('.') == -1 ? "" : name.substring(0, name.lastIndexOf('.'));
            if (getDefinedPackage(packageName) == null) {
                definePackage(packageName, null, null, null, null, null, null, null);
            }
            ClassLoader resourceClassLoader;
            if (name.startsWith("com.fox2code.foxloader.")) {
                resourceClassLoader = getParent();
            } else {
                resourceClassLoader = this;
            }
            if (resource == null) {
                resource =
                        resourceClassLoader.getResource(
                                name.replace('.', '/').concat(".class"));
            }
            URLConnection urlConnection;
            if (resource == null) {
                urlConnection = null;
            } else {
                urlConnection = resource.openConnection();
                InputStream is = urlConnection.getInputStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                int nRead;
                byte[] data = new byte[16384];

                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }

                bytes = buffer.toByteArray();
            }
            String tmpName = name.replace('/','.');
            // We need to apply some patches to mixins to make them actually work.
            if (wrappedExtensions != null && !isTransformExclude(tmpName)) {
                final byte[][] bytesStore = new byte[][]{bytes};
                try {
                    wrappedExtensions.transform(bytesStore, tmpName);
                } catch (Throwable e) {
                    bytes = bytesStore[0];
                    if (bytes != null && !this.didPrintedTransformFail) {
                        this.didPrintedTransformFail = true; // Only print first failure.
                        Files.write(new File(Platform.getFafDirectory(), "transform_fail.class").toPath(), bytes);
                    }
                    throw new ClassTransformException("Can't transform " + name, e);
                }
                bytes = bytesStore[0];
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                try {
                    bytes = wrappedExtensions.computeFrames(bytes);
                } catch (Exception e) {
                    Files.write(new File(Platform.getFafDirectory(), "compute_fail.class").toPath(), bytes);
                    throw new ClassTransformException("Can't compute frames for "+name, e);
                }
            } else {
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
            }

            URL url = null;
            if (urlConnection instanceof JarURLConnection) {
                url = ((JarURLConnection) urlConnection).getJarFileURL();
            }
            clas = defineClass(name,bytes,0,bytes.length, codeSourceFromURL(url));
            return clas;
        } catch (ClassTransformException e) {
            if (bytes != null) try {
                Files.write(new File(Platform.getFafDirectory(), "transform_fail.class").toPath(), bytes);
            } catch (IOException ignored) {}
            throw new ClassNotFoundException(name, e);
        } catch (ClassFormatError ioe) {
            if (bytes != null) try {
                Files.write(new File(Platform.getFafDirectory(), "load_fail.class").toPath(), bytes);
            } catch (IOException ignored) {}
            throw new ClassNotFoundException(name, ioe);
        } catch (Exception ioe) {
            throw new ClassNotFoundException(name, ioe);
        }
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        URL source = this.findPatchedResource(name, true);
        if (source != null) {
            return Enumerations.singleton(source);
        }
        return super.findResources(name);
    }

    @Override
    public URL findResource(String name) {
        return this.findPatchedResource(name, false);
    }

    public URL findPatchedResource(String name) {
        return this.findPatchedResource(name, true);
    }

    @SuppressWarnings("deprecation")
    private URL findPatchedResource(String name, boolean multi) {
        synchronized (this.findResourceLock) {
            // Work around protocol sometimes being pre-appended to path for patched resources.
            String nameFix = name.startsWith("flm:") ? name.substring(4) : name;
            if (this.resourcesOverrides.containsKey(nameFix)) {
                try {
                    return new URL("flm", null, -1, nameFix, null);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
            Function<URL, byte[]> function = this.resourcesLoadPatcher.remove(nameFix);
            if (function != null) {
                URL resourceOrig = super.findResource(nameFix);
                if (resourceOrig == null) return null;
                byte[] newData = function.apply(resourceOrig);
                if (newData == null) return multi ? null : resourceOrig;
                newData = this.wrappedExtensions.patchTransformedResource(newData, nameFix);
                this.resourcesOverrides.put(nameFix, newData);
                try {
                    return new URL("flm", null, -1, nameFix, null);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return multi ? null : super.findResource(name);
    }

    public URL findAsPatchedResource(String path) {
        URL url = this.findPatchedResource(path, true);
        if (url == null) return null;
        if ("flm".equals(url.getProtocol())) return url;
        String nameFix = path.startsWith("flm:") ? path.substring(4) : path;
        try {
            return new URL("flm", null, -1, nameFix, null);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private CodeSource codeSourceFromURL(final URL url) {
        if (url == null) return null;
        return this.codeSourceCache.computeIfAbsent(url.toString(),
                k -> new CodeSource(url, NO_CodeSigners));
    }

    public boolean isClassLoaded(String className) {
        return this.findLoadedClass(className) != null;
    }

    public boolean isClassInClassPath(String className) {
        final String path = className.replace('.', '/') + ".class";

        if (className.startsWith("com.fox2code.foxloader.")) {
            return this.getParent().getResource(path) != null;
        } else if (isLoaderClassName(className)) {
            return this.findResource(path) != null;
        } else {
            return this.getResource(path) != null;
        }
    }

    public boolean hasClass(String className) {
        return this.isClassLoaded(className) ||
                this.isClassInClassPath(className);
    }

    public static boolean isLoaderClassName(String name) {
        return name.startsWith("com.fox2code.faflaunchmod.loader.") ||
                name.startsWith("io.github.karlatemp.unsafeaccessor.") ||
                name.startsWith("com.llamalad7.mixinextras.") ||
                name.startsWith("com.bawnorton.mixinsquared.") ||
                name.startsWith("org.spongepowered.") ||
                name.startsWith("org.objectweb.asm.") ||
                name.startsWith("com.fox2code.rebuild.") ||
                name.startsWith("com.fox2code.foxevents.");
    }

    public static boolean isFAFLaunchModClass(String name) {
        return name.startsWith("com.fox2code.faflaunchmod.launcher.") ||
                name.startsWith("com.fox2code.faflaunchmod.utils.");
    }

    public void addTransformerExclusion(String exclusion) {
        if (!this.exclusions.contains(exclusion)) {
            this.exclusions.add(exclusion);
        }
    }

    public boolean isTransformExclude(String className) {
        if (isLoaderClassName(className)) {
            return true;
        }
        for (String excl:exclusions) {
            if (className.startsWith(excl)) {
                return true;
            }
        }
        return false;
    }

    public byte[] getResourceOverride(String key) {
        if (this.resourcesLoadPatcher.containsKey(key)) {
            this.findPatchedResource(key, false);
        }
        return this.resourcesOverrides.get(key);
    }

    public void addLoaderResourceLoadingPatch(String key) {
        this.setResourceLoadingPatch(key, readUrl);
    }

    public void setResourceLoadingPatch(String key, Function<URL, byte[]> function) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(function, "function");
        synchronized (this.findResourceLock) {
            if (this.resourcesOverrides.containsKey(key))
                throw new IllegalStateException("The path " + key + " is already patched!");
            if (function == readUrl) {
                this.resourcesLoadPatcher.putIfAbsent(key, function);
            } else {
                this.resourcesLoadPatcher.put(key, function);
            }
        }
    }

    public void installWrappedExtensions(WrappedExtensions wrappedExtensions) {
        if (this.wrappedExtensions != null)
            throw new IllegalStateException("Wrapped Extension Already Installed!");
        this.wrappedExtensions = Objects.requireNonNull(wrappedExtensions);
    }

    public boolean isHookedResource(String path) {
        synchronized (this.findResourceLock) {
            return this.resourcesOverrides.containsKey(path) ||
                    this.resourcesLoadPatcher.containsKey(path);
        }
    }

    private static class ClassTransformException extends Exception {
        public ClassTransformException(String message) {
            super(message);
        }

        public ClassTransformException(String message, Throwable e) {
            super(message, e);
        }
    }

    public static abstract class WrappedExtensions {
        public abstract void transform(byte[][] classData, String className);

        public abstract byte[] computeFrames(byte[] classData);

        public abstract byte[] patchTransformedResource(byte[] data, String name);
    }
}
