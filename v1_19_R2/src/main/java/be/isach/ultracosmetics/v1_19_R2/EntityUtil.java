package be.isach.ultracosmetics.v1_19_R2;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.toRadians;

import be.isach.ultracosmetics.UltraCosmeticsData;
import be.isach.ultracosmetics.treasurechests.TreasureChestDesign;
import be.isach.ultracosmetics.util.MathUtils;
import be.isach.ultracosmetics.util.Particles;
import be.isach.ultracosmetics.v1_19_R2.pathfinders.CustomPathFinderGoalPanic;
import be.isach.ultracosmetics.version.IEntityUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lidded;
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftBoat;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftCreature;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftEnderDragon;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftWither;
import org.bukkit.craftbukkit.v1_19_R2.inventory.CraftItemStack;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.util.Vector;

import com.mojang.datafixers.util.Pair;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.core.Rotations;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * @authors RadBuilder, iSach
 */
public class EntityUtil implements IEntityUtil {

    private final Random r = new Random();
    private Map<Player,Set<ArmorStand>> fakeArmorStandsMap = new HashMap<>();
    private Map<Player,Set<org.bukkit.entity.Entity>> cooldownJumpMap = new HashMap<>();

    private static Field memoriesField;
    private static Field sensorsField;
    private static Field lockedFlagsField;
    private static Field disabledFlagsField;
    static {
        try {
            memoriesField = Brain.class.getDeclaredField(ObfuscatedFields.MEMORIES);
            memoriesField.setAccessible(true);

            sensorsField = Brain.class.getDeclaredField(ObfuscatedFields.SENSORS);
            sensorsField.setAccessible(true);

            lockedFlagsField = GoalSelector.class.getDeclaredField(ObfuscatedFields.LOCKED_FLAGS);
            lockedFlagsField.setAccessible(true);

            disabledFlagsField = GoalSelector.class.getDeclaredField(ObfuscatedFields.DISABLED_FLAGS);
            disabledFlagsField.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void resetWitherSize(Wither wither) {
        ((CraftWither) wither).getHandle().setInvulnerableTicks(600);
    }

    @Override
    public void sendBlizzard(final Player player, Location loc,
            Function<org.bukkit.entity.Entity,Boolean> canAffectFunc, Vector v) {
        final Set<ArmorStand> fakeArmorStands = fakeArmorStandsMap.computeIfAbsent(player, k -> new HashSet<>());
        final Set<org.bukkit.entity.Entity> cooldownJump = cooldownJumpMap.computeIfAbsent(player, k -> new HashSet<>());

        final ArmorStand as = new ArmorStand(EntityType.ARMOR_STAND, ((CraftWorld) player.getWorld()).getHandle());
        as.setInvisible(true);
        as.setSharedFlag(5, true);
        as.setSmall(true);
        as.setNoGravity(true);
        as.setShowArms(true);
        as.setHeadPose(new Rotations(r.nextInt(360), r.nextInt(360), r.nextInt(360)));
        as.absMoveTo(loc.getX() + MathUtils.randomDouble(-1.5, 1.5), loc.getY() + MathUtils.randomDouble(0, 0.5) - 0.75,
                loc.getZ() + MathUtils.randomDouble(-1.5, 1.5), 0, 0);
        fakeArmorStands.add(as);
        ClientboundAddEntityPacket addPacket = new ClientboundAddEntityPacket(as);
        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(as.getId(), as.getEntityData().packDirty());
        List<Pair<EquipmentSlot,ItemStack>> equipment = new ArrayList<>();
        equipment.add(new Pair<>(EquipmentSlot.HEAD, CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(org.bukkit.Material.PACKED_ICE))));
        ClientboundSetEquipmentPacket equipmentPacket = new ClientboundSetEquipmentPacket(as.getId(), equipment);
        for (Player loopPlayer : player.getWorld().getPlayers()) {
            sendPacket(loopPlayer, addPacket);
            sendPacket(loopPlayer, dataPacket);
            sendPacket(loopPlayer, equipmentPacket);
        }
        Particles.CLOUD.display(loc.clone().add(MathUtils.randomDouble(-1.5, 1.5), MathUtils.randomDouble(0, 0.5) - 0.75,
                MathUtils.randomDouble(-1.5, 1.5)), 2, 0.4f);
        Bukkit.getScheduler().runTaskLater(UltraCosmeticsData.get().getPlugin(), () -> {
            for (Player pl : player.getWorld().getPlayers()) {
                sendPacket(pl, new ClientboundRemoveEntitiesPacket(as.getId()));
            }
            fakeArmorStands.remove(as);
        }, 20);
        as.getBukkitEntity().getNearbyEntities(0.5, 0.5, 0.5).stream()
                .filter(ent -> !cooldownJump.contains(ent) && ent != player && canAffectFunc.apply(ent))
                .forEachOrdered(ent -> {
                    MathUtils.applyVelocity(ent, new Vector(0, 1, 0).add(v));
                    cooldownJump.add(ent);
                    Bukkit.getScheduler().runTaskLater(UltraCosmeticsData.get().getPlugin(),
                            () -> cooldownJump.remove(ent), 20);
                });
    }

    @Override
    public void clearBlizzard(Player player) {
        if (!fakeArmorStandsMap.containsKey(player)) return;

        for (ArmorStand as : fakeArmorStandsMap.get(player)) {
            if (as == null) {
                continue;
            }
            for (Player pl : player.getWorld().getPlayers()) {
                sendPacket(pl, new ClientboundRemoveEntitiesPacket(as.getId()));
            }
        }

        fakeArmorStandsMap.remove(player);
        cooldownJumpMap.remove(player);
    }

    @Override
    public void clearPathfinders(org.bukkit.entity.Entity entity) {
        Mob nmsEntity = (Mob) ((CraftEntity) entity).getHandle();
        GoalSelector goalSelector = nmsEntity.goalSelector;
        GoalSelector targetSelector = nmsEntity.targetSelector;

        Brain<?> brain = ((LivingEntity) nmsEntity).getBrain();

        // deprecated and annotated VisibleForTesting but super convenient
        @SuppressWarnings("deprecation")
        Set<MemoryModuleType<?>> memoryTypes = brain.getMemories().keySet();
        for (MemoryModuleType<?> type : memoryTypes) {
            brain.eraseMemory(type);
        }

        try {
            sensorsField.set(brain, new LinkedHashMap<>());

            // this method is annotated with VisibleForTesting but it seems like the easiest
            // thing to do at the moment
            // this clears net.minecraft.world.entity.ai.Brain#availableBehaviorsByPriority
            brain.removeAllBehaviors();
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }

        try {
            // this is also annotated VisibleForTesting
            // this clears net.minecraft.world.entity.ai.goal.GoalSelector#availableGoals
            goalSelector.removeAllGoals(g -> true);
            targetSelector.removeAllGoals(g -> true);

            lockedFlagsField.set(targetSelector, new EnumMap<Goal.Flag,WrappedGoal>(Goal.Flag.class));

            disabledFlagsField.set(targetSelector, EnumSet.noneOf(Goal.Flag.class));
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void makePanic(org.bukkit.entity.Entity entity) {
        PathfinderMob insentient = (PathfinderMob) ((CraftEntity) entity).getHandle();
        insentient.goalSelector.addGoal(3, new CustomPathFinderGoalPanic(insentient));
    }

    @Override
    public void sendDestroyPacket(Player player, org.bukkit.entity.Entity entity) {
        sendPacket(player, new ClientboundRemoveEntitiesPacket(((CraftEntity) entity).getHandle().getId()));
    }

    @Override
    public void move(Creature creature, Location loc) {
        PathfinderMob ec = ((CraftCreature) creature).getHandle();
        setStepHeight(creature);

        if (loc == null) return;

        ec.yHeadRot = loc.getYaw();
        Path path = ec.getNavigation().createPath(loc.getX(), loc.getY(), loc.getZ(), 1);
        ec.getNavigation().moveTo(path, 2);
    }

    @Override
    public void moveDragon(Player player, Vector vector, org.bukkit.entity.Entity entity) {
        EnderDragon ec = ((CraftEnderDragon) entity).getHandle();

        float yaw = player.getLocation().getYaw();

        ec.hurtTime = -1;
        ec.setXRot(player.getLocation().getPitch());
        ec.setYRot(yaw - 180);

        double angleInRadians = toRadians(-yaw);

        double x = sin(angleInRadians);
        double z = cos(angleInRadians);

        Vector v = ec.getBukkitEntity().getLocation().getDirection();

        ec.move(MoverType.SELF, new Vec3(x, v.getY(), z));
    }

    @Override
    public void setStepHeight(org.bukkit.entity.Entity entity) {
        ((CraftEntity) entity).getHandle().maxUpStep = 1;
    }

    @Override
    public void moveShip(Player player, org.bukkit.entity.Entity entity, Vector vector) {
        Boat ec = ((CraftBoat) entity).getHandle();

        ec.getBukkitEntity().setVelocity(vector);

        ec.setXRot(player.getLocation().getPitch());
        ec.setYRot(player.getLocation().getYaw() - 180);

        ec.move(MoverType.SELF, new Vec3(1, 0, 0));
    }

    @Override
    public void playChestAnimation(Block b, boolean open, TreasureChestDesign design) {
        BlockState state = b.getState();
        ((Lidded) state).open();
        state.update();
    }

    @Override
    public void follow(org.bukkit.entity.Entity toFollow, org.bukkit.entity.Entity follower) {
        Entity pett = ((CraftEntity) follower).getHandle();
        ((Mob) pett).getNavigation().setMaxVisitedNodesMultiplier(2);
        Object petf = ((CraftEntity) follower).getHandle();
        Location targetLocation = toFollow.getLocation();
        Path path;
        path = ((Mob) petf).getNavigation().createPath(targetLocation.getX() + 1, targetLocation.getY(),
                targetLocation.getZ() + 1, 1);
        if (path != null) {
            ((Mob) petf).getNavigation().moveTo(path, 1.05D);
            ((Mob) petf).getNavigation().setSpeedModifier(1.05D);
        }
    }

    @Override
    public void sendTeleportPacket(Player player, org.bukkit.entity.Entity entity) {
        sendPacket(player, new ClientboundTeleportEntityPacket(((CraftEntity) entity).getHandle()));
    }

    private void sendPacket(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().connection.send(packet);
    }
}
