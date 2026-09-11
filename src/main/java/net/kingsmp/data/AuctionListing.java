package net.kingsmp.data;

import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public class AuctionListing {
    private final long id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final int price;

    public AuctionListing(long id, UUID sellerUuid, String sellerName, ItemStack item, int price) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
    }

    public long getId() { return id; }
    public UUID getSellerUuid() { return sellerUuid; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItem() { return item; }
    public int getPrice() { return price; }
}