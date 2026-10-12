package net.mcreator.auroremod.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

import net.minecraft.world.item.Item;

import net.mcreator.auroremod.item.*;
import net.mcreator.auroremod.AuroreModMod;

import java.util.function.Function;

public class AuroreModModItems {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(AuroreModMod.MODID);
	public static final DeferredItem<Item> ORBTYPIQUE;
	public static final DeferredItem<Item> ORB_ATYPIQUE;
	public static final DeferredItem<Item> ORBRARE;
	public static final DeferredItem<Item> ORBEPIQUE;
	public static final DeferredItem<Item> ORBLEGENDAIRE;
	public static final DeferredItem<Item> ORBMYTHIQUE;
	public static final DeferredItem<Item> ORBEXOTIQUE;
	public static final DeferredItem<Item> POTIONMINEURFOU;
	static {
		ORBTYPIQUE = register("orbtypique", OrbtypiqueItem::new);
		ORB_ATYPIQUE = register("orb_atypique", OrbAtypiqueItem::new);
		ORBRARE = register("orbrare", OrbrareItem::new);
		ORBEPIQUE = register("orbepique", OrbepiqueItem::new);
		ORBLEGENDAIRE = register("orblegendaire", OrblegendaireItem::new);
		ORBMYTHIQUE = register("orbmythique", OrbmythiqueItem::new);
		ORBEXOTIQUE = register("orbexotique", OrbexotiqueItem::new);
		POTIONMINEURFOU = register("potionmineurfou", PotionmineurfouItem::new);
	}

	// Start of user code block custom items
	// End of user code block custom items
	private static <I extends Item> DeferredItem<I> register(String name, Function<Item.Properties, ? extends I> supplier) {
		return REGISTRY.registerItem(name, supplier, Item.Properties::new);
	}
}