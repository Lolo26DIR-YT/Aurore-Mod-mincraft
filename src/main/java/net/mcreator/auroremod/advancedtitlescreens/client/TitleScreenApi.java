package net.mcreator.auroremod.advancedtitlescreens.client;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import net.neoforged.fml.loading.FMLPaths;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/**
 * Advanced Title Screens: what the ATS procedure blocks call. Elements are found by the ID set in the editor
 * (plus the reserved IDs "logo", "background" and "panorama"). Every change is saved to
 * config/advancedtitlescreens_aurore_mod.txt and re-applied on the next launch, so the main menu can change for
 * good after something happens in game. Client side only - the generated block code checks the dist first.
 */
public final class TitleScreenApi {
	private static final Logger LOGGER = LoggerFactory.getLogger("AdvancedTitleScreens");
	private static final Map<String, String> VARIABLES = new ConcurrentHashMap<>();
	/** Saved operations, one per line: kind|id|argument|value (the latest per key wins). */
	private static final Map<String, String> OPERATIONS = new java.util.LinkedHashMap<>();
	/** Bumped on every change so an open title screen knows to rebuild its buttons. */
	public static volatile int version;
	private static boolean loaded;
	private static boolean replaying;

	private TitleScreenApi() {
	}

	// ------------------------------------------------------------------ blocks
	public static synchronized void setText(String id, String text) {
		String value = text == null ? "" : text;
		boolean found = false;
		for (TitleScreenDef def : TitleScreenDefinitions.ALL) {
			if (is(id, "logo")) {
				def.logoText = value;
				found = true;
			}
			for (TitleScreenDef.TextDef t : def.texts)
				if (is(id, t.id)) {
					t.text = value;
					found = true;
				}
			for (TitleScreenDef.ButtonDef b : def.buttons)
				if (is(id, b.id)) {
					b.label = value;
					found = true;
				}
		}
		changed("text", id, "", value, found);
	}

	public static synchronized void setNumber(String id, String property, double value) {
		boolean found = false;
		for (TitleScreenDef def : TitleScreenDefinitions.ALL) {
			if (is(id, "background") || is(id, "panorama")) {
				found |= backgroundNumber(def, property, value);
				continue;
			}
			if (is(id, "logo")) {
				found |= logoNumber(def, property, value);
				continue;
			}
			for (TitleScreenDef.ButtonDef b : def.buttons)
				if (is(id, b.id))
					found |= buttonNumber(b, property, value);
			for (TitleScreenDef.TextDef t : def.texts)
				if (is(id, t.id))
					found |= textNumber(t, property, value);
			for (TitleScreenDef.ImageDef i : def.images)
				if (is(id, i.id))
					found |= imageNumber(i, property, value);
			for (TitleScreenDef.WidgetDef w : def.widgets)
				if (is(id, w.id))
					found |= widgetNumber(w, property, value);
		}
		changed("number", id, property, Double.toString(value), found);
	}

	/** Colors as text: "#FF5555", "FF5555" or "0xFF5555". */
	public static synchronized void setColor(String id, String property, String color) {
		if (color == null)
			return;
		String c = color.trim().replace("#", "").replaceFirst("^0[xX]", "");
		try {
			setNumber(id, property, Integer.parseInt(c, 16) & 0xFFFFFF);
		} catch (NumberFormatException e) {
			LOGGER.warn("ATS procedure: '{}' is not a color (use #RRGGBB)", color);
		}
	}

	public static synchronized void setTexture(String id, String texture) {
		Identifier rl = texture(texture);
		boolean found = false;
		for (TitleScreenDef def : TitleScreenDefinitions.ALL) {
			if (is(id, "logo")) {
				def.logoImage = rl;
				if (rl != null)
					def.logoType = "IMAGE";
				found = true;
			} else if (is(id, "background")) {
				def.backgroundImage = rl;
				found = true;
			} else if (is(id, "panorama")) {
				java.util.Arrays.fill(def.panoramaFaces, rl);
				found = true;
			}
			for (TitleScreenDef.ImageDef i : def.images)
				if (is(id, i.id)) {
					i.texture = rl;
					found = true;
				}
			for (TitleScreenDef.WidgetDef w : def.widgets)
				if (is(id, w.id)) {
					w.texture = rl;
					found = true;
				}
			for (TitleScreenDef.ButtonDef b : def.buttons)
				if (is(id, b.id)) {
					b.texture = rl;
					if (rl != null)
						b.style = "TEXTURE";
					found = true;
				}
		}
		changed("texture", id, "", texture == null ? "" : texture, found);
	}

	/** One Effect element's particle type. */
	public static synchronized void setEffectType(String id, String type) {
		boolean found = false;
		for (TitleScreenDef def : TitleScreenDefinitions.ALL)
			for (TitleScreenDef.WidgetDef w : def.widgets)
				if (is(id, w.id) && "EFFECT".equals(w.kind)) {
					w.effectType = type;
					found = true;
				}
		changed("effect", id, "", type, found);
	}

	public static synchronized void setWidgetType(String id, String kind) {
		boolean found = false;
		for (TitleScreenDef def : TitleScreenDefinitions.ALL)
			for (TitleScreenDef.WidgetDef w : def.widgets)
				if (is(id, w.id)) {
					w.kind = kind;
					found = true;
				}
		changed("type", id, "", kind, found);
	}

	public static synchronized void setVisible(String id, boolean visible) {
		boolean found = false;
		for (TitleScreenDef def : TitleScreenDefinitions.ALL) {
			for (TitleScreenDef.ButtonDef b : def.buttons)
				if (is(id, b.id)) {
					b.hidden = !visible;
					found = true;
				}
			for (TitleScreenDef.TextDef t : def.texts)
				if (is(id, t.id)) {
					t.hidden = !visible;
					found = true;
				}
			for (TitleScreenDef.ImageDef i : def.images)
				if (is(id, i.id)) {
					i.hidden = !visible;
					found = true;
				}
			for (TitleScreenDef.WidgetDef w : def.widgets)
				if (is(id, w.id)) {
					w.hidden = !visible;
					found = true;
				}
		}
		changed("visible", id, "", Boolean.toString(visible), found);
	}

	// ------------------------------------------------------------------ more blocks (pages, values, audio)
	/** Splash text forced by a procedure (null = the title screen picks one as usual). */
	@Nullable
	public static volatile String splashOverride;

	/** The open menu with custom elements (title screen, custom pause menu or a decorated vanilla menu). */
	@Nullable
	private static TitleMenus.Host openScreen() {
		return TitleMenus.host();
	}

	/** True while the custom title screen is open. */
	public static boolean isOpen() {
		TitleMenus.Host s = openScreen();
		return s != null && "TITLE".equals(s.menu());
	}

	public static String currentPage() {
		TitleMenus.Host s = openScreen();
		return s == null ? "" : s.getCurrentPage();
	}

	public static void openPage(String page) {
		TitleMenus.Host s = openScreen();
		if (s != null && page != null)
			s.openPage(page.trim());
	}

	public static void goBack() {
		TitleMenus.Host s = openScreen();
		if (s != null)
			s.back();
	}

	/**
	 * Opens TITLE, PAUSE, OPTIONS or MODS. Screens are opened through setScreen so the custom title screen / pause
	 * menu and the decorated menus apply. Runs on the render thread (procedures may run on the integrated server).
	 */
	public static void openMenu(String menu) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			net.minecraft.client.gui.screens.Screen parent = minecraft.screen;
			switch (menu == null ? "" : menu) {
				case "TITLE" -> {
					if (minecraft.level == null)
						minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
					else
						LOGGER.warn("ATS open menu: the title screen can't be opened while in a world");
				}
				case "PAUSE" -> {
					if (minecraft.level != null)
						minecraft.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
					else
						LOGGER.warn("ATS open menu: the pause menu only exists in a world");
				}
				case "OPTIONS" -> minecraft.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(parent, minecraft.options, minecraft.level != null));
				case "MODS" -> minecraft.setScreen(new net.neoforged.neoforge.client.gui.ModListScreen(parent));
				default -> LOGGER.warn("ATS open menu: unknown menu {}", menu);
			}
		});
	}

	/** Runs a button's procedure and action as if it had been clicked. */
	public static void clickButton(String id) {
		TitleMenus.Host s = openScreen();
		if (s != null && id != null)
			s.clickButton(id.trim());
	}

	public static String getVariableOrEmpty(String name) {
		String v = name == null ? null : getVariable(name.trim());
		return v == null ? "" : v;
	}

	/** Adds {@code amount} to a variable, treating it as a number (blank = 0). */
	public static synchronized void changeVariable(String name, double amount) {
		double current;
		try {
			current = Double.parseDouble(getVariableOrEmpty(name).trim());
		} catch (NumberFormatException e) {
			current = 0;
		}
		double result = current + amount;
		setVariable(name, result == Math.rint(result) && Math.abs(result) < 1e15 ? Long.toString((long) result) : Double.toString(result));
	}

	/** Reads a number property (same names as set number); 0 if the element or property doesn't exist. */
	public static synchronized double getNumber(String id, String property) {
		for (TitleScreenDef def : TitleScreenDefinitions.ALL) {
			if (is(id, "background") || is(id, "panorama")) {
				return switch (property) {
					case "SPIN" -> def.panoramaSpeed;
					case "PITCH" -> def.panoramaPitch;
					case "DISTANCE" -> def.panoramaDistance;
					case "FOV" -> def.panoramaFov;
					case "OPACITY" -> def.overlayOpacity;
					default -> 0;
				};
			}
			if (is(id, "logo"))
				return switch (property) {
					case "X" -> def.logoX;
					case "Y" -> def.logoY;
					case "WIDTH" -> def.logoWidth;
					case "HEIGHT" -> def.logoHeight;
					case "SCALE" -> def.logoTextScale;
					default -> 0;
				};
			for (TitleScreenDef.ButtonDef b : def.buttons)
				if (is(id, b.id))
					return switch (property) {
						case "X" -> b.x;
						case "Y" -> b.y;
						case "WIDTH" -> b.width;
						case "HEIGHT" -> b.height;
						case "SCALE" -> b.textScale;
						case "OPACITY" -> b.backgroundOpacity;
						default -> 0;
					};
			for (TitleScreenDef.TextDef t : def.texts)
				if (is(id, t.id))
					return switch (property) {
						case "X" -> t.x;
						case "Y" -> t.y;
						case "SCALE" -> t.scale;
						case "OPACITY" -> t.opacity;
						case "ROTATION" -> t.rotation;
						default -> 0;
					};
			for (TitleScreenDef.ImageDef i : def.images)
				if (is(id, i.id))
					return switch (property) {
						case "X" -> i.x;
						case "Y" -> i.y;
						case "WIDTH" -> i.width;
						case "HEIGHT" -> i.height;
						case "OPACITY" -> i.opacity;
						case "ROTATION" -> i.rotation;
						default -> 0;
					};
			for (TitleScreenDef.WidgetDef w : def.widgets)
				if (is(id, w.id))
					return switch (property) {
						case "X" -> w.x;
						case "Y" -> w.y;
						case "WIDTH" -> w.width;
						case "HEIGHT" -> w.height;
						case "OPACITY" -> w.opacity;
						case "ROTATION" -> w.rotation;
						case "SPIN_X" -> w.spinX;
						case "SPIN_Y", "SPIN" -> w.spinY;
						case "SPIN_Z" -> w.spinZ;
						case "TILT_X" -> w.tiltX;
						case "TILT_Y" -> w.tiltY;
						case "GLOW_OPACITY" -> w.glowOpacity;
						case "GLOW_SIZE" -> w.glowSize;
						case "EFFECT_COUNT" -> w.effectCount;
						case "EFFECT_SPEED" -> w.effectSpeed;
						case "EFFECT_SIZE" -> w.effectSize;
						default -> 0;
					};
		}
		return 0;
	}

	public static synchronized void setBackgroundType(String type) {
		for (TitleScreenDef def : TitleScreenDefinitions.ALL)
			def.backgroundType = type;
		changed("bgtype", "", "", type, true);
	}

	/** Every Effect element's type (older elements without effects: the global particles). */
	public static synchronized void setParticles(String type) {
		for (TitleScreenDef def : TitleScreenDefinitions.ALL) {
			boolean hasEffects = false;
			for (TitleScreenDef.WidgetDef w : def.widgets)
				if ("EFFECT".equals(w.kind)) {
					hasEffects = true;
					if ("NONE".equals(type))
						w.hidden = true;
					else {
						w.effectType = type;
						w.hidden = false;
					}
				}
			if (!hasEffects)
				def.particleType = type;
		}
		changed("particles", "", "", type, true);
	}

	public static synchronized void setSplash(String text) {
		splashOverride = text == null || text.isEmpty() ? null : text;
		changed("splash", "", "", text == null ? "" : text, true);
	}

	/** Switches the title music (sound id, e.g. "mymod:menu_theme"); the new track starts right away. */
	public static synchronized void setMusic(String sound) {
		Identifier rl = TitleScreenDef.rl(sound);
		for (TitleScreenDef def : TitleScreenDefinitions.ALL) {
			def.music = rl;
			def.musicMode = rl == null ? "VANILLA" : "CUSTOM";
		}
		changed("music", "", "", sound == null ? "" : sound, true);
		if (!replaying && Minecraft.getInstance().player == null)
			Minecraft.getInstance().getMusicManager().stopPlaying();
	}

	/** Plays a sound for the player right now (works without a world, e.g. on the title screen). */
	public static void playSound(String sound, float volume, float pitch) {
		Identifier rl = TitleScreenDef.rl(sound);
		if (rl != null)
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(rl), pitch, volume));
	}

	/** Shown by the %var:name% placeholder. */
	public static synchronized void setVariable(String name, String value) {
		VARIABLES.put(name.toLowerCase(Locale.ROOT), value == null ? "" : value);
		changed("var", name, "", value == null ? "" : value, true);
	}

	@Nullable
	public static String getVariable(String name) {
		return VARIABLES.get(name.toLowerCase(Locale.ROOT));
	}

	/** Forgets every procedure change and restores the title screens exactly as designed. */
	public static synchronized void reset() {
		splashOverride = null;
		OPERATIONS.clear();
		VARIABLES.clear();
		save();
		TitleScreenDefinitions.reload();
		version++;
	}

	// ------------------------------------------------------------------ property setters
	private static boolean backgroundNumber(TitleScreenDef d, String p, double v) {
		switch (p) {
			case "SPIN" -> d.panoramaSpeed = v;
			case "PITCH" -> d.panoramaPitch = v;
			case "DISTANCE" -> d.panoramaDistance = v;
			case "FOV" -> d.panoramaFov = v;
			case "COLOR" -> d.backgroundColor = (int) v;
			case "OPACITY" -> d.overlayOpacity = (int) v;
			case "TINT" -> d.overlayColor = (int) v;
			default -> {
				return false;
			}
		}
		return true;
	}

	private static boolean logoNumber(TitleScreenDef d, String p, double v) {
		switch (p) {
			case "X" -> d.logoX = (int) v;
			case "Y" -> d.logoY = (int) v;
			case "WIDTH" -> d.logoWidth = (int) v;
			case "HEIGHT" -> d.logoHeight = (int) v;
			case "SCALE" -> d.logoTextScale = v;
			case "COLOR" -> d.logoTextColor = (int) v;
			default -> {
				return false;
			}
		}
		return true;
	}

	private static boolean buttonNumber(TitleScreenDef.ButtonDef b, String p, double v) {
		switch (p) {
			case "X" -> b.x = (int) v;
			case "Y" -> b.y = (int) v;
			case "WIDTH" -> b.width = (int) v;
			case "HEIGHT" -> b.height = (int) v;
			case "SCALE" -> b.textScale = v;
			case "COLOR" -> b.textColor = (int) v;
			case "HOVER_COLOR" -> b.hoverTextColor = (int) v;
			case "TINT" -> b.textureTint = (int) v;
			case "OPACITY" -> b.backgroundOpacity = (int) v;
			default -> {
				return false;
			}
		}
		return true;
	}

	private static boolean textNumber(TitleScreenDef.TextDef t, String p, double v) {
		switch (p) {
			case "X" -> t.x = (int) v;
			case "Y" -> t.y = (int) v;
			case "SCALE" -> t.scale = v;
			case "COLOR" -> t.color = (int) v;
			case "HOVER_COLOR" -> t.hoverColor = (int) v;
			case "OPACITY" -> t.opacity = (int) v;
			case "ROTATION" -> t.rotation = v;
			default -> {
				return false;
			}
		}
		return true;
	}

	private static boolean imageNumber(TitleScreenDef.ImageDef i, String p, double v) {
		switch (p) {
			case "X" -> i.x = (int) v;
			case "Y" -> i.y = (int) v;
			case "WIDTH" -> i.width = (int) v;
			case "HEIGHT" -> i.height = (int) v;
			case "OPACITY" -> i.opacity = (int) v;
			case "TINT", "COLOR" -> i.tint = (int) v;
			case "ROTATION" -> i.rotation = v;
			default -> {
				return false;
			}
		}
		return true;
	}

	private static boolean widgetNumber(TitleScreenDef.WidgetDef w, String p, double v) {
		switch (p) {
			case "X" -> w.x = (int) v;
			case "Y" -> w.y = (int) v;
			case "WIDTH" -> w.width = (int) v;
			case "HEIGHT" -> w.height = (int) v;
			case "OPACITY" -> w.opacity = (int) v;
			case "ROTATION" -> w.rotation = v;
			case "COLOR" -> w.color = (int) v;
			case "HOVER_COLOR" -> w.hoverColor = (int) v;
			case "TINT" -> w.tint = (int) v;
			case "SPIN_X" -> w.spinX = v;
			case "SPIN_Y", "SPIN" -> w.spinY = v;
			case "SPIN_Z" -> w.spinZ = v;
			case "TILT_X" -> w.tiltX = v;
			case "TILT_Y" -> w.tiltY = v;
			case "GLOW_OPACITY" -> w.glowOpacity = (int) v;
			case "GLOW_SIZE" -> w.glowSize = (int) v;
			case "EFFECT_COUNT" -> w.effectCount = (int) v;
			case "EFFECT_SPEED" -> w.effectSpeed = v;
			case "EFFECT_SIZE" -> w.effectSize = (int) v;
			default -> {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ helpers
	private static boolean is(String wanted, String id) {
		return wanted != null && id != null && !id.isEmpty() && wanted.trim().equalsIgnoreCase(id);
	}

	/** "name" = this mod's screen texture, "ns:name" = another mod's screen texture, or a full path ending in .png. */
	@Nullable
	static Identifier texture(@Nullable String value) {
		if (value == null || value.isBlank())
			return null;
		String v = value.trim().toLowerCase(Locale.ROOT);
		if (v.endsWith(".png"))
			return TitleScreenDef.rl(v.contains(":") ? v : "aurore_mod:" + v);
		String ns = v.contains(":") ? v.substring(0, v.indexOf(':')) : "aurore_mod";
		String name = v.contains(":") ? v.substring(v.indexOf(':') + 1) : v;
		return TitleScreenDef.rl(ns + ":textures/screens/" + name + ".png");
	}

	private static void changed(String kind, String id, String arg, String value, boolean found) {
		if (!found && !replaying)
			LOGGER.warn("ATS procedure: no title screen element with ID '{}'", id);
		version++;
		if (replaying)
			return;
		OPERATIONS.put(kind + "|" + (id == null ? "" : id.trim().toLowerCase(Locale.ROOT)) + "|" + arg, kind + "|" + (id == null ? "" : id.trim()) + "|" + arg + "|" + escape(value));
		save();
	}

	private static Path file() {
		return FMLPaths.CONFIGDIR.get().resolve("advancedtitlescreens_aurore_mod.txt");
	}

	private static void save() {
		try {
			Files.write(file(), new ArrayList<>(OPERATIONS.values()), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("Could not save title screen changes: {}", e.toString());
		}
	}

	/** Re-applies saved procedure changes; called once when the title screens are built. */
	static synchronized void applySaved() {
		if (loaded)
			return;
		loaded = true;
		List<String> lines;
		try {
			Path f = file();
			if (!Files.isRegularFile(f))
				return;
			lines = Files.readAllLines(f, StandardCharsets.UTF_8);
		} catch (Exception e) {
			return;
		}
		replay(lines);
	}

	/** Used after reload(): applies the kept operations again on fresh definitions. */
	static synchronized void reapply() {
		replay(new ArrayList<>(OPERATIONS.values()));
	}

	private static void replay(List<String> lines) {
		replaying = true;
		try {
			for (String line : lines) {
				String[] p = line.split("\\|", 4);
				if (p.length < 4)
					continue;
				String value = unescape(p[3]);
				OPERATIONS.put(p[0] + "|" + p[1].toLowerCase(Locale.ROOT) + "|" + p[2], line);
				try {
					switch (p[0]) {
						case "text" -> setText(p[1], value);
						case "number" -> setNumber(p[1], p[2], Double.parseDouble(value));
						case "texture" -> setTexture(p[1], value);
						case "type" -> setWidgetType(p[1], value);
						case "effect" -> setEffectType(p[1], value);
						case "visible" -> setVisible(p[1], Boolean.parseBoolean(value));
						case "var" -> setVariable(p[1], value);
						case "bgtype" -> setBackgroundType(value);
						case "particles" -> setParticles(value);
						case "splash" -> setSplash(value);
						case "music" -> setMusic(value);
						default -> {
						}
					}
				} catch (Exception ignored) {
				}
			}
		} finally {
			replaying = false;
		}
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\n", "\\n").replace("|", "\\p");
	}

	private static String unescape(String s) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				char n = s.charAt(++i);
				out.append(n == 'n' ? '\n' : n == 'p' ? '|' : n);
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}
}