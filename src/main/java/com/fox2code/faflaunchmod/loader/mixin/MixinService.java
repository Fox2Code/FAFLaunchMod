package com.fox2code.faflaunchmod.loader.mixin;

import com.fox2code.faflaunchmod.launcher.LaunchClassLoader;
import com.fox2code.faflaunchmod.launcher.Main;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterJava;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.util.IConsumer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

public class MixinService extends MixinServiceAbstract implements IMixinService,
        IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
    private final ContainerHandleVirtual containerHandleVirtual = new ContainerHandleVirtual("foxloader");
    private IConsumer<MixinEnvironment.Phase> phaseConsumer;

    public MixinService() {
        if (Main.getLaunchClassLoader() != this.getClass().getClassLoader()) {
            throw new Error("WTF?! MixinService is loaded in the wrong context?");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void wire(MixinEnvironment.Phase phase, IConsumer<MixinEnvironment.Phase> phaseConsumer) {
        super.wire(phase, phaseConsumer);
        this.phaseConsumer = phaseConsumer;
    }

    @Override
    public String getName() {
        return "FoxMixinService";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.singletonList("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return containerHandleVirtual;
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return Collections.emptyList();
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_8;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return Main.getLaunchClassLoader().getResourceAsStream(name);
    }

    @Override
    public URL[] getClassPath() {
        return Main.getLaunchClassLoader().getURLs();
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return Main.getLaunchClassLoader().loadClass(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, Main.getLaunchClassLoader());
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, Main.getLaunchClassLoader());
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return this.getClassNode(name, false, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return this.getClassNode(name, runTransformers, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, IOException {
        ClassNode classNode = new ClassNode();
        InputStream is = Main.getLaunchClassLoader().getResourceAsStream(name.replace('.', '/') + ".class");
        if (is == null) {
            System.err.println("Failed to find class \"" + name + "\" for mixin");
            throw new ClassNotFoundException(name);
        }
        new ClassReader(is).accept(classNode, readerFlags);
        return classNode;
    }

    @Override
    public Collection<ITransformer> getTransformers() {
        return null;
    }

    @Override
    public Collection<ITransformer> getDelegatedTransformers() {
        return null;
    }

    @Override
    public void addTransformerExclusion(String name) {
        Main.getLaunchClassLoader().addTransformerExclusion(name);
    }

    @Override
    public void registerInvalidClass(String className) {
        //Invalid classes are not implemented in this context
    }

    @Override
    public boolean isClassLoaded(String className) {
        return Main.getLaunchClassLoader().isClassLoaded(className);
    }

    @Override
    public String getClassRestrictions(String className) {
        if (LaunchClassLoader.isFAFLaunchModClass(className)) {
            return "PACKAGE_CLASSLOADER_EXCLUSION,PACKAGE_TRANSFORMER_EXCLUSION";
        }
        return Main.getLaunchClassLoader().isTransformExclude(className) ?
                "PACKAGE_TRANSFORMER_EXCLUSION" : "";
    }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        return MixinEnvironment.Phase.PREINIT;
    }

    @Override
    public void offer(IMixinInternal internal) {
        super.offer(internal);
        if (internal instanceof IMixinTransformerFactory) {
            MixinEnvironment.getCurrentEnvironment().setActiveTransformer(
                    ((IMixinTransformerFactory) internal).createTransformer());

        }
    }

    protected ILogger createLogger(final String name) {
        return new LoggerAdapterJava(name) {
            @Override
            public void catching(Throwable t) {
                this.log(Level.WARN, "Catching " + t.getClass().getName() + ": " + t.getMessage(), t);
            }
        };
    }

    public void onStartup() {
        this.phaseConsumer.accept(MixinEnvironment.Phase.DEFAULT);
    }
}
