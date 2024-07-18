package com.articreep.fillinthewall.utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;

public class ToggleLookAtPlayerGoal extends LookAtPlayerGoal {
    boolean enabled = false;

    public ToggleLookAtPlayerGoal(Mob var0, Class<? extends LivingEntity> var1, float var2, float var3) {
        super(var0, var1, var2, var3);
    }

    @Override
    public boolean canUse() {
        if (!enabled) return false;
        return super.canUse();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
