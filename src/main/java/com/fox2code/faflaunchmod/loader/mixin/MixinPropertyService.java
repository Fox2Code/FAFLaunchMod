package com.fox2code.faflaunchmod.loader.mixin;

import org.spongepowered.asm.service.IPropertyKey;

import java.util.HashMap;

public class MixinPropertyService implements org.spongepowered.asm.service.IGlobalPropertyService {
    private static final HashMap<String, Object> mixinProperties = new HashMap<>();

    static class Key implements IPropertyKey {

        private final String key;

        Key(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return this.key;
        }
    }

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> T getProperty(IPropertyKey key) {
        return (T) mixinProperties.get(key.toString());
    }

    @Override
    public final void setProperty(IPropertyKey key, Object value) {
        mixinProperties.put(key.toString(), value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) mixinProperties.getOrDefault(key.toString(), defaultValue);
    }

    @Override
    public final String getPropertyString(IPropertyKey key, String defaultValue) {
        return mixinProperties.getOrDefault(key.toString(), defaultValue).toString();
    }
}
