package com.fox2code.faflaunchmod.loader;

import com.fox2code.faflaunchmod.launcher.Main;
import io.github.karlatemp.unsafeaccessor.Root;
import io.github.karlatemp.unsafeaccessor.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

public final class ReflectHelper {
    private ReflectHelper() {}

    static void setSystemClassLoader(ClassLoader classLoader) throws NoSuchFieldException, IllegalAccessException {
        ReflectHelper.getDeclaredFieldRoot(ClassLoader.class, "scl").set(null, classLoader);
    }

    public static Field getDeclaredFieldRoot(String cls, String name) throws NoSuchFieldException, ClassNotFoundException {
        return getDeclaredFieldRoot(Class.forName(cls, false, Main.getLaunchClassLoader()), name);
    }

    public static Field getDeclaredFieldRoot(Class<?> cls, String name) throws NoSuchFieldException {
        try {
            return Root.openAccess(cls.getDeclaredField(name));
        } catch (NoSuchFieldException e) {
            try { // Tinker with JVM internals if needed.
                Field[] fields = (Field[]) Root.openAccess(
                        Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class)).invoke(cls, false);
                for (Field field : fields) {
                    if (field.getName().equals(name)) {
                        return Root.openAccess(field);
                    }
                }
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException("Failed to get field " + name, ex);
            }
            throw e;
        }
    }

    public static void setFinalField(Object instance, String field, Object value) throws NoSuchFieldException, IllegalAccessException {
        setFinalField(instance, getDeclaredFieldRoot(instance.getClass(), field), value);
    }

    public static void setFinalField(Object instance, Field field, Object value) throws IllegalAccessException {
        Unsafe unsafe = Root.getUnsafe();
        field.getType().cast(value);
        if (!field.canAccess(instance)) {
            throw new IllegalAccessException();
        }
        if (!Modifier.isFinal(field.getModifiers())) {
            field.set(instance, field);
        } else if (Modifier.isStatic(field.getModifiers())) {
            unsafe.putReference(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
        } else {
            Objects.requireNonNull(instance, "instance");
            field.getDeclaringClass().cast(instance);
            unsafe.putReference(instance, unsafe.objectFieldOffset(field), value);
        }
    }
}
