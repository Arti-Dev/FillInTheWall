package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.game.Wall;
import com.articreep.fillinthewall.utils.CustomPathfinderGoal;
import com.articreep.fillinthewall.utils.ToggleLookAtPlayerGoal;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.EnderMan;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftEnderman;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.javatuples.Pair;

import java.util.Random;
import java.util.Set;

public class Tutorial extends ModifierEvent implements Listener {
    Enderman enderman = null;
    boolean allowTeleport = false;
    CustomPathfinderGoal pathfinderGoal = null;
    ToggleLookAtPlayerGoal lookAtPlayerGoal = null;
    int ticksBeforeNextSlide = 0;
    int currentSlide = 0;
    boolean error = false;
    int chargesAvailable = 2;

    private final static MiniMessage miniMessage = MiniMessage.miniMessage();

    public Tutorial() {
        super();
        overrideGeneration = true;
        allowMultipleWalls = true;
    }

    @EventHandler
    public void onEndermanTeleport(EntityTeleportEvent event) {
        if (event.getEntity().equals(enderman) && !allowTeleport) {
            event.setCancelled(true);
        }
    }

    @Override
    public void activate() {
        super.activate();
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        // todo untested failsafe
        try {
            spawnEnderman();
        } catch (NoClassDefFoundError e) {
            error = true;
            field.endEvent();
            e.printStackTrace();
        }
    }

    @Override
    public void end() {
        super.end();
        currentSlide = -1;
        if (error) field.sendTitleToPlayers(miniMessage.deserialize("<red>The tutorial failed to load!"), Component.text("Please report this!"), 10, 40, 20);
        else field.sendTitleToPlayers(Component.empty(), Component.text("Good luck!"), 10, 40, 20);
        if (enderman != null) {
            enderman.remove();
            enderman = null;
        }
        HandlerList.unregisterAll(this);
    }

    @Override
    public Component actionBarOverride() {
        if (currentSlide >= 11) return getFormattedFakeMeter();
        return MiniMessage.miniMessage().deserialize("<bold>To leave, walk off the playing field");
    }

    @Override
    public void tick() {
        if (ticksBeforeNextSlide > 0) {
            ticksBeforeNextSlide--;
        } else if (ticksBeforeNextSlide == 0) {
            playSlide(++currentSlide);
            ticksBeforeNextSlide--;
        }
    }

    private void spawnEnderman() {
        Location spawnpoint = field.getReferencePoint()
                .add(field.getFieldDirection().multiply((field.getLength() - 1) / 2.0));
        Player priorityPlayer = field.getPlayers().iterator().next();
        enderman = (Enderman) field.getWorld().spawnEntity(spawnpoint, EntityType.ENDERMAN);
        // todo this doesn't work for players in creative mode, so will have to find a workaround
        enderman.setInvulnerable(true);

        enderman.setRotation((priorityPlayer.getLocation().getYaw() + 180) % 360,
                -priorityPlayer.getLocation().getPitch());

        EnderMan nmsEnderman = ((CraftEnderman) enderman).getHandle();

        Set goals = (Set) Utils.getPrivateField("availableGoals", GoalSelector.class, nmsEnderman.goalSelector);
        Set targets = (Set) Utils.getPrivateField("availableGoals", GoalSelector.class, nmsEnderman.targetSelector);
        goals.clear();
        targets.clear();

        lookAtPlayerGoal = new ToggleLookAtPlayerGoal(nmsEnderman, net.minecraft.world.entity.player.Player.class, 30F, 1);
        nmsEnderman.goalSelector.addGoal(1, lookAtPlayerGoal);
        lookAtPlayerGoal.setEnabled(true);
    }

    private void setPathfinderGoal(Location location) {
        enderman.setAI(true);
        if (enderman == null || enderman.isDead()) return;
        EnderMan nmsEnderman = ((CraftEnderman) enderman).getHandle();
        if (pathfinderGoal != null) {
            nmsEnderman.goalSelector.removeGoal(pathfinderGoal);
        }
        pathfinderGoal = new CustomPathfinderGoal(nmsEnderman, location, 1);
        nmsEnderman.goalSelector.addGoal(0, pathfinderGoal);
    }

    private void teleportEnderman(Location location) {
        if (enderman == null || enderman.isDead()) return;
        allowTeleport = true;
        enderman.teleport(location);
        allowTeleport = false;
        setPathfinderGoal(location);
        field.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
        // todo particle effect?
    }

    // todo allow players to skip between slides
    private void playSlide(int slideToPlay, boolean tryAgain, String tip) {
        if (slideToPlay == 1) {
            // Welcome message
            Wall wall = new Wall(field.getLength(), field.getHeight());
            wall.insertHole(new Pair<>(0, 0));
            wall.setTimeRemaining(600);
            queue.addWall(wall);

            field.sendTitleToPlayers(Component.text("Welcome!"),
                    miniMessage.deserialize("<green>Your goal is to place blocks on this playing field.."), 10, 40, 10);
            ticksBeforeNextSlide = 20 * 3;
        } else if (slideToPlay == 2) {
            Location location = field.getReferencePoint()
                    .subtract(field.getIncomingDirection().multiply(queue.getFullLength()-5))
                    .add(field.getFieldDirection().multiply(field.getLength()));
            teleportEnderman(location);
            field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize("<green>such that they fill the holes in this wall."), 10, 40, 10);
            ticksBeforeNextSlide = 20 * 3;
        } else if (slideToPlay == 3) {
            field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize("<green>Like this!"), 10, 40, 10);
            Location location = field.getReferencePoint()
                    .add(field.getIncomingDirection().multiply(2));
            lookAtPlayerGoal.setEnabled(false);
            teleportEnderman(location);
            enderman.setCarriedBlock(field.getPlayerMaterial().createBlockData());
            Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> {
                field.coordinatesToBlock(new Pair<>(0, 0)).setType(field.getPlayerMaterial());
                field.getWorld().playSound(field.getReferencePoint(), Sound.BLOCK_GLASS_PLACE, 1, 1);
            }, 20);
            Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> {
                field.getQueue().instantSend();
            }, 40);
            ticksBeforeNextSlide = 20 * 4;
        } else if (slideToPlay == 4) {
            Wall wall = new Wall(field.getLength(), field.getHeight());
            wall.insertHole(new Pair<>(field.getLength()-1, field.getHeight()-1));
            wall.insertHole(new Pair<>(field.getLength()-1, 0));
            wall.insertHole(new Pair<>(0, field.getHeight()-1));
            wall.setTimeRemaining(600);
            queue.addWall(wall);
            lookAtPlayerGoal.setEnabled(true);
            if (tryAgain) {
                tryAgainTitle(tip);
            } else {
                field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize("<green>Try this wall!"), 10, 40, 10);
            }
            // wait for the board to be submitted
        } else if (slideToPlay == 5) {
            field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize("<green>Nice! You get an extra point for PERFECT judgements."), 10, 40, 10);
            field.flashScore(80);
            ticksBeforeNextSlide = 20 * 3;
        } else if (slideToPlay == 6) {
            Wall wall = new Wall(field.getLength(), field.getHeight());
            wall.insertHoles(new Pair<>(1, 1), new Pair<>(2, 1), new Pair<>(3, 1));
            wall.setTimeRemaining(160);
            field.getQueue().addWall(wall);
            if (tryAgain) {
                tryAgainTitle(tip);
            } else {
                field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize("<green>Fill in the next wall! This one’s faster."), 10, 40, 10);
            }
        } else if (slideToPlay == 7) {
            Wall wall = new Wall(field.getLength(), field.getHeight());
            wall.insertHole(new Pair<>(field.getLength()-2, 1));
            wall.setTimeRemaining(300);
            field.getQueue().addWall(wall);

            enderman.setCarriedBlock(Material.WAXED_COPPER_GRATE.createBlockData());
            lookAtPlayerGoal.setEnabled(true);
            Location location = field.getReferencePoint()
                    .add(field.getFieldDirection().multiply(field.getLength()-1))
                    .add(field.getIncomingDirection());
            setPathfinderGoal(location);
            field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize("<yellow>One way to place floating blocks is to use copper support blocks."), 10, 40, 10);
            ticksBeforeNextSlide = 20 * 4;
        } else if (slideToPlay == 8) {
            Block block = field.coordinatesToBlock(new Pair<>(field.getLength()-2, 0));
            field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize("<yellow>It behaves as any other solid block..."), 10, 40, 10);
            block.setType(Material.WAXED_COPPER_GRATE);
            // imitate block place effect
            Random random = new Random();
            field.getWorld().playSound(block.getLocation(), Sound.BLOCK_COPPER_GRATE_PLACE, 1, 1);
            field.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_PLACE, 1, 1);
            enderman.setCarriedBlock(field.getPlayerMaterial().createBlockData());
            ticksBeforeNextSlide = 20 * 3;
        } else if (slideToPlay == 9) {
            Block crackedBlock = field.coordinatesToBlock(new Pair<>(field.getLength()-2, 0));
            Block regularBlock = field.coordinatesToBlock(new Pair<>(field.getLength()-2, 1));
            field.sendTitleToPlayers(Component.empty(),
                    miniMessage.deserialize("<yellow>but it breaks right before the wall is submitted!"), 10, 40, 10);
            regularBlock.setType(field.getPlayerMaterial());
            field.getWorld().playSound(regularBlock.getLocation(), Sound.BLOCK_GLASS_PLACE, 1, 1);
            Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> field.getQueue().instantSend(), 40);
            ticksBeforeNextSlide = 20 * 3;
        } else if (slideToPlay == 10) {
            Wall wall = new Wall(field.getLength(), field.getHeight());
            wall.insertHoles(new Pair<>(0, 0), new Pair<>(1, 1), new Pair<>(2, 2));
            wall.setTimeRemaining(300);
            field.getQueue().addWall(wall);
            if (tryAgain) {
                tryAgainTitle(tip);
            } else {
                field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize("<green>Try it with this wall!"), 10, 40, 10);
            }
        } else if (slideToPlay == 11) {
            field.sendTitleToPlayers(Component.empty(), Component.text("Lastly, let's talk about the Charges on your action bar."), 10, 40, 10);
            ticksBeforeNextSlide = 20 * 3;
        } else if (slideToPlay == 12) {
            field.sendTitleToPlayers(Component.empty(), Component.text("You can use it at any time to activate a special effect, like freezing all walls."), 10, 40, 10);
            ticksBeforeNextSlide = 20 * 3;
        } else if (slideToPlay == 13) {
            // move enderman out of the way
            Location location = field.getReferencePoint()
                    .add(field.getFieldDirection().multiply(field.getLength()))
                    .add(field.getIncomingDirection().multiply(4));
            setPathfinderGoal(location);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (currentSlide == 13 &&
                            field.getQueue().countActiveWalls() + field.getQueue().countHiddenWalls() == 0) {
                        for (int i = 0; i < 3; i++) {
                            Wall wall = new Wall(field.getLength(), field.getHeight());
                            wall.generateHoles(1, 3, true);
                            wall.setTimeRemaining(160);
                            field.getQueue().addWall(wall);
                        }
                    } else if (currentSlide != 13) this.cancel();
                }
            }.runTaskTimer(FillInTheWall.getInstance(), 0, 5);
            field.getQueue().allowMultipleWalls(true);
            field.getQueue().setMaxSpawnCooldown(60);

            field.sendTitleToPlayers(Component.empty(),
                    miniMessage.deserialize("Try using one here by pressing <key:key.drop>!"), 10, 60, 10);
        } else if (slideToPlay == 14) {
            Location spawnpoint = field.getReferencePoint()
                    .add(field.getFieldDirection().multiply((field.getLength() - 1) / 2.0));
            teleportEnderman(spawnpoint);
            field.getQueue().clearAllWalls();
            field.sendTitleToPlayers(miniMessage.deserialize("<gold>That's all!"),
                    Component.text("Step off the playing field to end the tutorial."), 10, 100, 20);
        }
    }

    private void playSlide(int slideToPlay) {
        playSlide(slideToPlay, false, "");
    }

    // This runs BEFORE the wall is submitted.
    @Override
    public void onWallScore(Wall wall) {
        double percent = field.getScorer().calculatePercent(wall, field);
        if (currentSlide == 4 || currentSlide == 6 || currentSlide == 10) {
            if (percent == 1) {
                ticksBeforeNextSlide = 20;
            } else {
                String tip;
                if (!wall.getExtraBlocks(field).isEmpty()) tip = "Extra blocks will count against you!";
                else if (!wall.getMissingBlocks(field).isEmpty()) tip = "You missed some blocks!";
                else tip = "";
                Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(),
                        () -> playSlide(currentSlide, true, tip), 20);
            }
        }
    }

    private void tryAgainTitle(String tip) {
        field.playSoundToPlayers(Sound.ENTITY_ENDERMAN_SCREAM, 1, 1);
        field.sendTitleToPlayers(miniMessage.deserialize("<red>Try again!"),
                miniMessage.deserialize("<red>" + tip), 10, 40, 10);
    }

    public void onChargeActivate(Player player) {
        if (currentSlide == 13 && chargesAvailable > 0) {
            chargesAvailable--;
            wallFreeze = true;
            timeFreeze = true;
            field.sendTitleToPlayers(miniMessage.deserialize("<aqua>FREEZE!"),
                   miniMessage.deserialize("<dark_aqua>Walls are temporarily frozen!"), 0, 40, 10);
            field.playSoundToPlayers(Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5F, 1);
            Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> {
                wallFreeze = false;
                timeFreeze = false;
                field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize("<green>Walls are no longer frozen!"), 0, 20, 10);
                field.playSoundToPlayers(Sound.BLOCK_LAVA_EXTINGUISH, 0.5F, 1);
                ticksBeforeNextSlide = 20 * 5;
            }, 20 * 5);
        } else if (chargesAvailable <= 0) {
            player.sendMessage(miniMessage.deserialize("<red>You're out of charges!"));
        } else {
            player.sendMessage(miniMessage.deserialize("<red>Don't worry about this yet!"));
        }
    }

    private Component getFormattedFakeMeter() {
        if (chargesAvailable <= 0) {
            return miniMessage.deserialize("<red>Out of charges!");
        }
        return miniMessage.deserialize("<aqua>" + "Freeze Charges: " + "✦".repeat(chargesAvailable)  + " <blue><bold>Press <key:key.drop>");
    }

    public Tutorial copy() {
        return new Tutorial();
    }

    @Override
    public void playActivateSound() {

    }

    @Override
    public void playDeactivateSound() {

    }
}
