package net.mcreator.auroremod.item;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

public class OrblegendaireItem extends Item {
	public OrblegendaireItem(Item.Properties properties) {
		super(properties.rarity(Rarity.RARE).stacksTo(1).fireResistant());
	}

	@Override
	public float getDestroySpeed(ItemStack itemstack, BlockState state) {
		return 0f;
	}
}