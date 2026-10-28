package de.mapartassist.mixin;

import de.mapartassist.crystal.CrystalLimit;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    /** Crystal-Limit: Rechtsklick mit End-Crystal auf Obsidian verwerfen, wenn das Limit erreicht ist. */
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void mapartassist$crystalLimit(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult,
                                           CallbackInfoReturnable<ActionResult> cir) {
        if (CrystalLimit.shouldBlock(player, hand, hitResult)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
