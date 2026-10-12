package net.mcreator.auroremod.advancedtitlescreens.client;

import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;

import java.util.List;
import java.util.ArrayList;

/**
 * Advanced Title Screens: runtime copy of one Custom Title Screen mod element.
 * Plain data only - populated by TitleScreenDefinitions, read by AdvancedTitleScreen.
 */
public class TitleScreenDef {
	public final String id;
	public boolean enabled = true;
	public int selectionWeight = 1;
	public boolean introFade = true;
	public int introFadeMs = 2000;
	public boolean introOnlyOnce = true;
	public boolean showBranding = true;
	public boolean showCopyright = true;
	public String backgroundType = "VANILLA_PANORAMA";
	public final Identifier[] panoramaFaces = new Identifier[6];
	public double panoramaSpeed = 2.0;
	public double panoramaPitch = 10.0;
	public boolean panoramaOverlay = true;
	/** 0 = camera inside the cube (vanilla); larger = camera that far outside it. */
	public double panoramaDistance = 0;
	/** Camera field of view in degrees (vanilla 85); smaller zooms in. */
	public double panoramaFov = 85;
	@Nullable
	public Identifier backgroundImage;
	public String imageScaling = "COVER";
	public final List<Identifier> slideshowImages = new ArrayList<>();
	public int slideDurationMs = 7000;
	public int slideFadeMs = 1500;
	public boolean slideshowShuffle = false;
	public boolean kenBurns = true;
	public String video = "";
	public boolean videoLoop = true;
	public String videoEndBehavior = "HOLD";
	public int backgroundColor = 0x1B1B24;
	public int gradientTopColor = 0x1E2A4A;
	public int gradientBottomColor = 0x08080C;
	public boolean parallax = false;
	public int parallaxStrength = 12;
	public int overlayColor = 0;
	public int overlayOpacity = 0;
	public boolean vignette = false;
	public int vignetteStrength = 60;
	public boolean blurBackground = false;
	public String particleType = "NONE";
	public int particleColor = 0xFFFFFF;
	public int particleCount = 60;
	public double particleSpeed = 1.0;
	public int particleSize = 2;
	@Nullable
	public Identifier particleTexture;
	public String logoType = "VANILLA";
	@Nullable
	public Identifier logoImage;
	public int logoWidth = 256;
	public int logoHeight = 64;
	public String logoText = "";
	public String logoFont = "";
	public int logoTextColor = 0xFFFFFF;
	public double logoTextScale = 4.0;
	public boolean logoTextShadow = true;
	public String logoAnchor = "TOP_CENTER";
	public int logoX = 0;
	public int logoY = 30;
	public String logoAnimation = "NONE";
	public double logoAnimationSpeed = 1.0;
	public String splashMode = "VANILLA";
	public final List<String> customSplashes = new ArrayList<>();
	public int splashColor = 0xFFFF00;
	public String splashFont = "";
	public double splashScale = 1.0;
	public double splashRotation = -20.0;
	public boolean splashPulse = true;
	public boolean splashAttachToLogo = true;
	public String splashAnchor = "TOP_CENTER";
	public int splashX = -5;
	public int splashY = 39;
	public final List<ButtonDef> buttons = new ArrayList<>();
	public final List<TextDef> texts = new ArrayList<>();
	public final List<ImageDef> images = new ArrayList<>();
	public String buttonEntrance = "FADE";
	public int buttonEntranceDurationMs = 600;
	public int buttonEntranceStaggerMs = 70;
	public final List<WidgetDef> widgets = new ArrayList<>();
	/** The first page is where the menu opens. */
	public final List<String> pages = new ArrayList<>();
	public int pageTransitionMs = 250;
	public boolean confirmLinks = true;
	public boolean scaleLayout = true;
	public int designWidth = 427;
	public int designHeight = 240;
	public String musicMode = "VANILLA";
	@Nullable
	public Identifier music;
	public int musicMinDelay = 20;
	public int musicMaxDelay = 600;
	public boolean musicInSubmenus = true;
	public boolean stopMusicInWorld = true;
	@Nullable
	public Identifier openSound;
	// mouse effects
	public String cursorMode = "VANILLA";
	@Nullable
	public Identifier cursorTexture;
	@Nullable
	public Identifier cursorHoverTexture;
	public int cursorSize = 16;
	public int cursorHotspotX = 0;
	public int cursorHotspotY = 0;
	public String trailType = "NONE";
	public int trailColor = 0xFFD27F;
	public int trailColor2 = 0xFF4020;
	public int trailSize = 2;
	public int trailDensity = 6;
	public int trailLifetimeMs = 600;
	public double trailGravity = 0;
	@Nullable
	public Identifier trailTexture;
	public String clickEffect = "NONE";
	public int clickColor = 0xFFFFFF;
	public int clickCount = 12;
	public int clickSize = 2;
	public boolean mouseFxTitle = true;
	public boolean mouseFxPause = true;
	public boolean mouseFxOptions = true;
	public boolean mouseFxMods = true;
	public boolean mouseFxOther = false;
	public final MenuDef pauseMenu = new MenuDef();
	public final MenuDef optionsMenu = new MenuDef();
	public final MenuDef modsMenu = new MenuDef();

	public TitleScreenDef(String id) {
		this.id = id;
	}

	/** Settings of the PAUSE, OPTIONS or MODS menu (null for anything else, including TITLE). */
	@Nullable
	public MenuDef menu(String menu) {
		return switch (menu) {
			case "PAUSE" -> pauseMenu;
			case "OPTIONS" -> optionsMenu;
			case "MODS" -> modsMenu;
			default -> null;
		};
	}

	/** How one of the other menus (pause, options, mods) is customized. */
	public static class MenuDef {
		public boolean enabled;
		/** DECORATE: vanilla screen + our background and elements. REPLACE (pause menu only): our own screen. */
		public String mode = "DECORATE";
		/** KEEP (vanilla) | TITLE (the title screen background) | IMAGE | COLOR | GRADIENT */
		public String background = "KEEP";
		@Nullable
		public Identifier image;
		public String imageScaling = "COVER";
		public int color = 0x101014;
		public int gradientTopColor = 0x1E2A4A;
		public int gradientBottomColor = 0x08080C;
		public int overlayColor = 0;
		public int overlayOpacity = 0;
		public boolean blur;
		public boolean vignette;
		public int vignetteStrength = 60;
		/** Show the title screen's particles. */
		public boolean particles;
	}

	public static class ButtonDef {
		/** Position in the draw order (the back to front; the Layout tab's list shows it front first). */
		public int order;
		/** TITLE, PAUSE, OPTIONS or MODS */
		public String menu = "TITLE";
		/** "" = every page */
		public String page = "main";
		/** Name used by ATS procedure blocks; hidden is set by the show/hide block. */
		public String id = "";
		public boolean hidden;
		public String label = "";
		public String action = "NONE";
		public String actionValue = "";
		public String tooltip = "";
		/** The button's "run procedure", or null. */
		@Nullable
		public Runnable procedure;
		public String anchor = "CENTER";
		public int x, y;
		public int width = 200, height = 20;
		public String style = "VANILLA";
		@Nullable
		public Identifier texture;
		@Nullable
		public Identifier hoverTexture;
		public int textureTint = 0xFFFFFF;
		public int hoverTextureTint = 0xFFFFFF;
		public int backgroundColor = 0;
		public int backgroundOpacity = 55;
		public int hoverBackgroundColor = 0xFFFFFF;
		public int hoverBackgroundOpacity = 25;
		public int borderColor = 0xFFFFFF;
		public int hoverBorderColor = 0xFFFFFF;
		public int borderThickness = 0;
		public int textColor = 0xFFFFFF;
		public int hoverTextColor = 0xFFFFA0;
		public String font = "";
		public double textScale = 1.0;
		public boolean textShadow = true;
		public String textAlign = "CENTER";
		public boolean underlineOnHover = false;
		public String hoverEffect = "NONE";
		@Nullable
		public Identifier icon;
		public String vanillaIcon = "NONE";
		public int iconSize = 16;
		@Nullable
		public Identifier clickSound;
		@Nullable
		public Identifier hoverSound;
	}

	public static class TextDef {
		/** Position in the draw order (the back to front; the Layout tab's list shows it front first). */
		public int order;
		/** TITLE, PAUSE, OPTIONS or MODS */
		public String menu = "TITLE";
		/** "" = every page */
		public String page = "main";
		/** Name used by ATS procedure blocks; hidden is set by the show/hide block. */
		public String id = "";
		public boolean hidden;
		public String text = "";
		public String anchor = "TOP_LEFT";
		public int x, y;
		public int color = 0xFFFFFF;
		public int opacity = 100;
		public String font = "";
		public double scale = 1.0;
		public boolean shadow = true;
		public String url = "";
		public int hoverColor = 0xFFFF55;
		public double rotation;
		public String animation = "NONE";
		public double animationSpeed = 1.0;
	}

	public static class ImageDef {
		/** Position in the draw order (the back to front; the Layout tab's list shows it front first). */
		public int order;
		/** TITLE, PAUSE, OPTIONS or MODS */
		public String menu = "TITLE";
		/** "" = every page */
		public String page = "main";
		/** Name used by ATS procedure blocks; hidden is set by the show/hide block. */
		public String id = "";
		public boolean hidden;
		@Nullable
		public Identifier texture;
		public String anchor = "CENTER";
		public int x, y;
		public int width = 64, height = 64;
		public int opacity = 100;
		public String animation = "NONE";
		public double animationSpeed = 1.0;
		public String url = "";
		public int tint = 0xFFFFFF;
		public String stretch = "NONE";
		public double rotation;
	}

	public static class WidgetDef {
		/** Position in the draw order (the back to front; the Layout tab's list shows it front first). */
		public int order;
		/** TITLE, PAUSE, OPTIONS or MODS */
		public String menu = "TITLE";
		public String id = "";
		public boolean hidden;
		public String kind = "PANEL";
		public String page = "main";
		public String anchor = "CENTER";
		public int x, y;
		public int width = 120, height = 80;
		public int opacity = 100;
		public double rotation;
		public int color = 0;
		public int color2 = 0;
		public int opacity2 = 0;
		public String gradient = "NONE";
		public int borderColor = 0xFFFFFF;
		public int borderThickness = 0;
		@Nullable
		public Identifier texture;
		public int tint = 0xFFFFFF;
		public String stretch = "NONE";
		public String skinMode = "BUST";
		public boolean followMouse = true;
		/** Real 3D player model instead of the flat skin (skinMode still picks face / bust / full body). */
		public boolean model3d = false;
		public boolean skinFit = true;
		public double skinScale = 2.0;
		public int skinOffsetX = 0;
		public int skinOffsetY = 0;
		public boolean skinClip = true;
		public double modelYaw = 20;
		public double modelSpin = 0;
		@Nullable
		public Identifier texture2;
		public double spinX, spinY = 30, spinZ;
		public double tiltX = 20, tiltY = 30;
		public boolean shading = true;
		public int glowColor = 0xFFFFFF;
		public int glowOpacity = 0;
		public int glowSize = 250;
		public final List<ServerDef> servers = new ArrayList<>();
		public int rowHeight = 28;
		public int textColor = 0xFFFFFF;
		public int secondaryColor = 0xA0A0A0;
		public int hoverColor = 0x3A3A3A;
		public boolean showMotd = true;
		// EFFECT widgets (particles): color = particle color, texture = optional sprite
		public String effectType = "SNOW";
		public int effectCount = 60;
		public double effectSpeed = 1.0;
		public int effectSize = 2;
		/** Draw position: after the first layerIndex elements of group layer (WIDGETS, IMAGES, BUTTONS, TEXTS).
		 *  1.3.0 elements may still use BACK / MIDDLE / FRONT. */
		public String layer = "WIDGETS";
		public int layerIndex = 0;
	}

	public static class ServerDef {
		public String name = "";
		public String address = "";
		@Nullable
		public Identifier icon;

		public ServerDef(String name, String address, @Nullable Identifier icon) {
			this.name = name;
			this.address = address;
			this.icon = icon;
		}
	}

	/** Effective width: the screen width when stretched horizontally. */
	public static int stretchedW(String stretch, int width, int screenW) {
		return "WIDTH".equals(stretch) || "BOTH".equals(stretch) ? screenW : width;
	}

	public static int stretchedH(String stretch, int height, int screenH) {
		return "HEIGHT".equals(stretch) || "BOTH".equals(stretch) ? screenH : height;
	}

	/** True if an item made for {@code itemMenu} (blank = the title screen) belongs to {@code menu}. */
	public static boolean onMenu(@Nullable String itemMenu, String menu) {
		return (itemMenu == null || itemMenu.isEmpty() ? "TITLE" : itemMenu).equals(menu);
	}

	/** True if an item assigned to {@code itemPage} shows on {@code currentPage}. */
	public static boolean onPage(@Nullable String itemPage, String currentPage) {
		return itemPage == null || itemPage.isEmpty() || itemPage.equals(currentPage);
	}

	/** Parses a resource location, returning null (never throwing) for blank/invalid input. */
	@Nullable
	public static Identifier rl(@Nullable String value) {
		if (value == null || value.isBlank())
			return null;
		try {
			return Identifier.parse(value);
		} catch (Exception e) {
			return null;
		}
	}
}