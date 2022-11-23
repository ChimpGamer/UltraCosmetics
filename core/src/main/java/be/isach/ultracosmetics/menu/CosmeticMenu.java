package be.isach.ultracosmetics.menu;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.UltraCosmeticsData;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.Category;
import be.isach.ultracosmetics.cosmetics.type.CosmeticEntType;
import be.isach.ultracosmetics.cosmetics.type.CosmeticType;
import be.isach.ultracosmetics.menu.menus.MenuMain;
import be.isach.ultracosmetics.menu.menus.MenuPurchase;
import be.isach.ultracosmetics.player.UltraPlayer;
import be.isach.ultracosmetics.util.ItemFactory;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.cryptomorin.xseries.XMaterial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A cosmetic menu.
 *
 * @author iSach
 * @since 08-09-2016
 */
public abstract class CosmeticMenu<T extends CosmeticType<?>> extends Menu {

    public final static int[] COSMETICS_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    protected Category category;

    public CosmeticMenu(UltraCosmetics ultraCosmetics, Category category) {
        super(ultraCosmetics);
        this.category = category;
    }

    @Override
    public void open(UltraPlayer player) {
        open(player, 1);
    }

    public void open(UltraPlayer player, int page) {
        final int maxPages = getMaxPages(player);
        if (page > maxPages) {
            page = maxPages;
        }
        if (page < 1) {
            page = 1;
        }

        Inventory inventory = Bukkit.createInventory(new CosmeticsInventoryHolder(), getSize(), maxPages == 1 ? getName() : getName(page, player));
        boolean hasUnlockable = false;
        for (T type : enabled()) {
            if (!player.hasPermission(type.getPermission())) {
                hasUnlockable = true;
                break;
            }
        }

        // Cosmetic types.
        Map<Integer,T> slots = getSlots(page, player);
        for (Entry<Integer,T> entry : slots.entrySet()) {
            int slot = entry.getKey();
            T cosmeticType = entry.getValue();

            if (shouldHideItem(player, cosmeticType)) continue;

            final int price = SettingsManager.getConfig().getInt(cosmeticType.getConfigPath() + ".Purchase-Price");

            if (SettingsManager.getConfig().getBoolean("No-Permission.Custom-Item.enabled")
                    && !player.hasPermission(cosmeticType.getPermission())) {
                ItemStack stack = ItemFactory.getItemStackFromConfig("No-Permission.Custom-Item.Type");
                String name = ChatColor.translateAlternateColorCodes('&', SettingsManager.getConfig().getString("No-Permission.Custom-Item.Name")).replace("{cosmetic-name}", cosmeticType.getName());
                List<String> npLore = SettingsManager.getConfig().getStringList("No-Permission.Custom-Item.Lore");
                addPurchaseLore(price, npLore, cosmeticType, player);
                String[] array = new String[npLore.size()];
                npLore.toArray(array);
                putItem(inventory, slot, ItemFactory.rename(stack, name, array), clickData -> {
                    Player clicker = clickData.getClicker().getBukkitPlayer();
                    clicker.sendMessage(MessageManager.getMessage("No-Permission"));
                    clicker.closeInventory();
                });
                continue;
            }

            String toggle = category.getActivateTooltip();
            boolean deactivate = hasEquipped(player, cosmeticType);

            if (deactivate) {
                toggle = category.getDeactivateTooltip();
            }

            String typeName = getTypeName(cosmeticType, player);

            final ItemStack is = ItemFactory.rename(cosmeticType.getItemStack(), toggle + " " + typeName);
            if (deactivate) {
                ItemFactory.addGlow(is);
            }

            ItemMeta itemMeta = is.getItemMeta();
            List<String> loreList = new ArrayList<>();

            if (cosmeticType.showsDescription()) {
                loreList.add("");
                loreList.addAll(cosmeticType.getDescription());
                loreList.add("");
            }

            if (SettingsManager.getConfig().getBoolean("No-Permission.Show-In-Lore")) {
                String yesOrNo = player.hasPermission(cosmeticType.getPermission()) ? "Yes" : "No";
                String s = SettingsManager.getConfig().getString("No-Permission.Lore-Message-" + yesOrNo);
                loreList.add(ChatColor.translateAlternateColorCodes('&', s));
            }

            addPurchaseLore(price, loreList, cosmeticType, player);

            itemMeta.setLore(loreList);

            is.setItemMeta(itemMeta);
            filterItem(is, cosmeticType, player);
            putItem(inventory, slot, is, (data) -> {
                boolean success = handleClick(data, cosmeticType, price);
                if (success && UltraCosmeticsData.get().shouldCloseAfterSelect()) {
                    data.getClicker().getBukkitPlayer().closeInventory();
                }
            });
        }

        // Previous page item.
        if (page > 1) {
            int finalPage = page;
            putItem(inventory, getSize() - 18, ItemFactory.rename(ItemFactory.getItemStackFromConfig("Categories.Previous-Page-Item"),
                    MessageManager.getMessage("Menu.Misc.Button.Previous-Page")), (data) -> open(player, finalPage - 1));
        }

        // Next page item.
        if (page < maxPages) {
            int finalPage = page;
            putItem(inventory, getSize() - 10, ItemFactory.rename(ItemFactory.getItemStackFromConfig("Categories.Next-Page-Item"),
                    MessageManager.getMessage("Menu.Misc.Button.Next-Page")), (data) -> open(player, finalPage + 1));
        }

        // Clear cosmetic item.
        String message = MessageManager.getMessage("Clear." + category.getConfigPath());
        ItemStack itemStack = ItemFactory.rename(ItemFactory.getItemStackFromConfig("Categories.Clear-Cosmetic-Item"), message);
        putItem(inventory, inventory.getSize() - 5, itemStack, data -> {
            toggleOff(player, null);
            open(player, getCurrentPage(player));
        });

        // Go Back to Main Menu Arrow.
        if (getCategory().hasGoBackArrow()) {
            ItemStack item = ItemFactory.rename(ItemFactory.getItemStackFromConfig("Categories.Back-Main-Menu-Item"), MessageManager.getMessage("Menu.Main.Title"));
            putItem(inventory, inventory.getSize() - 6, item, (data) -> MenuMain.openMainMenu(player));
        }

        if (hasUnlockable && !SettingsManager.getConfig().getBoolean("No-Permission.Dont-Show-Item")) {
            String filterItemName;
            if (player.isFilteringByOwned()) {
                filterItemName = MessageManager.getMessage("Disable-Filter-By-Owned");
            } else {
                filterItemName = MessageManager.getMessage("Enable-Filter-By-Owned");
            }
            ItemStack filterItem = ItemFactory.create(XMaterial.HOPPER, filterItemName);
            final int finalPage = page;
            putItem(inventory, inventory.getSize() - 3, filterItem, data -> {
                player.setFilteringByOwned(!player.isFilteringByOwned());
                open(player, finalPage); // refresh inventory completely because it changes the layout
            });
        }

        putItems(inventory, player, page);
        ItemFactory.fillInventory(inventory);
        player.getBukkitPlayer().openInventory(inventory);
    }

    private void addPurchaseLore(int price, List<String> lore, T cosmeticType, UltraPlayer player) {
        if (price > 0 && !player.hasPermission(cosmeticType.getPermission()) && SettingsManager.getConfig().getBoolean("No-Permission.Allow-Purchase")) {
            lore.add("");
            lore.add(MessageManager.getMessage("Click-To-Purchase").replace("%price%", String.valueOf(price)));
        }
    }

    public T getCosmeticType(String name) {
        for (T effectType : enabled()) {
            if (effectType.getConfigName().replace(" ", "").equals(name.replace(" ", ""))) {
                return effectType;
            }
        }
        return null;
    }

    /**
     * @param ultraPlayer The menu owner
     * @return The current page of the menu opened by ultraPlayer
     */
    protected int getCurrentPage(UltraPlayer ultraPlayer) {
        Player player = ultraPlayer.getBukkitPlayer();
        String title = player.getOpenInventory().getTitle();
        if (player.getOpenInventory() != null
                && title.startsWith(getName())
                && !title.equals(getName())) {
            String s = player.getOpenInventory().getTitle()
                    .replace(getName() + " " + ChatColor.GRAY + "" + ChatColor.ITALIC + "(", "")
                    .replace("/" + getMaxPages(ultraPlayer) + ")", "");
            return Integer.parseInt(s);
        }
        return 0;
    }

    /**
     * Gets the max amount of pages.
     *
     * @return the maximum amount of pages.
     */
    protected int getMaxPages(UltraPlayer player) {
        int i = 0;
        for (CosmeticType<?> type : enabled()) {
            if (!shouldHideItem(player, type)) {
                i++;
            }
        }
        return Math.max(1, ((i - 1) / 21) + 1);
    }

    protected int getItemsPerPage() {
        return 21;
    }

    /**
     * This method can be overridden
     * to modify an itemstack of a
     * category being placed in the
     * inventory. The given itemstack
     * should be modified directly.
     *
     * @param itemStack    Item Stack being placed.
     * @param cosmeticType The Cosmetic Type.
     * @param player       The Inventory Opener.
     */
    protected void filterItem(ItemStack itemStack, T cosmeticType, UltraPlayer player) {
    }

    protected String getTypeName(T cosmeticType, UltraPlayer ultraPlayer) {
        return cosmeticType.getName();
    }

    /**
     * @param page The page to open.
     * @return The name of the menu with page detailed.
     */
    protected String getName(int page, UltraPlayer ultraPlayer) {
        return MessageManager.getMessage("Menu." + category.getConfigPath() + ".Title") + " " + ChatColor.GRAY + "" + ChatColor.ITALIC + "(" + page + "/" + getMaxPages(ultraPlayer) + ")";
    }

    @Override
    protected int getSize() {
        // I think for consistency it's better to have all menus max size
        return 54;
    }

    @Override
    protected void putItems(Inventory inventory, UltraPlayer ultraPlayer) {
        // --
    }

    /**
     * @return The name of the menu.
     */
    @Override
    protected String getName() {
        return MessageManager.getMessage("Menu." + category.getConfigPath() + ".Title");
    }

    public Category getCategory() {
        return category;
    }

    /**
     * Puts items in the inventory.
     *
     * @param inventory   Inventory.
     * @param ultraPlayer Inventory Owner.
     * @param page        Page to open.
     */
    protected void putItems(Inventory inventory, UltraPlayer ultraPlayer, int page) {
    }

    public abstract List<T> enabled();

    protected Map<Integer,T> getSlots(int page, UltraPlayer player) {
        int start = 21 * (page - 1);
        int limit = 21;
        int current = 0;
        Map<Integer,T> slots = new HashMap<>();
        List<T> enabled = new ArrayList<>(enabled());
        enabled.removeIf(k -> shouldHideItem(player, k));
        for (int i = start; current < limit && i < enabled.size(); i++) {
            slots.put(COSMETICS_SLOTS[current++ % 21], enabled.get(i));
        }
        return slots;
    }

    protected abstract void toggleOn(UltraPlayer ultraPlayer, T type, UltraCosmetics ultraCosmetics);

    protected void toggleOff(UltraPlayer ultraPlayer, T type) {
        ultraPlayer.removeCosmetic(category);
    }

    protected void handleRightClick(UltraPlayer ultraPlayer, T type) {
    }

    protected boolean handleActivate(UltraPlayer ultraPlayer) {
        if (!UltraCosmeticsData.get().shouldCloseAfterSelect()) {
            open(ultraPlayer, getCurrentPage(ultraPlayer));
        }
        return true;
    }

    /**
     * Handles clicking on cosmetics in the GUI
     *
     * @param data         The ClickData from the event
     * @param cosmeticType The cosmetic that was clicked
     * @param price        The price of the clicked cosmetic
     * @return true if closing the inventory now is OK
     */
    protected boolean handleClick(ClickData data, T cosmeticType, int price) {
        UltraPlayer ultraPlayer = data.getClicker();
        ItemStack clicked = data.getClicked();
        int currentPage = getCurrentPage(ultraPlayer);
        if (data.getClick().isRightClick()) {
            if (ultraPlayer.hasPermission(cosmeticType.getPermission())) {
                handleRightClick(ultraPlayer, cosmeticType);
                return false;
            }
        }

        if (startsWithColorless(clicked.getItemMeta().getDisplayName(), category.getDeactivateTooltip())) {
            toggleOff(ultraPlayer, cosmeticType);
            if (!UltraCosmeticsData.get().shouldCloseAfterSelect()) {
                open(ultraPlayer, currentPage);
            }
        } else if (startsWithColorless(clicked.getItemMeta().getDisplayName(), category.getActivateTooltip())) {
            if (ultraPlayer.hasPermission(cosmeticType.getPermission())) {
                toggleOn(ultraPlayer, cosmeticType, getUltraCosmetics());
                if (hasEquipped(ultraPlayer, cosmeticType)) {
                    return handleActivate(ultraPlayer);
                }
                return true;
            }

            if (!SettingsManager.getConfig().getBoolean("No-Permission.Allow-Purchase") || price <= 0) {
                ultraPlayer.sendMessage(MessageManager.getMessage("No-Permission"));
                return true;
            }

            String itemName = MessageManager.getMessage("Buy-Cosmetic-Description");
            itemName = itemName.replace("%price%", String.valueOf(price));
            itemName = itemName.replace("%gadgetname%", cosmeticType.getName());
            ItemStack display = ItemFactory.rename(cosmeticType.getItemStack(), itemName);
            PurchaseData pd = new PurchaseData();
            pd.setPrice(price);
            pd.setShowcaseItem(display);
            pd.setOnPurchase(() -> {
                ultraCosmetics.getPermissionProvider().setPermission(ultraPlayer.getBukkitPlayer(), cosmeticType.getPermission());
                // delay by five ticks so the command processes
                // TODO: how long is actually required?
                Bukkit.getScheduler().runTaskLater(getUltraCosmetics(), () -> {
                    cosmeticType.equip(ultraPlayer, getUltraCosmetics());
                    this.open(ultraPlayer);
                }, 5);
            });
            pd.setOnCancel(() -> this.open(ultraPlayer));
            MenuPurchase mp = new MenuPurchase(getUltraCosmetics(), "Purchase " + cosmeticType.getName(), pd);
            ultraPlayer.getBukkitPlayer().openInventory(mp.getInventory(ultraPlayer));
            return false; // we just opened another inventory, don't close it

        }
        // If we ended up here, that's a bug
        return true;
    }

    protected boolean startsWithColorless(String a, String b) {
        return ChatColor.stripColor(a).startsWith(ChatColor.stripColor(b));
    }

    protected boolean shouldHideItem(UltraPlayer player, CosmeticType<?> cosmeticType) {
        if ((SettingsManager.getConfig().getBoolean("No-Permission.Dont-Show-Item")
                || player.isFilteringByOwned())
                && !player.hasPermission(cosmeticType.getPermission())) {
            return true;
        }
        if (cosmeticType instanceof CosmeticEntType
                && ((CosmeticEntType<?>) cosmeticType).isMonster()
                && player.getBukkitPlayer().getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            return true;
        }
        return false;
    }

    protected boolean hasEquipped(UltraPlayer ultraPlayer, T type) {
        return ultraPlayer.hasCosmetic(type.getCategory()) && ultraPlayer.getCosmetic(type.getCategory()).getType() == type;
    }
}
