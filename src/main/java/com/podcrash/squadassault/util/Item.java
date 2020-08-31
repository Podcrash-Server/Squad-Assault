package com.podcrash.squadassault.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * this is a basic wrapper class for ItemStacks without having to worry about the amount of the item. the reason this
 * is helpful is because the stack count is used for ammo, so this avoids that
 */
public class Item {

    private String name;
    private byte data;
    private Material material;

    public Item(ItemStack itemStack) {
        this.material = itemStack.getType();
        this.data = itemStack.getData().getData();
        this.name = itemStack.getItemMeta().getDisplayName();
    }

    public Item(Material material, byte data, String name) {
        this.material = material;
        this.data = data;
        this.name = name;
    }

    public boolean equals(ItemStack itemStack) {
        return itemStack != null && itemStack.getItemMeta() != null && itemStack.getType() == material && itemStack.getItemMeta().getDisplayName().contains(name);
    }

    public boolean equals(ItemStack itemStack, String s) {
        return itemStack != null && itemStack.getItemMeta() != null && itemStack.getType() == material && itemStack.getItemMeta().getDisplayName().equals(name + " §7" + s);
    }

    public Material getType() {
        return this.material;
    }

    public String getName() {
        return this.name;
    }

    public byte getData() {
        return this.data;
    }


}