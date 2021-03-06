package net.kjp12.plymouth.debug;// Created 2021-03-30T00:09:55

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Early riser, avoid calling net.minecraft and any mod classes that may cascade-load, <em>especially the various Plymouth modules</em>.
 *
 * @author KJP12
 * @since 0.0.0
 */
public class MixinConfig implements IMixinConfigPlugin {
    private int substr;

    @Override
    public void onLoad(String mixinPackage) {
        substr = mixinPackage.length();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var loader = FabricLoader.getInstance();
        var index = mixinClassName.indexOf('.', substr);
        String env, cur = mixinClassName.substring(substr, index);
        switch (cur) {
            case "anti_xray": {
                if (!loader.isModLoaded("plymouth-anti-xray")) return false;
                env = getEnvironment(mixinClassName, index);
                break;
            }
            case "tracker": {
                if (!loader.isModLoaded("plymouth-tracker")) return false;
                env = getEnvironment(mixinClassName, index);
                break;
            }
            default:
                env = cur;
        }
        return checkEnvironment(loader, env);
    }

    private String getEnvironment(String in, int s) {
        int e = in.indexOf('.', s);
        return e == -1 ? "" : in.substring(s, e);
    }

    private boolean checkEnvironment(FabricLoader loader, String str) {
        switch (str) {
            case "client":
                return loader.getEnvironmentType() == EnvType.CLIENT;
            case "server":
                return loader.getEnvironmentType() == EnvType.SERVER;
            default:
                return true;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
