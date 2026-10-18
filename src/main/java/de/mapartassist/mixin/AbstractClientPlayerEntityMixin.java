package de.mapartassist.mixin;

import de.mapartassist.cape.CapeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void mapartassist$ownCape(CallbackInfoReturnable<SkinTextures> cir) {
        // Nur der eigene Spieler bekommt das lokale Cape.
        if ((Object) this != MinecraftClient.getInstance().player) return;
        SkinTextures result = CapeManager.apply(cir.getReturnValue());
        if (result != cir.getReturnValue()) cir.setReturnValue(result);
    }
}
