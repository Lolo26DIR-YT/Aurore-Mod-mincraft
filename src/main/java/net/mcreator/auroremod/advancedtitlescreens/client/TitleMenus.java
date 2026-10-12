package net.mcreator.auroremod.advancedtitlescreens.client;

import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.util.Util;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

import java.util.*;

/**
 * Advanced Title Screens: the pause, options and mods menus. "Decorate" keeps the vanilla screen and its buttons
 * and adds the custom background plus the layout elements made for that menu. (The pause menu's "Replace" mode
 * is an AdvancedTitleScreen instead, see TitleScreenHooks.)
 */
@EventBusSubscriber(Dist.CLIENT)
public class TitleMenus {
	/** A menu showing custom elements: the custom title/pause screen or a decorated vanilla menu. */
	public interface Host {
		/** TITLE, PAUSE, OPTIONS or MODS */
		String menu();

		String getCurrentPage();

		void openPage(String page);

		void back();

		void clickButton(String id);
	}

	private static final Map<Screen, Decorated> DECORATED = new WeakHashMap<>();

	/** The menu with custom elements that is open right now, or null. */
	@Nullable
	public static Host host() {
		Screen screen = Minecraft.getInstance().screen;
		if (screen instanceof AdvancedTitleScreen title)
			return title;
		return screen == null ? null : DECORATED.get(screen);
	}

	/** The pause menu's quit button says "Disconnect" on servers, like vanilla. */
	static void adaptLabel(TitleButtonWidget widget, TitleScreenDef.ButtonDef b) {
		if ("DISCONNECT".equals(b.action) && "lang:menu.returnToMenu".equals(b.label) && !Minecraft.getInstance().isLocalServer())
			widget.labelOverride = "lang:menu.disconnect";
	}

	/** Which of our menus a vanilla screen is (exact classes, so other mods' subclasses are left alone). */
	@Nullable
	private static String menuOf(Screen screen) {
		if (screen.getClass() == PauseScreen.class)
			return ((PauseScreen) screen).showsPauseMenu() ? "PAUSE" : null;
		if (screen.getClass() == OptionsScreen.class)
			return "OPTIONS";
		if (screen.getClass() == ModListScreen.class)
			return "MODS";
		return null;
	}

	@Nullable
	private static Decorated decorated(Screen screen) {
		Decorated existing = DECORATED.get(screen);
		if (existing != null)
			return existing;
		String menu = menuOf(screen);
		TitleScreenDef def = menu == null ? null : TitleScreenDefinitions.active();
		TitleScreenDef.MenuDef settings = def == null ? null : def.menu(menu);
		if (settings == null || !settings.enabled || ("PAUSE".equals(menu) && "REPLACE".equals(settings.mode)))
			return null;
		Decorated created = new Decorated(screen, menu, def);
		DECORATED.put(screen, created);
		return created;
	}

	@SubscribeEvent
	public static void onInit(ScreenEvent.Init.Post event) {
		Decorated d = decorated(event.getScreen());
		if (d == null)
			return;
		d.buttons.clear();
		long start = Util.getMillis();
		int i = 0;
		// every button of this menu is added once; visibility follows the page and the show/hide block per frame
		for (TitleScreenDef.ButtonDef b : d.def.buttons) {
			if (!TitleScreenDef.onMenu(b.menu, d.menu))
				continue;
			int x = TitleRender.resolveX(b.anchor, b.x, b.width, d.screen.width);
			int y = TitleRender.resolveY(b.anchor, b.y, b.height, d.screen.height);
			TitleButtonWidget widget = new TitleButtonWidget(b, x, y, () -> TitleLayer.runAction(d.screen, d.def, b, d), start + (long) i * Math.max(0, d.def.buttonEntranceStaggerMs), d.def.buttonEntrance, d.def.buttonEntranceDurationMs);
			widget.active = TitleLayer.available(b.action);
			widget.manualRender = true; // drawn in Render.Post, in the Layout tab's order
			adaptLabel(widget, b);
			d.buttons.add(widget);
			event.addListener(widget);
			i++;
		}
		d.syncButtons();
	}

	@SubscribeEvent
	public static void onBackground(ScreenEvent.Render.Background event) {
		Decorated d = DECORATED.get(event.getScreen());
		if (d == null)
			return;
		if (d.apiVersion != TitleScreenApi.version) {
			d.apiVersion = TitleScreenApi.version;
			d.backdrop.setDef(d.def);
		}
		d.syncButtons();
		Screen screen = d.screen;
		int[] mouse = {event.getMouseX(), event.getMouseY()};
		TitleScreenDef.MenuDef settings = d.def.menu(d.menu);
		if (settings != null)
			d.backdrop.renderMenu(event.getGuiGraphics(), settings, screen.width, screen.height, mouse[0], mouse[1], event.getPartialTick());
		d.layer.clearHitAreas();
		float fade = d.pageFade();
		// the vanilla widgets can't be put in between: everything before your first button or text goes under them
		List<Object> order = TitleLayer.drawList(d.def);
		for (int i = 0; i < splitIndex(d, order); i++) {
			d.layer.renderItem(event.getGuiGraphics(), Minecraft.getInstance().font, order.get(i), d.menu, d.page, screen.width, screen.height, mouse[0], mouse[1], fade);
		}
	}

	/** Where the draw order passes the vanilla widgets: at the first shown button or text of this menu. */
	private static int splitIndex(Decorated d, List<Object> order) {
		for (int i = 0; i < order.size(); i++)
			if ((order.get(i) instanceof TitleScreenDef.ButtonDef || order.get(i) instanceof TitleScreenDef.TextDef) && TitleLayer.shows(order.get(i), d.menu, d.page))
				return i;
		return order.size();
	}

	@SubscribeEvent
	public static void onRender(ScreenEvent.Render.Post event) {
		Decorated d = DECORATED.get(event.getScreen());
		if (d == null)
			return;
		Screen screen = d.screen;
		// from your first button or text on, everything goes on top of the vanilla widgets, in the Layout tab's order
		List<Object> order = TitleLayer.drawList(d.def);
		for (int i = splitIndex(d, order); i < order.size(); i++) {
			Object item = order.get(i);
			if (item instanceof TitleScreenDef.ButtonDef b) {
				for (TitleButtonWidget widget : d.buttons)
					if (widget.def() == b)
						widget.drawNow(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
			} else {
				d.layer.renderItem(event.getGuiGraphics(), Minecraft.getInstance().font, item, d.menu, d.page, screen.width, screen.height, event.getMouseX(), event.getMouseY(), d.pageFade());
			}
		}
	}

	@SubscribeEvent
	public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
		Decorated d = DECORATED.get(event.getScreen());
		if (d != null && event.getButton() == 0 && !anyWidgetAt(d.screen, event.getMouseX(), event.getMouseY()) && d.layer.mouseClicked(d.screen, d.def, event.getMouseX(), event.getMouseY()))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onKey(ScreenEvent.KeyPressed.Pre event) {
		Decorated d = DECORATED.get(event.getScreen());
		if (d != null && event.getKeyCode() == 256 && !d.history.isEmpty()) { // Escape on a sub-page goes back
			d.back();
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Screen screen = Minecraft.getInstance().screen;
		if (screen != null && DECORATED.containsKey(screen))
			TitleServerStatus.tick();
	}

	private static boolean anyWidgetAt(Screen screen, double x, double y) {
		for (var child : screen.children())
			if (child.isMouseOver(x, y))
				return true;
		return false;
	}

	/** State of one open decorated vanilla menu. */
	private static final class Decorated implements Host, TitleLayer.Pages {
		final Screen screen;
		final String menu;
		final TitleScreenDef def;
		final TitleBackdrop backdrop;
		final TitleLayer layer = new TitleLayer();
		final List<TitleButtonWidget> buttons = new ArrayList<>();
		final Deque<String> history = new ArrayDeque<>();
		String page;
		long pageOpenedAtMs = -1;
		int apiVersion = TitleScreenApi.version;

		Decorated(Screen screen, String menu, TitleScreenDef def) {
			this.screen = screen;
			this.menu = menu;
			this.def = def;
			this.backdrop = new TitleBackdrop(def);
			this.page = def.pages.isEmpty() ? "main" : def.pages.get(0);
		}

		/** Shows the buttons of the current page that aren't hidden by a procedure. */
		void syncButtons() {
			for (TitleButtonWidget widget : buttons) {
				TitleScreenDef.ButtonDef b = widget.def();
				widget.visible = !b.hidden && TitleScreenDef.onPage(b.page, page);
				// follows window resizes (not every vanilla menu re-runs init on resize)
				widget.setPosition(TitleRender.resolveX(b.anchor, b.x, b.width, screen.width), TitleRender.resolveY(b.anchor, b.y, b.height, screen.height));
				widget.screenAlpha = pageFade();
			}
		}

		float pageFade() {
			if (pageOpenedAtMs < 0 || def.pageTransitionMs <= 0)
				return 1F;
			return Math.min(1F, Math.max(0F, (Util.getMillis() - pageOpenedAtMs) / (float) def.pageTransitionMs));
		}

		@Override
		public String menu() {
			return menu;
		}

		@Override
		public String getCurrentPage() {
			return page;
		}

		@Override
		public void openPage(String next) {
			open(next);
		}

		@Override
		public void open(String next) {
			if (next == null || next.isBlank() || next.equals(page))
				return;
			history.push(page);
			page = next.trim();
			pageOpenedAtMs = Util.getMillis();
			syncButtons();
		}

		@Override
		public void back() {
			if (history.isEmpty())
				return;
			page = history.pop();
			pageOpenedAtMs = Util.getMillis();
			syncButtons();
		}

		@Override
		public void clickButton(String id) {
			for (TitleScreenDef.ButtonDef b : def.buttons)
				if (id.equalsIgnoreCase(b.id) && TitleScreenDef.onMenu(b.menu, menu)) {
					TitleLayer.runAction(screen, def, b, this);
					return;
				}
		}
	}
}