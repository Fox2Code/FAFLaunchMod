package com.fox2code.faflaunchmod.mixins;

import io.github.karlatemp.unsafeaccessor.Root;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

@Mixin(AbstractApplicationContext.class)
public class MixinAbstractApplicationContext {
    @Redirect(method = "getBean(Ljava/lang/Class;)Ljava/lang/Object;", at = @At(value = "INVOKE",
            target = "Lorg/springframework/beans/factory/config/ConfigurableListableBeanFactory;getBean(Ljava/lang/Class;)Ljava/lang/Object;"))
    private <T> T getBeanHook(ConfigurableListableBeanFactory configurableListableBeanFactory, Class<T> cls) {
        if (Modifier.isAbstract(cls.getModifiers()) ||
                cls.getName().startsWith("com.faforever.client.")) {
            return configurableListableBeanFactory.getBean(cls);
        }
        try {
            return configurableListableBeanFactory.getBean(cls);
        } catch (NoSuchBeanDefinitionException noSuchBeanDefinitionException) {
            if (cls.getAnnotation(Component.class) == null) {
                throw noSuchBeanDefinitionException;
            }
            try { // Allow class bean to be allocated properly.
                return Root.openAccess(cls.getDeclaredConstructor()).newInstance();
            } catch (InvocationTargetException e) {
                throw new BeanInitializationException("Failed to create bean", e.getCause());
            } catch (ReflectiveOperationException e) {
                throw noSuchBeanDefinitionException;
            }
        }
    }
}
