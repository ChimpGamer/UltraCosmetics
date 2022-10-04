package be.isach.ultracosmetics.cosmetics;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.type.CosmeticType;
import be.isach.ultracosmetics.player.UltraPlayer;
import be.isach.ultracosmetics.util.TextUtil;
import be.isach.ultracosmetics.worldguard.CosmeticRegionState;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;
import java.util.UUID;

/**
 * A cosmetic instance summoned by a player.
 *
 * @author iSach
 * @since 07-21-2016
 */
public abstract class Cosmetic<T extends CosmeticType<?>> extends BukkitRunnable implements Listener {
    protected static final Random RANDOM = new Random();
    private final UltraPlayer owner;
    private final Category category;
    private final UltraCosmetics ultraCosmetics;
    protected boolean equipped;
    protected final T cosmeticType;
    private final UUID ownerUniqueId;

    public Cosmetic(UltraPlayer owner, T type, UltraCosmetics ultraCosmetics) {
        if (owner == null || Bukkit.getPlayer(owner.getUUID()) == null) {
            throw new IllegalArgumentException("Invalid UltraPlayer.");
        }
        this.owner = owner;
        this.ownerUniqueId = owner.getUUID();
        this.category = type.getCategory();
        this.ultraCosmetics = ultraCosmetics;
        this.cosmeticType = type;
    }

    public final void equip() {
        if (!owner.getBukkitPlayer().hasPermission(getType().getPermission())) {
            getPlayer().sendMessage(MessageManager.getMessage("No-Permission"));
            return;
        }

        if (SettingsManager.getConfig().getBoolean("Prevent-Cosmetics-In-Vanish")) {
            owner.clear();
            getPlayer().sendMessage(MessageManager.getMessage("Not-Allowed-In-Vanish"));
            return;
        }
        CosmeticRegionState state = ultraCosmetics.getWorldGuardManager().allowedCosmeticsState(getPlayer(), category);
        if (state == CosmeticRegionState.BLOCKED_ALL) {
            getPlayer().sendMessage(MessageManager.getMessage("Region-Disabled"));
            return;
        } else if (state == CosmeticRegionState.BLOCKED_CATEGORY) {
            getPlayer().sendMessage(MessageManager.getMessage("Region-Disabled-Category")
                    .replace("%category%", ChatColor.stripColor(MessageManager.getMessage("Menu." + category.getMessagesName() + ".Title"))));
            return;
        }

        if (!tryEquip()) {
            return;
        }

        ultraCosmetics.getServer().getPluginManager().registerEvents(this, ultraCosmetics);

        unequipLikeCosmetics();

        this.equipped = true;

        getPlayer().sendMessage(filterPlaceholders(getCategory().getActivateMessage()));

        if (this instanceof Updatable) {
            scheduleTask();
        }

        onEquip();

        getOwner().setCosmeticEquipped(this);
    }

    public /* final */ void clear() {
        getPlayer().sendMessage(filterPlaceholders(getCategory().getDeactivateMessage()));

        HandlerList.unregisterAll(this);

        try {
            cancel();
        } catch (IllegalStateException ignored) {
        } // not scheduled yet

        // Call untask finally. (in main thread)
        onClear();
        unsetCosmetic();
    }

    protected void scheduleTask() {
        runTaskTimer(getUltraCosmetics(), 0, 1);
    }

    protected void unsetCosmetic() {
        owner.unsetCosmetic(category);
    }

    protected void unequipLikeCosmetics() {
        getOwner().removeCosmetic(category);
    }

    protected boolean tryEquip() {
        return true;
    }

    @Override
    public void run() {
        if (getPlayer() == null || getOwner().getCosmetic(category) != this) {
            return;
        }
        ((Updatable) this).onUpdate();
    }

    protected abstract void onEquip();

    protected void onClear() {
    }

    public final UltraPlayer getOwner() {
        return owner;
    }

    public final UltraCosmetics getUltraCosmetics() {
        return ultraCosmetics;
    }

    public final Category getCategory() {
        return category;
    }

    public final Player getPlayer() {
        return owner.getBukkitPlayer();
    }

    public boolean isEquipped() {
        return equipped;
    }

    public final UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    public T getType() {
        return cosmeticType;
    }

    protected String getTypeName() {
        return getType().getName();
    }

    protected String filterPlaceholders(String message) {
        return message.replace(getCategory().getChatPlaceholder(), TextUtil.filterPlaceHolder(getTypeName()));
    }
}
