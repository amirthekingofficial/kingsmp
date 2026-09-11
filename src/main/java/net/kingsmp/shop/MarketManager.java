package net.kingsmp.shop;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MarketManager {
    // Holds all active listings
    private final List<MarketListing> activeListings = new ArrayList<>();

    // NEW: Holds payments for offline players!
    private final Map<UUID, List<ItemStack>> offlineMailbox = new HashMap<>();

    public void addListing(MarketListing listing) {
        activeListings.add(listing);
    }

    public void removeListing(MarketListing listing) {
        activeListings.remove(listing);
    }

    public List<MarketListing> getActiveListings() {
        return activeListings;
    }

    // ── MAILBOX METHODS ───────────────────────────────────────────────────

    public void deliverToMailbox(UUID playerId, ItemStack payment) {
        // If they don't have a mailbox list yet, create one, then add the item
        offlineMailbox.computeIfAbsent(playerId, k -> new ArrayList<>()).add(payment);
    }

    public List<ItemStack> collectMail(UUID playerId) {
        // Removes their mail from the map and returns it (or null if empty)
        return offlineMailbox.remove(playerId);
    }
}