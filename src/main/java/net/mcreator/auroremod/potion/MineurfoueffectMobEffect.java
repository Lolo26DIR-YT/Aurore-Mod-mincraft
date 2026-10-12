package net.mcreator.auroremod.potion;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;

import net.mcreator.auroremod.procedures.MineurfoueffectQuandLeffetCommenceEstAppliqueProcedure;

public class MineurfoueffectMobEffect extends MobEffect {
	public MineurfoueffectMobEffect() {
		super(MobEffectCategory.BENEFICIAL, -16776961);
	}

	@Override
	public void onEffectStarted(LivingEntity entity, int amplifier) {
		MineurfoueffectQuandLeffetCommenceEstAppliqueProcedure.execute(entity);
	}
}