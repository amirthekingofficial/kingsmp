package net.kingsmp.factions;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FactionScreenHandler extends ChestMenu {

    public FactionScreenHandler(int syncId, Inventory playerInventory, String factionName, List<UUID> members) {
        super(MenuType.GENERIC_9x6, syncId, playerInventory, new SimpleContainer(54), 6);
        populate(factionName, members, playerInventory.player);
    }

    private void populate(String factionName, List<UUID> members, Player viewer) {
        this.getContainer().clearContent();
        
        for (int i = 0; i < Math.min(members.size(), 54); i++) {
            UUID memberUuid = members.get(i);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            
            String playerName = memberUuid.toString().substring(0, 8);
            net.minecraft.server.MinecraftServer server = viewer.level().getServer();
            ServerPlayer memberEntity = server != null ? server.getPlayerList().getPlayer(memberUuid) : null;
            
            if (memberEntity != null) {
                playerName = memberEntity.getScoreboardName();
            }

            head.set(DataComponents.CUSTOM_NAME, Component.literal(playerName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            
            FactionManager.FactionRank rank = FactionManager.getPlayerRankByUuid(memberUuid);
            String rankName = rank != null ? rank.name() : "MEMBER";
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Rank: " + rankName).withStyle(ChatFormatting.AQUA));
            lore.add(Component.literal(memberEntity != null ? "Online" : "Offline").withStyle(memberEntity != null ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            
            head.set(DataComponents.LORE, new ItemLore(lore));
            
            this.getContainer().setItem(i, head);
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        // Prevent all clicks in the top inv
        if (slotIndex >= 0 && slotIndex < 54) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.containerMenu.broadcastFullState();
            }
            return;
        }
        if (actionType == ContainerInput.QUICK_MOVE) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.containerMenu.broadcastFullState();
            }
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
    }
}
