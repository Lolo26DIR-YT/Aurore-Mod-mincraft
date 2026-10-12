/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.auroremod.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.registries.Registries;

import net.mcreator.auroremod.AuroreModMod;

public class AuroreModModPotions {
	public static final DeferredRegister<Potion> REGISTRY = DeferredRegister.create(Registries.POTION, AuroreModMod.MODID);
	public static final DeferredHolder<Potion, Potion> MINEURFOUPOTION = REGISTRY.register("mineurfoupotion", () -> new Potion("mineurfoupotion", new MobEffectInstance(AuroreModModMobEffects.MINEURFOUEFFECT, 1200, 0, false, true)));
}