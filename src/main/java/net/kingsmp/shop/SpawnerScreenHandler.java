package net.kingsmp.shop;

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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpawnerScreenHandler extends ChestMenu {

    private final int tier;
    private final InteractionHand hand;
    private final net.minecraft.world.Container guiInventory;

    // Map slot index to mob type
    private final Map<Integer, String> slotToMob = new HashMap<>();

    public SpawnerScreenHandler(int syncId, Inventory playerInventory, int tier, InteractionHand hand) {
        super(MenuType.GENERIC_9x3, syncId, playerInventory, new SimpleContainer(27), 3);
        this.tier = tier;
        this.hand = hand;
        this.guiInventory = this.getContainer();
        setupItems();
    }

    private void setupItems() {
        guiInventory.clearContent();

        // Filler panes
        ItemStack filler = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        filler.set(DataComponents.CUSTOM_NAME, Component.empty());
        for (int i = 0; i < 27; i++) {
            guiInventory.setItem(i, filler);
        }

        // Add Mobs
        addMobItem(10, Items.ZOMBIE_SPAWN_EGG, "Zombie", "Rotten Flesh, Iron, Carrot, Potato, Zombie Head");
        addMobItem(11, Items.SKELETON_SPAWN_EGG, "Skeleton", "Bone, Arrow, Bow, Skeleton Skull");
        addMobItem(12, Items.CREEPER_SPAWN_EGG, "Creeper", "Gunpowder, Music Discs, Creeper Head");
        addMobItem(13, Items.SPIDER_SPAWN_EGG, "Spider", "String, Spider Eye, Cobweb");
        addMobItem(14, Items.BLAZE_SPAWN_EGG, "Blaze", "Blaze Rod, Fire Charge");
        addMobItem(15, Items.ENDERMAN_SPAWN_EGG, "Enderman", "Ender Pearl, Eye of Ender");
        addMobItem(16, Items.SLIME_SPAWN_EGG, "Slime", "Slimeball, Slime Block");
        addMobItem(17, Items.WITCH_SPAWN_EGG, "Witch", "Glowstone, Redstone, Gunpowder, Potion");
    }

    private void addMobItem(int slot, net.minecraft.world.item.Item item, String mobName, String dropsPreview) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, 
            Component.literal(mobName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Tier " + tier + " Loot Multiplier").withStyle(ChatFormatting.YELLOW));
        lore.add(Component.literal("Drops:").withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal(dropsPreview).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        lore.add(Component.empty());
        lore.add(Component.literal("Click to select and harvest!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

        stack.set(DataComponents.LORE, new ItemLore(lore));
        guiInventory.setItem(slot, stack);
        slotToMob.put(slot, mobName);
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        if (slotIndex < 0 || slotIndex >= 27) {
            super.clicked(slotIndex, button, actionType, player);
            return;
        }

        if (slotToMob.containsKey(slotIndex)) {
            String mob = slotToMob.get(slotIndex);
            
            // Verify they still have the spawner
            ItemStack heldItem = player.getItemInHand(hand);
            if (heldItem.is(Items.SPAWNER)) {
                heldItem.shrink(1);
                
                // Generate and award loot
                List<ItemStack> drops = SpawnerLootTable.generateLoot(mob, tier);
                for (ItemStack drop : drops) {
                    if (!player.getInventory().add(drop)) {
                        player.drop(drop, false);
                    }
                }
                
                net.kingsmp.KingSMPMod.playSoundToPlayer(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                net.kingsmp.KingSMPMod.playSoundToPlayer(player, SoundEvents.SPAWNER_PLACE, 1.0f, 1.0f);
                
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.closeContainer();
                }
            } else {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.closeContainer();
                }
            }
            return;
        }
        
        // Prevent taking items out of the custom GUI
        this.broadcastFullState();
    }
}
