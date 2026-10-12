package net.mcreator.auroremod.advancedtitlescreens.client;

import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.Music;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

/** Advanced Title Screens: swaps in the custom title screen (and pause menu) and controls title/menu music. */
@EventBusSubscriber(Dist.CLIENT)
public class TitleScreenHooks {
	@Nullable
	private static Music customMusic;

	@SubscribeEvent
	public static void onScreenOpening(ScreenEvent.Opening event) {
		// exact class check: never replace another mod's TitleScreen subclass, nor our own screen
		if (event.getNewScreen() != null && event.getNewScreen().getClass() == TitleScreen.class) {
			TitleScreenDef def = TitleScreenDefinitions.active();
			if (def != null)
				event.setNewScreen(new AdvancedTitleScreen(def));
		} else if (event.getNewScreen() instanceof PauseScreen pause && pause.getClass() == PauseScreen.class && pause.showsPauseMenu()) {
			// "Replace" pause menu (F3+Esc pauses without a menu, that one is left alone)
			TitleScreenDef def = TitleScreenDefinitions.active();
			if (def != null && def.pauseMenu.enabled && "REPLACE".equals(def.pauseMenu.mode))
				event.setNewScreen(new AdvancedTitleScreen(def, "PAUSE"));
		}
	}

	@SubscribeEvent
	public static void onSelectMusic(SelectMusicEvent event) {
		TitleScreenDef def = TitleScreenDefinitions.active();
		if (def == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			// Only override the generic menu track; screens with their own music (e.g. the credits) keep it
			if (event.getOriginalMusic() != Musics.MENU)
				return;
			if (!def.musicInSubmenus && !(minecraft.screen instanceof AdvancedTitleScreen))
				return;
			if ("NONE".equals(def.musicMode)) {
				event.setMusic(null);
			} else if ("CUSTOM".equals(def.musicMode) && def.music != null) {
				event.setMusic(customMusic(def));
			}
		} else if (def.stopMusicInWorld && event.getMusic() != null && isTitleMusic(def, event.getPlayingMusic())) {
			// In-game music never replaces a playing track by default; force it to so the title music stops
			Music music = event.getMusic();
			event.setMusic(new Music(music.sound(), music.minDelay(), music.maxDelay(), true));
		}
	}

	private static Music customMusic(TitleScreenDef def) {
		// rebuilt when a procedure switches the track (ATS "set title music")
		if (customMusic == null || !customMusic.sound().value().location().equals(def.music)) {
			Holder<SoundEvent> sound = Holder.direct(SoundEvent.createVariableRangeEvent(def.music));
			customMusic = new Music(sound, Math.max(0, def.musicMinDelay), Math.max(Math.max(0, def.musicMinDelay), def.musicMaxDelay), true);
		}
		return customMusic;
	}

	private static boolean isTitleMusic(TitleScreenDef def, @Nullable SoundInstance playing) {
		if (playing == null)
			return false;
		Identifier location = playing.getIdentifier();
		if ("CUSTOM".equals(def.musicMode) && def.music != null)
			return def.music.equals(location);
		return SoundEvents.MUSIC_MENU.value().location().equals(location);
	}
}