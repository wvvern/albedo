package dev.albedo.legacy;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Selects the mixins for the game with or without OptiFine, because OptiFine changes the signature of some targets
 * (for example it makes FaceBakery.getFaceBrightness static) and a mixin that does not match stops the game.
 */
public final class MixinPlugin implements IMixinConfigPlugin {
    /** The Forge tweaker of OptiFine, whose jar FML puts on the class path before the mixins apply. */
    private static final boolean OPTIFINE = MixinPlugin.class.getClassLoader().getResource("optifine/OptiFineForgeTweaker.class") != null;

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (name.startsWith("OptiFine")) return OPTIFINE;
        if (name.endsWith("VanillaMixin")) return !OPTIFINE;
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith(".OptiFineRenderMixin")) OptiFineRenderPatch.apply(targetClass);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
