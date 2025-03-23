package com.fox2code.faflaunchmod.loader.bytepatch;

import com.fox2code.faflaunchmod.loader.StyleHelper;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.IdentityHashMap;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class BytePatches {
    public static final int ASM_BUILD = Opcodes.ASM9;
    private static final String FAFLaunchCompatController =
            "com/fox2code/faflaunchmod/ui/FAFLaunchCompatController";
    private static final String Controller = "com/faforever/client/fx/Controller";
    private static final String FAFLaunchCompatNodeController =
            "com/fox2code/faflaunchmod/ui/FAFLaunchCompatNodeController";
    private static final String NodeController = "com/faforever/client/fx/NodeController";

    private BytePatches() {}

    public static byte[] patchThemeService(byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.SKIP_FRAMES);
        MethodNode getThemeFileUrl = getMethod(classNode, "getThemeFileUrl");
        LdcInsnNode fileLdcInsnNode = null;
        for (AbstractInsnNode abstractInsnNode : getThemeFileUrl.instructions) {
            if (abstractInsnNode.getOpcode() == Opcodes.LDC) {
                LdcInsnNode ldcInsnNode = (LdcInsnNode) abstractInsnNode;
                if ("file:".equals(ldcInsnNode.cst)) {
                    fileLdcInsnNode = ldcInsnNode;
                    break;
                }
            }
        }
        Objects.requireNonNull(fileLdcInsnNode, "jarLdcInsnNode");
        AbstractInsnNode aload = fileLdcInsnNode;
        while (aload.getOpcode() != Opcodes.ALOAD) {
            aload = previousCodeInsn(aload);
        }
        InsnList isFLM = copyCodeUntil(aload, Opcodes.IFNE);
        for (AbstractInsnNode abstractInsnNode : isFLM) {
            if (abstractInsnNode instanceof LdcInsnNode ldcInsnNode && "file:".equals(ldcInsnNode.cst)) {
                ldcInsnNode.cst = "flm:";
            }
        }
        getThemeFileUrl.instructions.insertBefore(aload, isFLM);
        MethodNode getStylesheets = getMethod(classNode, "getStylesheets");
        boolean patchedInt = false;
        int arrayIndexValue = -1;
        AbstractInsnNode lastAAStore = null;
        for (AbstractInsnNode abstractInsnNode : getStylesheets.instructions) {
            if (!patchedInt) {
                if (abstractInsnNode.getOpcode() == Opcodes.ANEWARRAY) {
                    arrayIndexValue = ((IntInsnNode) abstractInsnNode.getPrevious()).operand;
                    ((IntInsnNode) abstractInsnNode.getPrevious()).operand++;
                    patchedInt = true;
                }
            } else if (abstractInsnNode.getOpcode() == Opcodes.AASTORE) {
                lastAAStore = abstractInsnNode;
            }
        }
        Objects.requireNonNull(lastAAStore, "lastAAStore");
        InsnList faflaunchmodAsset = new InsnList();
        faflaunchmodAsset.add(new InsnNode(Opcodes.DUP));
        faflaunchmodAsset.add(getNumberInsn(arrayIndexValue));
        faflaunchmodAsset.add(new LdcInsnNode("flm:" + StyleHelper.STYLE_EXTENSIONS_PATH));
        faflaunchmodAsset.add(new InsnNode(Opcodes.AASTORE));
        getStylesheets.instructions.insert(lastAAStore, faflaunchmodAsset);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }

    public static void genericPatch(ClassNode classNode) {
        if ("java/lang/Object".equals(classNode.superName) &&
                (classNode.access & Opcodes.ACC_INTERFACE) == 0) {
            if (classNode.interfaces != null &&
                    classNode.interfaces.contains(FAFLaunchCompatNodeController)) {
                classNode.superName = NodeController;
                MethodNode initialize = findMethod(classNode, "initialize", "()V");
                if (initialize != null) {
                    initialize.name = "onInitialize";
                }
                for (MethodNode methodNode : classNode.methods) {
                    if (methodNode.name.equals("<init>")) {
                        for (AbstractInsnNode abstractInsnNode : methodNode.instructions) {
                            if (abstractInsnNode.getOpcode() == Opcodes.INVOKESPECIAL &&
                                    abstractInsnNode instanceof MethodInsnNode methodInsnNode &&
                                    methodInsnNode.owner.equals("java/lang/Object") &&
                                    methodInsnNode.name.equals("<init>")) {
                                methodInsnNode.owner = NodeController;
                            }
                        }
                    }
                }
            }
        }
    }

    @NotNull
    public static MethodNode getMethod(ClassNode classNode, String methodName) {
        return findMethod0(classNode, methodName, null, true);
    }

    @NotNull
    public static MethodNode getMethod(ClassNode classNode, String methodName, String methodDesc) {
        return findMethod0(classNode, methodName, methodDesc, true);
    }

    @Nullable
    public static MethodNode findMethod(ClassNode classNode, String methodName) {
        return findMethod0(classNode, methodName, null, false);
    }

    @Nullable
    public static MethodNode findMethod(ClassNode classNode, String methodName, String methodDesc) {
        return findMethod0(classNode, methodName, methodDesc, false);
    }

    @Contract("_, _, _, true -> !null")
    private static MethodNode findMethod0(ClassNode classNode, String methodName, String methodDesc, boolean require) {
        MethodNode bridgeMethodNode = null;
        for (MethodNode methodNode:classNode.methods) {
            if (methodNode.name.equals(methodName)
                    && (methodDesc == null || methodNode.desc.equals(methodDesc))) {
                if ((methodNode.access & Opcodes.ACC_BRIDGE) != 0) {
                    bridgeMethodNode = methodNode;
                } else return methodNode;
            }
        }
        if (bridgeMethodNode != null) {
            return bridgeMethodNode;
        }
        if (require) {
            throw new NoSuchElementException(classNode.name + "." +
                    methodName + (methodDesc == null ? "()" : methodDesc));
        } else {
            return null;
        }
    }

    @NotNull
    public static InsnList copyCodeUntil(final AbstractInsnNode start, int endOpCode) {
        AbstractInsnNode abstractInsnNode = start;
        IdentityHashMap<LabelNode, LabelNode> map = new IdentityHashMap<>() {
            @Override
            public LabelNode get(Object key) {
                LabelNode labelNode = super.get(key);
                return labelNode == null ? (LabelNode) key : labelNode;
            }
        };
        while (abstractInsnNode != null &&
                abstractInsnNode.getOpcode() != endOpCode) {
            if (abstractInsnNode instanceof LabelNode) {
                map.put((LabelNode) abstractInsnNode, new LabelNode());
            }
            abstractInsnNode = abstractInsnNode.getNext();
        }
        if (abstractInsnNode == null) {
            throw new IllegalArgumentException("Opcodes " + endOpCode + " isn't present after the given instruction");
        }
        InsnList copy = new InsnList();
        abstractInsnNode = start;
        while (abstractInsnNode.getOpcode() != endOpCode) {
            copy.add(abstractInsnNode.clone(map));
            abstractInsnNode = abstractInsnNode.getNext();
        }
        copy.add(abstractInsnNode.clone(map));
        return copy;
    }

    public static AbstractInsnNode nextCodeInsn(AbstractInsnNode abstractInsnNode) {
        do {
            abstractInsnNode = abstractInsnNode.getNext();
        } while (abstractInsnNode != null && abstractInsnNode.getOpcode() == -1);
        return abstractInsnNode;
    }

    public static AbstractInsnNode previousCodeInsn(AbstractInsnNode abstractInsnNode) {
        do {
            abstractInsnNode = abstractInsnNode.getPrevious();
        } while (abstractInsnNode != null && abstractInsnNode.getOpcode() == -1);
        return abstractInsnNode;
    }

    public static AbstractInsnNode getNumberInsn(int number) {
        if (number >= -1 && number <= 5)
            return new InsnNode(number + 3);
        else if (number >= -128 && number <= 127)
            return new IntInsnNode(Opcodes.BIPUSH, number);
        else if (number >= -32768 && number <= 32767)
            return new IntInsnNode(Opcodes.SIPUSH, number);
        else
            return new LdcInsnNode(number);
    }
    public static AbstractInsnNode getObjectInsn(Object object) {
        if (object instanceof String) {
            return new LdcInsnNode(object);
        } else if (object instanceof Integer integer) {
            return getNumberInsn(integer);
        } else {
            throw new IllegalArgumentException("Does not support " + object.getClass() + " yet!");
        }
    }

    // This code is made specifically for the compiler the FAF Launcher use, and may not work for other compilers.
    public static void patchEnum(ClassNode classNode, String name, Object... args) {
        final String classDesc = "L" + classNode.name + ";";
        MethodNode clinit = getMethod(classNode, "<clinit>");
        MethodNode $values = getMethod(classNode, "$values");
        StringBuilder desc = new StringBuilder().append("(Ljava/lang/String;I");
        for (Object object : args) {
            if (object instanceof String) {
                desc.append("Ljava/lang/String;");
            } else if (object instanceof Integer) {
                desc.append("I");
            } else {
                throw new IllegalArgumentException("Does not support " + object.getClass() + " yet!");
            }
        }
        desc.append(")V");
        boolean patchedInt = false;
        int arrayIndexValue = -1;
        AbstractInsnNode lastAAStore = null;
        for (AbstractInsnNode abstractInsnNode : $values.instructions) {
            if (!patchedInt) {
                if (abstractInsnNode.getOpcode() == Opcodes.ANEWARRAY) {
                    arrayIndexValue = ((IntInsnNode) abstractInsnNode.getPrevious()).operand;
                    ((IntInsnNode) abstractInsnNode.getPrevious()).operand++;
                    patchedInt = true;
                }
            } else if (abstractInsnNode.getOpcode() == Opcodes.AASTORE) {
                lastAAStore = abstractInsnNode;
            }
        }
        Objects.requireNonNull(lastAAStore, "lastAAStore");
        InsnList enumNewEntry = new InsnList();
        enumNewEntry.add(new InsnNode(Opcodes.DUP));
        enumNewEntry.add(getNumberInsn(arrayIndexValue));
        enumNewEntry.add(new FieldInsnNode(Opcodes.GETSTATIC,
                classNode.name, name, classDesc));
        enumNewEntry.add(new InsnNode(Opcodes.AASTORE));
        $values.instructions.insert(lastAAStore, enumNewEntry);
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC |
                Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
                name, classDesc, null, null));
        InsnList newEnumEntry = new InsnList();
        newEnumEntry.add(new TypeInsnNode(Opcodes.NEW, classNode.name));
        newEnumEntry.add(new InsnNode(Opcodes.DUP));
        newEnumEntry.add(getObjectInsn(name));
        newEnumEntry.add(getObjectInsn(arrayIndexValue));
        for (Object object : args) {
            newEnumEntry.add(getObjectInsn(object));
        }
        newEnumEntry.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                classNode.name, "<init>", desc.toString(), false));
        newEnumEntry.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                classNode.name, name, classDesc));
        AbstractInsnNode lastPutStatic = null;
        for (AbstractInsnNode abstractInsnNode : clinit.instructions) {
            if (abstractInsnNode.getOpcode() == Opcodes.PUTSTATIC) {
                FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;
                if (fieldInsnNode.owner.equals(classNode.name) &&
                        fieldInsnNode.desc.equals(classDesc)) {
                    lastPutStatic = abstractInsnNode;
                }
            }
        }
        clinit.instructions.insert(lastPutStatic, newEnumEntry);
    }


    /**
     * @param bytes raw class
     * @param checkAsIs if frames and max values should be checked.
     */
    public static void checkBytecodeValidity(final byte[] bytes, boolean checkAsIs) {
        final String[] fullMethodNameRegister = new String[]{null, null, null};
        try {
            new ClassReader(bytes).accept(new ClassVisitor(ASM_BUILD, new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    return "java/lang/Object";
                }
            }) {
                String className;

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    if (name == null) throw new RuntimeException("Name is null");
                    if (superName == null) throw new RuntimeException("Super name is null");
                    super.visit(version, access, name, signature, superName, interfaces);
                    this.className = name;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    final String fullMethodName = this.className + "." + name + descriptor;
                    final String methodName = name;
                    final String methodDescriptor = descriptor;
                    fullMethodNameRegister[0] = fullMethodName;
                    fullMethodNameRegister[1] = methodName;
                    fullMethodNameRegister[2] = methodDescriptor;
                    try {
                        return new AdviceAdapter(ASM_BUILD,
                                super.visitMethod(access, name, descriptor, signature, exceptions),
                                access, name, descriptor) {
                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                try {
                                    super.visitMaxs(maxStack, maxLocals);
                                } catch (RuntimeException e) {
                                    throw new RuntimeException("For method " + fullMethodName +
                                            dumpMethod(bytes, methodName, methodDescriptor), e);
                                }
                                if (checkAsIs) {
                                    int argSize = Type.getType(descriptor).getArgumentsAndReturnSizes() >> 2;
                                    if ((access & ACC_STATIC) != 0) argSize--;
                                    if (maxLocals < argSize) {
                                        throw new RuntimeException("For method " + fullMethodName +
                                                dumpMethod(bytes, methodName, methodDescriptor),
                                                new RuntimeException("MaxLocals too small got " +
                                                        maxLocals + " but need " + argSize));
                                    }
                                }
                            }

                            @Override
                            public void visitEnd() {
                                try {
                                    super.visitEnd();
                                } catch (RuntimeException e) {
                                    throw new RuntimeException("For method " + fullMethodName +
                                            dumpMethod(bytes, methodName, methodDescriptor), e);
                                }
                            }

                            @Override
                            public void visitMethodInsn(int opcodeAndSource, String owner, String name, String descriptor, boolean isInterface) {
                                try {
                                    super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface);
                                } catch (RuntimeException e) {
                                    throw new RuntimeException("For method " + fullMethodName +
                                            dumpMethod(bytes, methodName, methodDescriptor), e);
                                }
                                if (Type.getType(descriptor).getSort() != Type.METHOD) {
                                    final String fullDescriptor = owner + "." + name + " " + descriptor;
                                    throw new RuntimeException("For method " + fullMethodName +
                                            dumpMethod(bytes, methodName, methodDescriptor),
                                            new RuntimeException("Invalid method call " + fullDescriptor));
                                }
                            }
                        };
                    } catch (RuntimeException e) {
                        throw new RuntimeException("For method " + fullMethodName +
                                dumpMethod(bytes, methodName, methodDescriptor), e);
                    }
                }
            }, ClassReader.SKIP_FRAMES);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("For method " + fullMethodNameRegister[0] +
                    dumpMethod(bytes, fullMethodNameRegister[1], fullMethodNameRegister[2]), e);
        }
    }

    private static String dumpMethod(byte[] bytes, String name, String desc) {
        ClassNode classNode = new ClassNode();
        boolean cantReadClassFile = false;
        try {
            new ClassReader(bytes).accept(classNode, 0);
        } catch (Throwable t) {
            cantReadClassFile = true;
        }
        MethodNode methodNode = BytePatches.findMethod(classNode, name, desc);
        return methodNode == null ? (cantReadClassFile ?
                (classNode.name == null ? "\nCan't read class file" : "\nCan't fully read class file") :
                "\nMissing method body") : "\n" + BytePatches.printInsnList(methodNode.instructions) +
                        (cantReadClassFile ? "\nFailed to fully read class file" : "");
    }

    public static String printInsnList(final InsnList insnList) {
        final StringBuilder stringBuilder = new StringBuilder();
        printInsnList(insnList, stringBuilder);
        return stringBuilder.toString();
    }

    public static void printInsnList(final InsnList insnList,final StringBuilder stringBuilder) {
        Textifier textifier = new Textifier();
        MethodNode methodNode = new MethodNode(0, "insns", "()V", null, null);
        methodNode.instructions = insnList;
        methodNode.accept(new TraceMethodVisitor(textifier));
        textifier.print(new PrintWriter(new Writer() {
            @Override
            public void write(@NotNull String str, int off, int len) {
                stringBuilder.append(str, off, len);
            }

            @Override
            public void write(char @NotNull [] cbuf, int off, int len) {
                stringBuilder.append(cbuf, off, len);
            }

            @Override public void flush() {}
            @Override public void close() {}
        }));
    }
}
