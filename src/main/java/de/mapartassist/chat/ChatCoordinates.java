package de.mapartassist.chat;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Shows the sender's coordinates when that player is loaded in the local client world. */
public final class ChatCoordinates {
    private ChatCoordinates() {}

    public static void init() {
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, parameters, receptionTimestamp) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || sender == null) return;

           PlayerEntity player = client.world.getPlayerByUuid(sender.id());
            Text line;
            if (player != null) {
                int x = (int) Math.floor(player.getX());
                int y = (int) Math.floor(player.getY());
                int z = (int) Math.floor(player.getZ());
                line = Text.literal("  ↳ Position: X: " + x + " · Y: " + y + " · Z: " + z)
                        .formatted(Formatting.GREEN);
            } else {
                line = Text.literal("  ↳ Position unbekannt (Spieler nicht lokal geladen)")
                        .formatted(Formatting.GRAY);
            }
            client.execute(() -> {
                if (client.inGameHud != null) client.inGameHud.getChatHud().addMessage(line);
            });
        });
    }
}
