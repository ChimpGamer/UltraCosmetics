package be.isach.ultracosmetics.cosmetics.morphs;

import java.util.ArrayList;

import be.isach.ultracosmetics.UltraCosmeticsData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.cosmetics.type.MorphType;
import be.isach.ultracosmetics.player.UltraPlayer;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * Represents an instance of an enderman morph summoned by a player.
 *
 * @author iSach
 * @since 08-26-2015
 */
public class MorphEnderman extends Morph {
    private boolean cooldown;

    public MorphEnderman(UltraPlayer owner, MorphType type, UltraCosmetics ultraCosmetics) {
        super(owner, type, ultraCosmetics);
    }

    @Override
    protected void onEquip() {
        super.onEquip();
        getPlayer().setAllowFlight(true);
    }

    @EventHandler
    public void onPlayerToggleFligh(PlayerToggleFlightEvent event) {
        if (event.getPlayer() == getPlayer()
                && event.getPlayer().getGameMode() != GameMode.CREATIVE
                && !event.getPlayer().isFlying()) {
            if (cooldown) {
                event.getPlayer().setFlying(false);
                event.setCancelled(true);
                return;
            }
            cooldown = true;
            Bukkit.getScheduler().runTaskLaterAsynchronously(getUltraCosmetics(), () -> cooldown = false, 70);
            Block b = event.getPlayer().getTargetBlock(null, 17);
            Location loc = b.getLocation();
            loc.setPitch(event.getPlayer().getLocation().getPitch());
            loc.setYaw(event.getPlayer().getLocation().getYaw());
            event.getPlayer().teleport(loc);
            spawnRandomFirework(b.getLocation().add(0.5, 0, 0.5));
            event.getPlayer().setFlying(false);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() == getPlayer()
                && getOwner().getCurrentMorph() == this) {
            event.setCancelled(true);
        }
    }

    public static FireworkEffect getRandomFireworkEffect() {
        FireworkEffect.Builder builder = FireworkEffect.builder();
        return builder.flicker(false).trail(false).with(FireworkEffect.Type.BALL_LARGE).withColor(Color.fromRGB(0, 0, 0)).withFade(Color.fromRGB(0, 0, 0)).build();
    }

    public void spawnRandomFirework(Location location) {
        final ArrayList<Firework> fireworks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final Firework f = getPlayer().getWorld().spawn(location, Firework.class);

            FireworkMeta fm = f.getFireworkMeta();
            fm.addEffect(getRandomFireworkEffect());
            f.setFireworkMeta(fm);
            fireworks.add(f);
            f.setMetadata("uc_firework", new FixedMetadataValue(getUltraCosmetics(), 1));
        }
        Bukkit.getScheduler().runTaskLater(getUltraCosmetics(), () -> {
            for (Firework f : fireworks)
                f.detonate();
        }, 2);
    }

    @Override
    public void onClear() {
        if (getPlayer().getGameMode() != GameMode.CREATIVE) {
            getPlayer().setAllowFlight(false);
        }
    }
}
