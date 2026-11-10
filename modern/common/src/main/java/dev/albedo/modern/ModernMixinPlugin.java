package dev.albedo.modern;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Leaves out the custom sky draw when OptiFine is present, because OptiFine changes the sky code of the game and a
 * required sky mixin that does not match it stops the game; the texture repaint stays.
 */
public final class ModernMixinPlugin implements IMixinConfigPlugin {
    private static final boolean OPTIFINE = ModernMixinPlugin.class.getClassLoader().getResource("net/optifine/Config.class") != null;

    @Override
    public void onLoad(String mixinPackage) {
        if (OPTIFINE) LoggerFactory.getLogger("albedo").warn("OptiFine is present, so the sky stays vanilla");
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return !(OPTIFINE && (name.startsWith("SkyLayers") || name.startsWith("SkySunrise")));
    }

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
