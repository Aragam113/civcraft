package com.civcraft.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class SettlerEntity extends PathfinderMob {
	public SettlerEntity(EntityType<? extends SettlerEntity> type, Level world) {
		super(type, world);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.4)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0f));
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
	}
}
