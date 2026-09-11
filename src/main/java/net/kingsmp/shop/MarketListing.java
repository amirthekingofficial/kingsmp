package net.kingsmp.shop;

import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public class MarketListing {
    private final long id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack itemToSell;

    private final int silverPrice;       // For virtual currency
    private final boolean isServerShop;

    // Sell for VIRTUAL SILVER (with ID)
    public MarketListing(long id, UUID sellerId, String sellerName, ItemStack itemToSell, int silverPrice, boolean isServerShop) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.itemToSell = itemToSell.copy();
        this.silverPrice = silverPrice;
        this.isServerShop = isServerShop;
    }

    // Sell for VIRTUAL SILVER (auto-generate ID)
    public MarketListing(UUID sellerId, String sellerName, ItemStack itemToSell, int silverPrice, boolean isServerShop) {
        this(System.nanoTime(), sellerId, sellerName, itemToSell, silverPrice, isServerShop);
    }



    public long getId() { return id; }
    public UUID getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItemToSell() { return itemToSell; }
    public int getSilverPrice() { return silverPrice; }
    public boolean isServerShop() { return isServerShop; }
}