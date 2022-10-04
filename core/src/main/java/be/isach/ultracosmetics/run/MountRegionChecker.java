package be.isach.ultracosmetics.run;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.player.UltraPlayer;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/*
 * Mounts that aren't horses don't trigger PlayerMoveEvent I guess,
 * so we have to check manually for those.
 */

public class MountRegionChecker extends BukkitRunnable {
    private UltraPlayer player;
    private UltraCosmetics uc;

    public MountRegionChecker(UltraPlayer player, UltraCosmetics uc) {
        this.player = player;
        this.uc = uc;
    }

    @Override
    public void run() {
        Player bukkitPlayer = player.getBukkitPlayer();
        // Mount#onClear() will cancel it for us
        if (bukkitPlayer == null) return;
        uc.getWorldGuardManager().doCosmeticCheck(bukkitPlayer, uc);
    }
}
