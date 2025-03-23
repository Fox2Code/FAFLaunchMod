package com.fox2code.faflaunchmod.loader;

import com.fox2code.faflaunchmod.launcher.Main;
import com.fox2code.faflaunchmod.loader.bytepatch.BytePatches;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

final class TabHelper {
    private static final boolean DEBUG = true;
    public static final String HEADER_BAR_PATH = "theme/headerbar/header_bar.fxml";
    private static final String HEADER_BAR_MATCHING = "text=\"%main.units\" toggleGroup=\"$mainNavigation\"/>";
    public static final String HEADER_BAR_CONTROLLER = "com.faforever.client.headerbar.HeaderBarController";
    private static final String HEADER_BAR_CONTROLLER_ASM = HEADER_BAR_CONTROLLER.replace('.', '/');
    public static final String NAVIGATION_ITEM = "com.faforever.client.main.event.NavigationItem";
    private static final String NAVIGATION_ITEM_ASM = NAVIGATION_ITEM.replace('.', '/');
    private static final String NAVIGATION_ITEM_DESC = "L" + NAVIGATION_ITEM_ASM + ";";
    private static final ArrayList<String> tabs = new ArrayList<>();
    private static boolean postInitDone = false;

    static void postInit() {
        if (postInitDone) return;
        postInitDone = true;
        Main.getLaunchClassLoader().addLoaderResourceLoadingPatch("theme/main.fxml");
        Main.getLaunchClassLoader().addLoaderResourceLoadingPatch(HEADER_BAR_PATH);
        for (String tab : tabs) {
            Main.getLaunchClassLoader().addLoaderResourceLoadingPatch(tab);
            String subStr = tab.substring(0, tab.length() - 9);
            String translateKey = subStr + ".tab";
            TranslationHelper.addFallbackTranslation(translateKey, translateKey);
        }
        try {
            Class.forName(HEADER_BAR_CONTROLLER, false, Main.getLaunchClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to patch HeaderBarController", e);
        }
        try {
            Class.forName(NAVIGATION_ITEM, false, Main.getLaunchClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to patch NavigationItem", e);
        }
    }

    public static void addTab(String entryName) {
        if (postInitDone) throw new IllegalStateException("Post Init already done!");
        if (!tabs.contains(entryName)) {
            if (DEBUG) {
                System.out.println("New tab: " + entryName);
            }
            tabs.add(entryName);
        }
        System.out.println(tabs);
    }

    static byte[] patchHeaderBar(byte[] bytes) {
        if (tabs.isEmpty()) return bytes;
        String string = new String(bytes, StandardCharsets.UTF_8);
        int i = string.indexOf(HEADER_BAR_MATCHING) + HEADER_BAR_MATCHING.length();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(string, 0, i);
        for (String tab : tabs) {
            String subStr = tab.substring(0, tab.length() - 9);
            String id = "flm_" + subStr.replace('.', '_').replace('-', '_');
            stringBuilder.append("\n" + "                <ToggleButton fx:id=\"").append(id)
                    .append("\" minWidth=\"-Infinity\" mnemonicParsing=\"false\"\n")
                    .append("                              ")
                    .append("onAction=\"#onNavigateButtonClicked\" styleClass=\"main-navigation-button\"\n")
                    .append("                              ")
                    .append("text=\"%").append(subStr).append(".tab\" toggleGroup=\"$mainNavigation\"/>");
        }
        stringBuilder.append(string, i, string.length());
        System.out.println(stringBuilder);
        return stringBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] patchHeaderBarController(byte[] bytes) {
        if (!postInitDone) throw new IllegalStateException("Post init not done!");
        if (tabs.isEmpty()) return bytes;
        ClassReader classReader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.SKIP_FRAMES);
        MethodNode onInitialize = BytePatches.getMethod(classNode, "onInitialize");
        MethodInsnNode lastPut = null;
        for (AbstractInsnNode abstractInsnNode : onInitialize.instructions) {
            if (abstractInsnNode.getOpcode() == Opcodes.INVOKEINTERFACE) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;
                if (methodInsnNode.owner.equals("java/util/Map")) {
                    if (methodInsnNode.name.equals("forEach")) {
                        break;
                    }
                    if (methodInsnNode.name.equals("put")) {
                        lastPut = methodInsnNode;
                    }
                }
            }
        }
        InsnList newButtonsEntry = new InsnList();
        for (String tab : tabs) {
            String subStr = tab.substring(0, tab.length() - 9);
            String id = "flm_" + subStr.replace('.', '_').replace('-', '_');
            String enumBig = id.toUpperCase(Locale.ROOT);
            classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,
                    id, "Ljavafx/scene/control/ToggleButton;", null, null));
            newButtonsEntry.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            newButtonsEntry.add(new FieldInsnNode(Opcodes.GETFIELD,
                    HEADER_BAR_CONTROLLER_ASM, "navigationItemMap", "Ljava/util/Map;")); // map
            newButtonsEntry.add(new VarInsnNode(Opcodes.ALOAD, 0)); // map this
            newButtonsEntry.add(new FieldInsnNode(Opcodes.GETFIELD,
                    HEADER_BAR_CONTROLLER_ASM, id, "Ljavafx/scene/control/ToggleButton;")); // map button
            newButtonsEntry.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    NAVIGATION_ITEM_ASM, enumBig, NAVIGATION_ITEM_DESC)); // map button navItem
            newButtonsEntry.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                    "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true)); // ret
            newButtonsEntry.add(new InsnNode(Opcodes.POP)); //
        }
        onInitialize.instructions.insert(BytePatches.nextCodeInsn(lastPut), newButtonsEntry);
        try {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("Failed to write " + classNode.name, e);
        }
    }

    static byte[] patchNavigationItemEnum(byte[] bytes) {
        if (!postInitDone) throw new IllegalStateException("Post init not done!");
        if (tabs.isEmpty()) return bytes;
        ClassReader classReader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.SKIP_FRAMES);
        for (String tab : tabs) {
            String subStr = tab.substring(0, tab.length() - 9);
            String enumBig = "FLM_" + (subStr.replace('.', '_')
                    .replace('-', '_').toUpperCase(Locale.ROOT));
            BytePatches.patchEnum(classNode, enumBig, tab, subStr + ".tab");
        }
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }
}
