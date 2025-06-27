package com.articreep.fillinthewall.game;

import com.articreep.fillinthewall.FillInTheWall;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BuildSwapper {

    public static void swapBuild(PlayingField playingField, String name) {
        Clipboard clipboard;
        try {
            clipboard = getClipboard(name);
        } catch (IOException e) {
            FillInTheWall.getInstance().getSLF4JLogger().error("Unable to import schematic");
            return;
        }

        AffineTransform transform = getAffineTransform(playingField);

        Location spawnLoc = playingField.getSpawnLocation();
        PotionEffect blindness = new PotionEffect(PotionEffectType.BLINDNESS, 30, 0);
        for (Player player : playingField.getPlayers()) {
            if (player.getLocation().distance(spawnLoc) <= 5) continue;
            player.teleport(spawnLoc);
            player.addPotionEffect(blindness);
        }

        World world = BukkitAdapter.adapt(playingField.getWorld());
        Location refPoint = playingField.getReferencePoint();
        BlockVector3 loc = BlockVector3.at(refPoint.x(), refPoint.y(), refPoint.z());
        try (EditSession session = WorldEdit.getInstance().newEditSession(world)) {
            ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);
            clipboardHolder.setTransform(transform);
            Operation operation = clipboardHolder.createPaste(session)
                    .to(loc)
                    .build();
            Operations.complete(operation);
        } catch (WorldEditException e) {
            throw new RuntimeException(e);
        }

        // todo change environment
    }

    private static @NotNull AffineTransform getAffineTransform(PlayingField playingField) {
        // all schematics' playing field incoming direction should be SOUTH
        Vector incomingDirection = playingField.getIncomingDirection();

        Vector[] vectors = {BlockFace.SOUTH.getDirection(),
                BlockFace.EAST.getDirection(),
                BlockFace.NORTH.getDirection(),
                BlockFace.WEST.getDirection()};
        int rotation = -1;
        for (int i = 0; i < 4; i++) {
            if (incomingDirection.equals(vectors[i])) {
                rotation = i * 90;
            }
        }

        if (rotation == -1) throw new IllegalStateException("This playing field has a weird rotation and can't be fitted by the schematic!");
        return new AffineTransform().rotateY(rotation);
    }

    private static Clipboard getClipboard(String name) throws IOException {
        File dataFolder = FillInTheWall.getInstance().getDataFolder();
        File build = new File(dataFolder, "schematics/" + name + ".schem");

        ClipboardFormat format = ClipboardFormats.findByFile(build);
        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(build))) {
            clipboard = reader.read();
        }
        return clipboard;
    }

    public static List<String> getAvailableSchematics() {
        ArrayList<String> list = new ArrayList<>();
        File dataFolder = FillInTheWall.getInstance().getDataFolder();
        File schematicFolder = new File(dataFolder, "schematics");
        if (!schematicFolder.exists()) {
            schematicFolder.mkdirs();
        }
        File[] files = schematicFolder.listFiles();
        if (files == null) {
            FillInTheWall.getInstance().getSLF4JLogger().error("Failed to load schematic folder");
            return list;
        }
        for (File file : files) {
            list.add(FilenameUtils.removeExtension(file.getName()));
        }
        return list;
    }


}
