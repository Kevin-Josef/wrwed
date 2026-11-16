package de.mapartassist.mixin;

import de.mapartassist.esp.CamPose;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Liest das tatsaechlich benutzte FOV mit (Zoomify, Ok Zoomer, Sprint, Traenke ...), damit die ESP-Projektion
 * beim Zoomen deckungsgleich bleibt. require = 0: Wird die Methode in einer anderen Version nicht gefunden,
 * laeuft die Mod trotzdem (dann mit dem alten FOV aus den Optionen).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), require = 0)
    private void mapartassist$captureFov(CallbackInfoReturnable<?> cir) {
        Object v = cir.getReturnValue();
        if (v instanceof Number n) CamPose.captureFov(n.doubleValue());
    }
}
