package net.kingsmp.mixin;

import net.kingsmp.shop.ShopScreenHandler;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerLevel;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleSignUpdate", at = @At("HEAD"), cancellable = true)
    private void onHandleSignUpdate(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
        if (ShopScreenHandler.activeSearchSessions.containsKey(player.getUUID())) {
            ShopScreenHandler.SearchSession session = ShopScreenHandler.activeSearchSessions.remove(player.getUUID());
            if (session != null) {
                // Restore original block state
                ServerLevel level = (ServerLevel) player.level();
                level.setBlock(session.pos, session.oldState, 3);

                // Build query string from non-empty sign lines
                StringBuilder queryBuilder = new StringBuilder();
                for (String line : packet.getLines()) {
                    if (line != null && !line.trim().isEmpty()) {
                        if (queryBuilder.length() > 0) {
                            queryBuilder.append(" ");
                        }
                        queryBuilder.append(line.trim());
                    }
                }
                String query = queryBuilder.toString().trim();

                // Re-open shop with search query applied on main server thread
                player.level().getServer().execute(() -> {
                    ShopScreenHandler.reopenShop(player, session.returnTab, session.returnSortMode, query.isEmpty() ? null : query);
                });

                ci.cancel();
            }
        }
    }
}
