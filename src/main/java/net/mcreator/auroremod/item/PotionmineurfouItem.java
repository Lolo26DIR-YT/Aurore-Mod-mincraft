package net.mcreator.auroremod.item;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.LivingEntity;

import net.mcreator.auroremod.procedures.PotionmineurfouEvenementAuClicDroitDansLairProcedure;

public class PotionmineurfouItem extends Item {
	public PotionmineurfouItem(Item.Properties properties) {
		super(properties.rarity(Rarity.RARE).stacksTo(1).food((new FoodProperties.Builder()).nutrition(0).saturationModifier(0.5f).alwaysEdible().build(), Consumables.defaultDrink().consumeSeconds(3F).build()));
	}

	@Override
	public ItemStack finishUsingItem(ItemStack itemstack, Level world, LivingEntity entity) {
		ItemStack retval = super.finishUsingItem(itemstack, world, entity);
		PotionmineurfouEvenementAuClicDroitDansLairProcedure.execute(entity);
		return retval;
	}
}