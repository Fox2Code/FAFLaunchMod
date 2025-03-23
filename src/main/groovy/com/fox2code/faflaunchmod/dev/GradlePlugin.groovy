package com.fox2code.faflaunchmod.dev

import com.fox2code.faflaunchmod.launcher.BuildConfig
import com.fox2code.faflaunchmod.launcher.DependencyHelper
import com.fox2code.faflaunchmod.loader.FAFLauncherHelper
import com.fox2code.faflaunchmod.utils.SourceUtil
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class GradlePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.apply([plugin: 'java-library'])
        project.java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }

            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
            withSourcesJar()
            withJavadocJar()
        }
        project.tasks.javadoc.failOnError false
        // Support "@reason" javadoc Mixin tag mandated by Minecraft-Dev Intellij plugin
        project.tasks.javadoc.options.tags = [ "reason" ]
        project.tasks.javadoc.options.addStringOption('Xdoclint:none', '-quiet')
        project.repositories {
            mavenCentral()
            maven {
                name = 'Fabric'
                url 'https://maven.fabricmc.net/'
                content {
                    includeGroup "net.fabricmc"
                }
            }
            maven {
                name = 'Fox2Code Maven'
                url 'https://cdn.fox2code.com/maven'
            }
        }
        project.tasks.register("runModded", JavaExec) {
            group = "FAFLaunchMod"
            description = "Run FAFLaunch Mod from gradle"
        }.get().dependsOn(project.getTasks().named("assemble"))
        project.extensions.create("faflaunchmod", FAFLaunchModConfig)
        File buildDir = project.layout.buildDirectory.get().asFile
        File gitIgnore = new File(buildDir, ".gitignore")
        if (buildDir.exists() && !gitIgnore.exists()) {
            Files.write(gitIgnore.toPath(), "*".getBytes(StandardCharsets.UTF_8))
        }
        project.afterEvaluate {
            String jfxPlat = System.getProperty('os.name')
                    .toLowerCase(Locale.ROOT).contains('win') ? 'win' : 'linux'
            project.dependencies {
                compileOnly("com.fox2code:FAFLaunchMod:" + BuildConfig.FAF_LAUNCH_MOD_VERSION) { transitive = false }
                compileOnly "org.openjfx:javafx-base:21:$jfxPlat"
                compileOnly "org.openjfx:javafx-graphics:21:$jfxPlat"
                compileOnly "org.openjfx:javafx-controls:21:$jfxPlat"
                compileOnly "org.openjfx:javafx-fxml:21:$jfxPlat"
                compileOnly "org.openjfx:javafx-media:21:$jfxPlat"
                compileOnly "org.openjfx:javafx-swing:21:$jfxPlat"
                compileOnly "org.openjfx:javafx-web:21:$jfxPlat"
            }
            for (DependencyHelper.Dependency dependency : DependencyHelper.commonDependencies) {
                project.dependencies {
                    compileOnly(dependency.name) { transitive = false }
                }
            }
            FAFLaunchModConfig config = project.extensions.getByName("faflaunchmod") as FAFLaunchModConfig
            File fafLauncher = FAFLauncherHelper.updateAndGetFAFLauncher(config.launcherVersion)
            File fafLauncherLib = new File(fafLauncher, "lib")
            for (File file : Objects.requireNonNull(fafLauncherLib.listFiles())) {
                if (file.getName().endsWith(".jar") && file.isFile()) {
                    if (file.getName().startsWith("asm-")) continue
                    if (file.getName().startsWith("javafx-")) continue
                    if (FAFLauncherHelper.doNotUseAsLibrary(file.getName())) continue
                    project.dependencies {
                        compileOnly(project.files(file))
                    }
                }
            }
            Jar jarTask = ((Jar) project.getTasks().named("jar").get())
            File mod = jarTask.getArchiveFile().get().getAsFile()
            final JavaToolchainService toolchain = project.extensions.getByType(JavaToolchainService.class)
            final String java21executable = toolchain.launcherFor {
                it.languageVersion.set(JavaLanguageVersion.of(21))
            }.get().executablePath.asFile.absolutePath
            JavaExec runModded = project.getTasks().named("runModded").get() as JavaExec
            runModded.executable = java21executable
            runModded.classpath(SourceUtil.getSourceFile(GradlePlugin.class))
            runModded.mainClass.set("com.fox2code.faflaunchmod.launcher.Main")
            runModded.systemProperty("faflaunchmod.inject-mod", mod.getAbsolutePath())
            runModded.systemProperty("faflaunchmod.use-faf-launcher", fafLauncher.getAbsolutePath())
            runModded.workingDir = fafLauncher
            String modVersion = config.modVersion
            if (modVersion == null || modVersion.isEmpty()) {
                modVersion = String.valueOf(project.version)
            }
            jarTask.manifest {
                attributes 'ModVersion': modVersion
            }
        }
    }

    static class FAFLaunchModConfig {
        public String launcherVersion = null
        public String modVersion = null
    }
}
