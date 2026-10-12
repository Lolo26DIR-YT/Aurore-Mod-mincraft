package net.mcreator.auroremod.advancedtitlescreens.client;

import net.neoforged.neoforge.client.gui.ModListScreen;

import net.minecraft.util.Util;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.ArrayList;

import com.mojang.realmsclient.RealmsMainScreen;

/**
 * Advanced Title Screens: draws the layout elements (widgets, images, texts) of one menu/page, handles clicks on
 * links and server rows, and runs button actions. Shared by the custom title/pause screen and the decorated
 * vanilla menus.
 */
public class TitleLayer {
	/** Page navigation of whoever shows the layer. */
	public interface Pages {
		void open(String page);

		void back();
	}

	private final List<Link> links = new ArrayList<>();
	private final List<ServerArea> serverAreas = new ArrayList<>();
	/** Particle state per Effect element; rebuilt when its settings change (e.g. by a procedure). */
	private final Map<TitleScreenDef.WidgetDef, TitleParticles> effects = new IdentityHashMap<>();
	private final Map<TitleScreenDef.WidgetDef, String> effectKeys = new IdentityHashMap<>();

	private record ServerArea(TitleScreenDef.WidgetDef widget, int x, int y) {
	}

	private record Link(int x, int y, int w, int h, String url) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/** Call once per frame before rendering: clickable areas are collected while drawing. */
	public void clearHitAreas() {
		links.clear();
		serverAreas.clear();
	}

	// ------------------------------------------------------------------ rendering
	/** Every element (widgets, effects, images, buttons, texts) in draw order - back to front (the Layout tab's list shows it front first). */
	public static List<Object> drawList(TitleScreenDef def) {
		List<Object> all = new ArrayList<>(def.widgets.size() + def.images.size() + def.buttons.size() + def.texts.size());
		all.addAll(def.widgets);
		all.addAll(def.images);
		all.addAll(def.buttons);
		all.addAll(def.texts);
		all.sort(java.util.Comparator.comparingInt(TitleLayer::orderOf)); // stable: ties keep the list order
		return all;
	}

	private static int orderOf(Object item) {
		return switch (item) {
			case TitleScreenDef.WidgetDef w -> w.order;
			case TitleScreenDef.ImageDef i -> i.order;
			case TitleScreenDef.ButtonDef b -> b.order;
			case TitleScreenDef.TextDef t -> t.order;
			default -> 0;
		};
	}

	/** Is the element shown in this menu and page (and not hidden by a procedure)? */
	public static boolean shows(Object item, String menu, String page) {
		return switch (item) {
			case TitleScreenDef.WidgetDef w -> !w.hidden && TitleScreenDef.onMenu(w.menu, menu) && TitleScreenDef.onPage(w.page, page);
			case TitleScreenDef.ImageDef i -> !i.hidden && TitleScreenDef.onMenu(i.menu, menu) && TitleScreenDef.onPage(i.page, page);
			case TitleScreenDef.ButtonDef b -> !b.hidden && TitleScreenDef.onMenu(b.menu, menu) && TitleScreenDef.onPage(b.page, page);
			case TitleScreenDef.TextDef t -> !t.hidden && TitleScreenDef.onMenu(t.menu, menu) && TitleScreenDef.onPage(t.page, page);
			default -> false;
		};
	}

	/** Draws one widget, effect, image or text (buttons are Minecraft widgets, drawn by the screen). */
	public void renderItem(GuiGraphicsExtractor g, Font font, Object item, String menu, String page, int layoutW, int layoutH, int mouseX, int mouseY, float fade) {
		if (!shows(item, menu, page))
			return;
		switch (item) {
			case TitleScreenDef.WidgetDef w when "EFFECT".equals(w.kind) -> renderEffect(g, w, layoutW, layoutH, fade);
			case TitleScreenDef.WidgetDef w -> renderWidget(g, w, layoutW, layoutH, mouseX, mouseY, fade);
			case TitleScreenDef.ImageDef image -> renderImage(g, image, layoutW, layoutH, fade);
			case TitleScreenDef.TextDef text -> renderText(g, font, text, layoutW, layoutH, mouseX, mouseY, fade);
			default -> {
			}
		}
	}

	private void renderWidget(GuiGraphicsExtractor g, TitleScreenDef.WidgetDef w, int layoutW, int layoutH, int mouseX, int mouseY, float fade) {
		int ww = TitleScreenDef.stretchedW(w.stretch, w.width, layoutW);
		int wh = TitleScreenDef.stretchedH(w.stretch, w.height, layoutH);
		int wx = ww != w.width ? 0 : TitleRender.resolveX(w.anchor, w.x, w.width, layoutW);
		int wy = wh != w.height ? 0 : TitleRender.resolveY(w.anchor, w.y, w.height, layoutH);
		g.pose().pushMatrix();
		rotateAround(g, w.rotation, wx + ww / 2F, wy + wh / 2F);
		TitleWidgets.render(g, w, wx, wy, ww, wh, mouseX, mouseY, fade, Util.getMillis());
		g.pose().popMatrix();
		if ("SERVERS".equals(w.kind))
			serverAreas.add(new ServerArea(w, wx, wy));
	}

	/** An Effect element (particles in its area). */
	private void renderEffect(GuiGraphicsExtractor g, TitleScreenDef.WidgetDef w, int layoutW, int layoutH, float fade) {
		int ww = TitleScreenDef.stretchedW(w.stretch, w.width, layoutW);
		int wh = TitleScreenDef.stretchedH(w.stretch, w.height, layoutH);
		int wx = ww != w.width ? 0 : TitleRender.resolveX(w.anchor, w.x, w.width, layoutW);
		int wy = wh != w.height ? 0 : TitleRender.resolveY(w.anchor, w.y, w.height, layoutH);
		String key = w.effectType + "|" + w.color + "|" + w.effectCount + "|" + w.effectSpeed + "|" + w.effectSize + "|" + w.texture;
		TitleParticles particles = effects.get(w);
		if (particles == null || !key.equals(effectKeys.get(w))) {
			particles = new TitleParticles(w.effectType, w.color, w.effectCount, w.effectSpeed, w.effectSize, w.texture);
			effects.put(w, particles);
			effectKeys.put(w, key);
		}
		g.pose().pushMatrix();
		g.pose().translate(wx, wy);
		rotateAround(g, w.rotation, ww / 2F, wh / 2F);
		particles.render(g, ww, wh, fade * w.opacity / 100F, Util.getMillis());
		g.pose().popMatrix();
	}

	/** Is (mouseX, mouseY) (layout coordinates) over a link or a server row? */
	public boolean hoversLink(double mouseX, double mouseY) {
		for (Link link : links)
			if (link.contains(mouseX, mouseY))
				return true;
		for (ServerArea area : serverAreas)
			if (TitleWidgets.serverRowAt(area.widget(), area.x(), area.y(), mouseX, mouseY) >= 0)
				return true;
		return false;
	}

	/** Rotates the pose stack by {@code degrees} (clockwise) around (cx, cy). */
	public static void rotateAround(GuiGraphicsExtractor g, double degrees, float cx, float cy) {
		if (degrees == 0)
			return;
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Math.toRadians((float) degrees));
		g.pose().translate(-cx, -cy);
	}

	private void renderImage(GuiGraphicsExtractor g, TitleScreenDef.ImageDef image, int layoutW, int layoutH, float fade) {
		if (image.texture == null)
			return;
		int iw = TitleScreenDef.stretchedW(image.stretch, image.width, layoutW);
		int ih = TitleScreenDef.stretchedH(image.stretch, image.height, layoutH);
		int x = iw == layoutW && iw != image.width ? 0 : TitleRender.resolveX(image.anchor, image.x, iw, layoutW);
		int y = ih == layoutH && ih != image.height ? 0 : TitleRender.resolveY(image.anchor, image.y, ih, layoutH);
		g.pose().pushMatrix();
		rotateAround(g, image.rotation, x + iw / 2F, y + ih / 2F);
		float motion = TitleRender.applyMotion(g.pose(), image.animation, image.animationSpeed, x + iw / 2F, y + ih / 2F);
		TitleRender.blitTinted(g, image.texture, x, y, iw, ih, image.tint, fade * motion * image.opacity / 100F);
		g.pose().popMatrix();
		if (image.url != null && !image.url.isBlank())
			links.add(new Link(x, y, iw, ih, image.url.trim()));
	}

	private void renderText(GuiGraphicsExtractor g, Font font, TitleScreenDef.TextDef text, int layoutW, int layoutH, int mouseX, int mouseY, float fade) {
		Identifier fontId = TitleScreenDef.rl(text.font);
		float scale = (float) Math.max(0.1, text.scale);
		String value = TitlePlaceholders.resolve(text.text);
		int w = Math.round(TitleRender.textWidth(font, value, fontId) * scale);
		int h = Math.round(9F * scale);
		int x = TitleRender.resolveX(text.anchor, text.x, w, layoutW);
		int y = TitleRender.resolveY(text.anchor, text.y, h, layoutH);
		boolean isLink = text.url != null && !text.url.isBlank();
		boolean hovered = isLink && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		g.pose().pushMatrix();
		rotateAround(g, text.rotation, x + w / 2F, y + h / 2F);
		float motion = TitleRender.applyMotion(g.pose(), text.animation, text.animationSpeed, x + w / 2F, y + h / 2F);
		float alpha = fade * motion * text.opacity / 100F;
		int color = hovered ? text.hoverColor : text.color;
		TitleRender.drawText(g, font, value, fontId, x, y, color, alpha, scale, text.shadow);
		if (hovered && alpha >= 0.02F)
			g.fill(x, y + h - 1, x + w, y + h, TitleRender.argb(color, alpha));
		g.pose().popMatrix();
		if (isLink)
			links.add(new Link(x, y, w, h, text.url.trim()));
	}

	// ------------------------------------------------------------------ input
	/** Left click on a link or server row (layout coordinates). True if it was handled. */
	public boolean mouseClicked(Screen screen, TitleScreenDef def, double mouseX, double mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		for (Link link : links) {
			if (link.contains(mouseX, mouseY)) {
				clickSound();
				openLink(screen, def, link.url());
				return true;
			}
		}
		for (ServerArea area : serverAreas) {
			int row = TitleWidgets.serverRowAt(area.widget(), area.x(), area.y(), mouseX, mouseY);
			if (row >= 0 && minecraft.allowsMultiplayer() && minecraft.level == null) {
				TitleScreenDef.ServerDef server = area.widget().servers.get(row);
				if (ServerAddress.isValidAddress(server.address)) {
					clickSound();
					ConnectScreen.startConnecting(screen, minecraft, ServerAddress.parseString(server.address), new ServerData(server.name, server.address, ServerData.Type.OTHER), false, null);
					return true;
				}
			}
		}
		return false;
	}

	private static void clickSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	public static void openLink(Screen screen, TitleScreenDef def, String url) {
		if (url == null || url.isBlank())
			return;
		if (def.confirmLinks)
			ConfirmLinkScreen.confirmLinkNow(screen, url.trim());
		else
			Util.getPlatform().openUri(url.trim());
	}

	// ------------------------------------------------------------------ button actions
	/** False if the action can't be used right now (the button is then greyed out). */
	public static boolean available(String action) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean inWorld = minecraft.level != null;
		return switch (action == null ? "NONE" : action) {
			case "MULTIPLAYER", "REALMS", "JOIN_SERVER" -> !inWorld && minecraft.allowsMultiplayer();
			case "SINGLEPLAYER", "LOAD_WORLD" -> !inWorld;
			case "RESUME", "DISCONNECT" -> inWorld;
			case "ADVANCEMENTS", "STATISTICS" -> minecraft.player != null;
			case "SHARE_TO_LAN" -> minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null && !minecraft.getSingleplayerServer().isPublished();
			default -> true;
		};
	}

	/** Runs a button: its procedure first, then its action. {@code screen} becomes the parent of opened menus. */
	public static void runAction(Screen screen, TitleScreenDef def, TitleScreenDef.ButtonDef b, Pages pages) {
		if (b.procedure != null) {
			try {
				b.procedure.run();
			} catch (Throwable t) {
				org.slf4j.LoggerFactory.getLogger("AdvancedTitleScreens").error("Menu button procedure failed", t);
			}
		}
		if (!available(b.action))
			return;
		Minecraft minecraft = Minecraft.getInstance();
		String value = b.actionValue == null ? "" : b.actionValue.trim();
		switch (b.action == null ? "NONE" : b.action) {
			case "SINGLEPLAYER" -> minecraft.setScreen(new SelectWorldScreen(screen));
			case "MULTIPLAYER" -> minecraft.setScreen(minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(screen) : new SafetyScreen(screen));
			case "REALMS" -> minecraft.setScreen(new RealmsMainScreen(screen));
			case "MODS" -> minecraft.setScreen(new ModListScreen(screen));
			case "OPTIONS" -> minecraft.setScreen(new OptionsScreen(screen, minecraft.options, minecraft.level != null));
			case "LANGUAGE" -> minecraft.setScreen(new LanguageSelectScreen(screen, minecraft.options, minecraft.getLanguageManager()));
			case "ACCESSIBILITY" -> minecraft.setScreen(new AccessibilityOptionsScreen(screen, minecraft.options));
			case "CREDITS" -> minecraft.setScreen(new CreditsAndAttributionScreen(screen));
			case "QUIT" -> minecraft.stop();
			case "OPEN_URL" -> openLink(screen, def, value);
			case "PAGE" -> pages.open(value);
			case "BACK" -> pages.back();
			case "RESUME" -> minecraft.setScreen(null);
			case "ADVANCEMENTS" -> minecraft.setScreen(new AdvancementsScreen(minecraft.player.connection.getAdvancements(), screen));
			case "STATISTICS" -> minecraft.setScreen(new StatsScreen(screen, minecraft.player.getStats()));
			case "SHARE_TO_LAN" -> minecraft.setScreen(new ShareToLanScreen(screen));
			case "DISCONNECT" -> minecraft.getReportingContext().draftReportHandled(minecraft, screen, TitleLayer::disconnect, true);
			case "JOIN_SERVER" -> {
				if (!value.isEmpty() && ServerAddress.isValidAddress(value)) {
					String name = b.label == null || b.label.isBlank() || b.label.startsWith("lang:") ? value : b.label;
					ConnectScreen.startConnecting(screen, minecraft, ServerAddress.parseString(value), new ServerData(name, value, ServerData.Type.OTHER), false, null);
				}
			}
			case "LOAD_WORLD" -> {
				if (!value.isEmpty() && minecraft.getLevelSource().levelExists(value))
					minecraft.createWorldOpenFlows().openWorld(value, () -> minecraft.setScreen(screen));
				else
					minecraft.setScreen(new SelectWorldScreen(screen));
			}
			default -> {
			}
		}
	}

	/** Vanilla's "Save and Quit to Title" / "Disconnect" (like the pause menu's button). */
	private static void disconnect() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null)
			return;
		// ends on a plain TitleScreen (or the server list): TitleScreenHooks swaps in the custom one
		minecraft.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
	}
}