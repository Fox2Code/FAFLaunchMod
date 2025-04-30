package com.fox2code.faflaunchmod.loader;

import com.fox2code.faflaunchmod.event.PreLaunchEvent;
import com.fox2code.faflaunchmod.event.Registerer;
import com.fox2code.faflaunchmod.launcher.BuildConfig;
import com.fox2code.faflaunchmod.launcher.LaunchClassLoader;
import com.fox2code.faflaunchmod.launcher.Main;
import com.fox2code.faflaunchmod.loader.bytepatch.BytePatches;
import com.fox2code.faflaunchmod.loader.mixin.ModLoaderMixin;
import com.fox2code.faflaunchmod.utils.Platform;
import com.fox2code.faflaunchmod.utils.SourceUtil;
import com.fox2code.rebuild.ClassDataProvider;
import com.google.gson.*;
import io.github.karlatemp.unsafeaccessor.Root;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ModLoader {
    private static final boolean DEBUG = false;
    private static final String INJECT_MOD = System.getProperty("faflaunchmod.inject-mod");
    private static final String USE_FAF_LAUNCHER = System.getProperty("faflaunchmod.use-faf-launcher");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final File fafDirectory = Platform.getFafDirectory();
    private static final File fafLauncherMods = new File(fafDirectory, "launcher-mods");
    private static final ArrayList<File> mods = new ArrayList<>();
    private static final List<File> modsImm = Collections.unmodifiableList(mods);
    private static final Iterable<File> modsItr = modsImm::iterator;
    private static final Attributes.Name MOD_VERSION = new Attributes.Name("ModVersion");
    private static final Function<String, String> translatePatchingMethod = translation ->
            translation.replace("%FAFLaunchModVersion%", BuildConfig.FAF_LAUNCH_MOD_VERSION)
                    .replace("%FAFLaunchModLoadedModCount%", "" + mods.size());
    private static String iceAppend = "";

    public static void launchModded(String[] args) throws Throwable {
        // Check JVM Compatibility
        Objects.requireNonNull(Root.getModuleAccess(), "module access");
        // Initialize FoxEvents
        Registerer.INSTANCE.check();
        // Preloads mods into the classpath
        if (!fafLauncherMods.isDirectory() && !fafLauncherMods.mkdirs())
            throw new IOError(new IOException("Failed to create faf-launch-mods directory"));
        String skipStartsWith = "\u0000";
        if (INJECT_MOD != null && !INJECT_MOD.isEmpty()) {
            File mod = new File(INJECT_MOD).getAbsoluteFile();
            if (mod.isFile() && mod.getName().endsWith(".jar")) {
                if (DEBUG) {
                    System.out.println("Loading injected mod: " + mod.getPath());
                }
                Main.getLaunchClassLoader().addURL(mod.toURI().toURL());
                mods.add(mod);
                String modName = mod.getName();
                int i = modName.lastIndexOf('-');
                if (i == -1) {
                    i = modName.lastIndexOf('.');
                }
                skipStartsWith = modName.substring(0, i);
            }
        }
        for (File file : Objects.requireNonNull(fafLauncherMods.listFiles())) {
            if (file.getName().endsWith(".jar") && file.isFile()) {
                if (file.getName().startsWith(skipStartsWith)) continue;
                if (DEBUG) {
                    System.out.println("Loading mod: " + file.getPath());
                }
                Main.getLaunchClassLoader().addURL(file.toURI().toURL());
                mods.add(file);
            }
        }
        System.out.println(mods);
        // Setup FAF launcher
        File fafLauncher = null;
        if (USE_FAF_LAUNCHER != null && !USE_FAF_LAUNCHER.isEmpty()) {
            fafLauncher = new File(USE_FAF_LAUNCHER).getAbsoluteFile();
        }
        if (fafLauncher == null || !fafLauncher.isDirectory()) {
            fafLauncher = FAFLauncherHelper.updateAndGetFAFLauncher(null);
        }
        if (!fafLauncher.getAbsolutePath().equals(System.getProperty("user.dir"))) {
            restartInDirectory(fafLauncher, args);
            return;
        }
        // Setup JVM for FAF launcher
        File install4j = new File(fafLauncher, ".install4j");
        loadJarsIn(install4j);
        File libs = new File(fafLauncher, "lib");
        loadJarsIn(libs);
        StringBuilder iceAppendBuilder = new StringBuilder();
        for (File file : Objects.requireNonNull(libs.listFiles())) {
            if (file.getName().endsWith(".jar") && file.isFile()) {
                if (file.getName().startsWith("javafx-")) {
                    iceAppendBuilder.append(file.getAbsolutePath()).append(File.pathSeparator);
                }
            }
        }
        iceAppend = iceAppendBuilder.toString();
        File natives = new File(fafLauncher, "natives");
        String path = System.getProperty("java.library.path");
        if (path == null || path.isEmpty()) {
            System.setProperty("java.library.path", natives.getAbsolutePath());
        } else {
            System.setProperty("java.library.path",
                    path + File.pathSeparator + natives.getAbsolutePath());
        }
        Root.getModuleAccess().addOpens(Object.class.getModule(),
                "java.lang", Root.getModuleAccess().getEVERYONE_MODULE());
        Root.getModuleAccess().addOpensToAllUnnamed(Object.class.getModule(), "java.lang");
        ReflectHelper.setSystemClassLoader(Main.getLaunchClassLoader());
        // Tell the class loader to modify the messages.properties
        System.out.println("FAF Launch Mod v" + BuildConfig.FAF_LAUNCH_MOD_VERSION);
        Main.getLaunchClassLoader().addLoaderResourceLoadingPatch(TranslationHelper.BASE_TRANSLATION_PATH);
        TranslationHelper.load("faflaunchmod.translations.json", translatePatchingMethod);
        SettingsHelper.init();
        SettingsHelper.addSettingPanel("faflaunchmod.settings.fxml");
        // Initialize Mixins
        initializeMixin();
        ModLoaderMixin.addMixinConfigurationSafe("FAFLaunchMod", "faflaunchmod.mixins.json");
        // Load mods mixins
        System.out.println(mods);
        for (File file : mods) {
            if (DEBUG) {
                System.out.println("Parsing mod: " + file.getPath());
            }
            try (JarFile jarFile = new JarFile(file)) {
                final String modVersion = jarFile.getManifest().getMainAttributes().getValue(MOD_VERSION);
                Enumeration<JarEntry> entryEnumeration = jarFile.entries();
                while (entryEnumeration.hasMoreElements()) {
                    JarEntry jarEntry = entryEnumeration.nextElement();
                    String entryName = jarEntry.getName();
                    if (DEBUG) {
                        System.out.println("Entry: " + entryName + " " + entryName.indexOf('/') + " " +
                                entryName.startsWith(".") + " " + entryName.startsWith("faflaunchmod."));
                    }
                    if (entryName.indexOf('/') == -1 && !entryName.startsWith(".") &&
                            !entryName.startsWith("faflaunchmod.")) {
                        if (DEBUG) {
                            System.out.println("Check Entry: " + entryName);
                        }
                        if (entryName.endsWith(".init.json")) {
                            InitHelper.loadInit(entryName);
                        } else if (entryName.endsWith(".mixins.json")) {
                            ModLoaderMixin.addMixinConfigurationSafe(entryName
                                    .substring(0, entryName.length() - 12).replace('.', '_'), entryName);
                        } else if (entryName.endsWith(".translations.json")) {
                            Function<String, String> modTranslatePatchingMethod = translatePatchingMethod;
                            if (modVersion != null && !modVersion.isEmpty()) {
                                modTranslatePatchingMethod = translation ->
                                        translatePatchingMethod.apply(translation)
                                                .replace("%ModVersion%", modVersion);
                            }
                            TranslationHelper.load(entryName, modTranslatePatchingMethod);
                        } else if (entryName.endsWith(".styles.css")) {
                            StyleHelper.addStyle(entryName);
                        } else if (entryName.endsWith(".settings.fxml")) {
                            SettingsHelper.addSettingPanel(entryName);
                        } else if (entryName.endsWith(".tab.fxml")) {
                            TabHelper.addTab(entryName);
                        }
                    }
                }
            }
        }
        // Run post init
        TabHelper.postInit();
        InitHelper.runInit();
        new PreLaunchEvent().callEvent();
        // Run FAF Launcher
        Class<?> cls;
        try {
            cls = Class.forName("install4j.com.faforever.client.Main");
        } catch (ClassNotFoundException ignored) {
            cls = Class.forName("com.faforever.client.Main");
        }
        cls.getDeclaredMethod("main", String[].class).invoke(null, (Object) args);
    }

    private static void loadJarsIn(File directory) throws MalformedURLException {
        if (!directory.isDirectory()) return;
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.getName().endsWith(".jar") && file.isFile()) {
                if (file.getName().startsWith("asm-")) continue;
                if (FAFLauncherHelper.doNotUseAsLibrary(file.getName())) continue;
                Main.getLaunchClassLoader().addURL(file.toURI().toURL());
            }
        }
    }

    private static void restartInDirectory(File fafLauncher, String[] args) throws IOException, InterruptedException {
        String[] allArgs = new String[]{
                Platform.getPlatform().javaBin.getAbsolutePath(), "-jar",
                SourceUtil.getSourceFile(ModLoader.class).getAbsolutePath()
        };
        if (args.length != 0) {
            int allArgsInitial = allArgs.length;
            allArgs = Arrays.copyOf(allArgs, allArgs.length + args.length);
            System.arraycopy(args, 0, allArgs, allArgsInitial, args.length);
        }
        System.exit(new ProcessBuilder(allArgs).inheritIO().directory(fafLauncher).start().waitFor());
    }

    private static void initializeMixin() {
        final IMixinTransformer mixinTransformer = ModLoaderMixin.init();
        final ClassDataProvider classDataProvider =
                new ClassDataProvider(Main.getLaunchClassLoader());
        final MixinEnvironment defalutMixinEnvironment =
                MixinEnvironment.getEnvironment(MixinEnvironment.Phase.DEFAULT);
        Main.getLaunchClassLoader().installWrappedExtensions(new LaunchClassLoader.WrappedExtensions() {
            @Override
            public void transform(byte[][] bytesStore, String className) {
                byte[] classData = bytesStore[0];
                try {
                    if ("com.faforever.client.theme.ThemeService".equals(className)) {
                        classData = BytePatches.patchThemeService(classData);
                    } else if (TabHelper.HEADER_BAR_CONTROLLER.equals(className)) {
                        classData = TabHelper.patchHeaderBarController(classData);
                        BytePatches.checkBytecodeValidity(classData, true);
                    } else if (TabHelper.NAVIGATION_ITEM.equals(className)) {
                        classData = TabHelper.patchNavigationItemEnum(classData);
                        BytePatches.checkBytecodeValidity(classData, true);
                    }
                    ClassNode classNode = new ClassNode();
                    ClassReader classReader = new ClassReader(classData);
                    classReader.accept(classNode, ClassReader.SKIP_FRAMES);
                    BytePatches.genericPatch(classNode);
                    mixinTransformer.transformClass(defalutMixinEnvironment, className, classNode);
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    classNode.accept(classWriter);
                    classData = classWriter.toByteArray();
                } finally {
                    bytesStore[0] = classData;
                }
            }

            @Override
            public byte[] computeFrames(byte[] classData) {
                ClassReader classReader = new ClassReader(classData);
                ClassWriter classWriter = classDataProvider.newClassWriter();
                classReader.accept(classWriter, 0);
                return classWriter.toByteArray();
            }

            @Override
            public byte[] patchTransformedResource(byte[] data, String name) {
                if (TranslationHelper.BASE_TRANSLATION_PATH.equals(name)) {
                    try {
                        return TranslationHelper.patchBaseTranslations(data);
                    } catch (IOException e) {
                        throw new IOError(e);
                    }
                } else if (SettingsHelper.SETTINGS_PATH.equals(name)) {
                    return SettingsHelper.patchSettingsFxml(data);
                } else if (SettingsHelper.FAF_LAUNCH_MOD_SETTINGS_PATH.equals(name)) {
                    return SettingsHelper.patchFAFLaunchModSettingsFxml(data);
                } else if (StyleHelper.STYLE_EXTENSIONS_PATH.equals(name)) {
                    try {
                        return StyleHelper.patchStyleExtensions(data);
                    } catch (IOException e) {
                        throw new IOError(e);
                    }
                } else if (TabHelper.HEADER_BAR_PATH.equals(name)) {
                    return TabHelper.patchHeaderBar(data);
                }
                return data;
            }
        });
        ModLoaderMixin.notifyInitialized();
    }

    public static String getVersion() {
        return BuildConfig.FAF_LAUNCH_MOD_VERSION;
    }

    public static File getModsDirectory() {
        return fafLauncherMods;
    }

    public static Iterable<File> getMods() {
        return modsItr;
    }

    public static Gson getGson() {
        return gson;
    }

    public static String getIceAppend() {
        return iceAppend;
    }
}
