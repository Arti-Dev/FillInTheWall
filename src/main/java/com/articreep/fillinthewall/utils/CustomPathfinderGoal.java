package com.articreep.fillinthewall.utils;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.Location;

// todo apparently this class is still loaded if a mob holds a reference to it. isn't that bad?
public class CustomPathfinderGoal extends Goal {
    double speed;
    Mob mob;
    Location location;

    public CustomPathfinderGoal(Mob mob, Location location, double speed) {
        this.mob = mob;
        this.location = location;
        this.speed = speed;
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(location.getX(), location.getY(), location.getZ(), speed);
    }

    @Override
    public void tick() {
        mob.getNavigation().moveTo(location.getX(), location.getY(), location.getZ(), speed);
    }
}

