package com.fox2code.faflaunchmod.utils;

import java.io.File;
import java.net.URISyntaxException;
import java.security.CodeSource;

public class SourceUtil {
    private SourceUtil() {}
    public static File getSourceFile(Class<?> cls) {
        CodeSource codeSource = cls.getProtectionDomain().getCodeSource();
        try {
            return new File(codeSource.getLocation().toURI().getPath()).getAbsoluteFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static File getSourceFileOfClassName(String cls) {
        try {
            return getSourceFile(Class.forName(cls, false, Thread.currentThread().getContextClassLoader()));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
