package net.mcreator.auroremod.procedures;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.Event;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementHolder;

import net.mcreator.auroremod.init.AuroreModModItems;

import javax.annotation.Nullable;

@EventBusSubscriber
public class MultiorbprocedureProcedure {
	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		execute(event, event.getEntity());
	}

	public static void execute(Entity entity) {
		execute(null, entity);
	}

	private static void execute(@Nullable Event event, Entity entity) {
		if (entity == null)
			return;
		if (!(entity instanceof ServerPlayer _plr0 && _plr0.level() instanceof ServerLevel _serverLevel0
				&& _plr0.getAdvancements().getOrStartProgress(_serverLevel0.getServer().getAdvancements().get(Identifier.parse("aurore_mod:multiorb"))).isDone())) {
			if (hasEntityInInventory(entity, new ItemStack(AuroreModModItems.ORBTYPIQUE.get()))) {
				if (hasEntityInInventory(entity, new ItemStack(AuroreModModItems.ORB_ATYPIQUE.get()))) {
					if (hasEntityInInventory(entity, new ItemStack(AuroreModModItems.ORBRARE.get()))) {
						if (hasEntityInInventory(entity, new ItemStack(AuroreModModItems.ORBEPIQUE.get()))) {
							if (hasEntityInInventory(entity, new ItemStack(AuroreModModItems.ORBLEGENDAIRE.get()))) {
								if (hasEntityInInventory(entity, new ItemStack(AuroreModModItems.ORBMYTHIQUE.get()))) {
									if (hasEntityInInventory(entity, new ItemStack(AuroreModModItems.ORBEXOTIQUE.get()))) {
										if (entity instanceof ServerPlayer _player && _player.level() instanceof ServerLevel _level) {
											AdvancementHolder _adv = _level.getServer().getAdvancements().get(Identifier.parse("aurore_mod:multiorb"));
											if (_adv != null) {
												AdvancementProgress _ap = _player.getAdvancements().getOrStartProgress(_adv);
												if (!_ap.isDone()) {
													for (String criteria : _ap.getRemainingCriteria())
														_player.getAdvancements().award(_adv, criteria);
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	private static boolean hasEntityInInventory(Entity entity, ItemStack itemstack) {
		if (entity instanceof Player player)
			return player.getInventory().contains(stack -> !stack.isEmpty() && ItemStack.isSameItem(stack, itemstack));
		return false;
	}
}