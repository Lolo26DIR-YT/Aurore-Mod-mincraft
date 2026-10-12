/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.auroremod.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import net.mcreator.auroremod.AuroreModMod;

public class AuroreModModTabs {
	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AuroreModMod.MODID);
	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SMP = REGISTRY.register("smp",
			() -> CreativeModeTab.builder().title(Component.translatable("item_group.aurore_mod.smp")).icon(() -> new ItemStack(Items.KNOWLEDGE_BOOK)).displayItems((parameters, tabData) -> {
				tabData.accept(AuroreModModItems.ORBTYPIQUE.get());
				tabData.accept(AuroreModModItems.ORB_ATYPIQUE.get());
				tabData.accept(AuroreModModItems.ORBRARE.get());
				tabData.accept(AuroreModModItems.ORBEPIQUE.get());
				tabData.accept(AuroreModModItems.ORBLEGENDAIRE.get());
				tabData.accept(AuroreModModItems.ORBMYTHIQUE.get());
				tabData.accept(AuroreModModItems.ORBEXOTIQUE.get());
				tabData.accept(AuroreModModItems.POTIONMINEURFOU.get());
			}).withSearchBar().build());
}