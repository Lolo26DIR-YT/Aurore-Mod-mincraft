package net.mcreator.auroremod.advancedtitlescreens.client;

import net.minecraft.util.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Deque;
import java.util.ArrayList;
import java.util.ArrayDeque;

import java.io.BufferedReader;

/**
 * Advanced Title Screens: the custom main menu that replaces Minecraft's TitleScreen. The same screen also replaces
 * the pause menu when a title screen element sets its pause menu to "Replace" (menu = "PAUSE").
 */
public class AdvancedTitleScreen extends Screen implements TitleMenus.Host {
	private static final Component COPYRIGHT_TEXT = Component.translatable("title.credits");
	private static boolean introShownThisSession;
	private static boolean openSoundPlayedThisSession;
	private TitleScreenDef def;
	/** TITLE or PAUSE */
	private final String menu;
	/** Last TitleScreenApi.version seen - procedure blocks bump it when they change something. */
	private int apiVersion = TitleScreenApi.version;
	private final LogoRenderer logoRenderer = new LogoRenderer(false);
	private final RandomSource random = RandomSource.create();
	private final TitleBackdrop backdrop;
	private final TitleLayer layer = new TitleLayer();
	@Nullable
	private final String splash;
	private final boolean playIntro;
	private long openedAtMs = -1;
	private final List<TitleButtonWidget> styledButtons = new ArrayList<>();
	@Nullable
	private PlainTextButton copyrightButton;
	/** Logo bounds from the last frame, used to attach the splash text. */
	private int logoLeft, logoTop, logoRight;
	/** Layout scaling: everything except the background is drawn in a virtual layoutW x layoutH screen scaled by
	 *  layoutScale, so a layout designed for designWidth x designHeight always fits. */
	private float layoutScale = 1F;
	private int layoutW, layoutH;
	private String currentPage;
	private final Deque<String> pageHistory = new ArrayDeque<>();
	private long pageOpenedAtMs = -1;

	public AdvancedTitleScreen(TitleScreenDef def) {
		this(def, "TITLE");
	}

	public AdvancedTitleScreen(TitleScreenDef def, String menu) {
		super(Component.translatable("PAUSE".equals(menu) ? "menu.game" : "narrator.screen.title"));
		this.def = def;
		this.menu = menu;
		this.backdrop = new TitleBackdrop(def);
		this.currentPage = def.pages.isEmpty() ? "main" : def.pages.get(0);
		if (isTitle()) {
			this.splash = pickSplash();
			this.playIntro = def.introFade && def.introFadeMs > 0 && !(def.introOnlyOnce && introShownThisSession);
			introShownThisSession = true;
		} else {
			this.splash = null;
			this.playIntro = false;
		}
	}

	private boolean isTitle() {
		return "TITLE".equals(menu);
	}

	@Override
	public String menu() {
		return menu;
	}

	@Override
	public boolean isPauseScreen() {
		return "PAUSE".equals(menu);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !isTitle();
	}

	@Override
	public void tick() {
		TitleServerStatus.tick();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 256 && !pageHistory.isEmpty()) { // Escape on a sub-page goes back
			goBack();
			return true;
		}
		return super.keyPressed(event);
	}

	// ------------------------------------------------------------------ pages
	@Override
	public String getCurrentPage() {
		return currentPage;
	}

	/** For the ATS "open page" block. */
	@Override
	public void openPage(String page) {
		goToPage(page);
	}

	/** For the ATS "go back" block. */
	@Override
	public void back() {
		goBack();
	}

	/** For the ATS "click button" block: runs the button's procedure and action. */
	@Override
	public void clickButton(String id) {
		for (TitleScreenDef.ButtonDef b : def.buttons)
			if (id.equalsIgnoreCase(b.id) && TitleScreenDef.onMenu(b.menu, menu)) {
				runAction(b);
				return;
			}
	}

	private void goToPage(String page) {
		if (page == null || page.isBlank() || page.equals(currentPage))
			return;
		pageHistory.push(currentPage);
		currentPage = page;
		pageOpenedAtMs = Util.getMillis();
		this.rebuildWidgets();
	}

	private void goBack() {
		if (pageHistory.isEmpty())
			return;
		currentPage = pageHistory.pop();
		pageOpenedAtMs = Util.getMillis();
		this.rebuildWidgets();
	}

	private final TitleLayer.Pages pages = new TitleLayer.Pages() {
		@Override
		public void open(String page) {
			goToPage(page);
		}

		@Override
		public void back() {
			goBack();
		}
	};

	// ------------------------------------------------------------------ layout
	private void computeLayout() {
		layoutScale = 1F;
		if (def.scaleLayout && def.designWidth > 0 && def.designHeight > 0 && this.width > 0 && this.height > 0)
			layoutScale = Math.min(this.width / (float) def.designWidth, this.height / (float) def.designHeight);
		layoutW = Math.max(1, Math.round(this.width / layoutScale));
		layoutH = Math.max(1, Math.round(this.height / layoutScale));
	}

	@Override
	protected void init() {
		computeLayout();
		styledButtons.clear();
		copyrightButton = null;
		if (openedAtMs < 0)
			openedAtMs = Util.getMillis();
		long entranceStart = pageOpenedAtMs >= 0 ? pageOpenedAtMs : openedAtMs + (playIntro ? def.introFadeMs / 2 : 0);
		int i = 0;
		for (TitleScreenDef.ButtonDef b : def.buttons) {
			if (b.hidden || !TitleScreenDef.onMenu(b.menu, menu) || !TitleScreenDef.onPage(b.page, currentPage))
				continue;
			int x = TitleRender.resolveX(b.anchor, b.x, b.width, layoutW);
			int y = TitleRender.resolveY(b.anchor, b.y, b.height, layoutH);
			TitleButtonWidget widget = new TitleButtonWidget(b, x, y, () -> runAction(b), entranceStart + (long) i * Math.max(0, def.buttonEntranceStaggerMs), def.buttonEntrance, def.buttonEntranceDurationMs);
			widget.active = TitleLayer.available(b.action);
			TitleMenus.adaptLabel(widget, b);
			styledButtons.add(this.addRenderableWidget(widget));
			i++;
		}
		if (isTitle() && def.showCopyright) {
			int copyrightWidth = this.font.width(COPYRIGHT_TEXT);
			copyrightButton = this.addRenderableWidget(new PlainTextButton(layoutW - copyrightWidth - 2, layoutH - 10, copyrightWidth, 10, COPYRIGHT_TEXT, button -> this.minecraft.setScreen(new CreditsAndAttributionScreen(this)), this.font));
		}
	}

	/** For the hover cursor: is the mouse (screen coordinates) over a button, slider, link or server row? */
	public boolean hoversClickable(double mouseX, double mouseY) {
		double x = mouseX / layoutScale, y = mouseY / layoutScale;
		for (var child : this.children())
			if (TitleCursor.clickable(child, x, y))
				return true;
		return layer.hoversLink(x, y);
	}

	private void runAction(TitleScreenDef.ButtonDef b) {
		TitleLayer.runAction(this, def, b, pages);
	}

	/** The event in layout coordinates (the layout is drawn scaled by layoutScale). */
	private MouseButtonEvent scaled(MouseButtonEvent event) {
		return new MouseButtonEvent(event.x() / layoutScale, event.y() / layoutScale, event.buttonInfo());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return super.mouseReleased(scaled(event));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return super.mouseDragged(scaled(event), dragX / layoutScale, dragY / layoutScale);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX / layoutScale, mouseY / layoutScale, scrollX, scrollY);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		super.mouseMoved(mouseX / layoutScale, mouseY / layoutScale);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent rawEvent, boolean doubleClick) {
		MouseButtonEvent event = scaled(rawEvent);
		if (super.mouseClicked(event, doubleClick))
			return true;
		return event.button() == 0 && layer.mouseClicked(this, def, event.x(), event.y());
	}

	// ------------------------------------------------------------------ splash
	@Nullable
	private String pickSplash() {
		List<String> pool = new ArrayList<>();
		if ("CUSTOM".equals(def.splashMode) || "CUSTOM_AND_VANILLA".equals(def.splashMode))
			pool.addAll(def.customSplashes);
		if ("VANILLA".equals(def.splashMode) || "CUSTOM_AND_VANILLA".equals(def.splashMode)) {
			try (BufferedReader reader = net.minecraft.client.Minecraft.getInstance().getResourceManager().openAsReader(Identifier.withDefaultNamespace("texts/splashes.txt"))) {
				reader.lines().map(String::trim).filter(line -> !line.isEmpty() && line.hashCode() != 125780783).forEach(pool::add);
			} catch (Exception ignored) {
			}
		}
		return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
	}

	// ------------------------------------------------------------------ rendering
	private float backgroundFade = 1F, foregroundFade = 1F;
	private long frameNow;

	/** First part of a frame: bookkeeping, then the backdrop (its own GUI stratum, so blur can apply to it). */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (apiVersion != TitleScreenApi.version) {
			// a procedure changed the title screen: pick up the (possibly rebuilt) definition and re-create buttons
			apiVersion = TitleScreenApi.version;
			TitleScreenDef latest = TitleScreenDefinitions.active();
			if (latest != null)
				def = latest;
			// background, particle type/color may have changed too
			backdrop.setDef(def);
			this.rebuildWidgets();
		}
		long now = Util.getMillis();
		frameNow = now;
		if (openedAtMs < 0)
			openedAtMs = now;
		if (isTitle() && def.openSound != null && !openSoundPlayedThisSession) {
			openSoundPlayedThisSession = true;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(def.openSound), 1.0F));
		}
		backgroundFade = 1F;
		foregroundFade = 1F;
		if (playIntro) {
			float t = (now - openedAtMs) / (float) def.introFadeMs;
			backgroundFade = Mth.clamp(t * 2F, 0F, 1F);
			foregroundFade = Mth.clampedMap(t, 0.5F, 1.0F, 0.0F, 1.0F);
		}
		if (isTitle()) {
			backdrop.renderAll(g, this.width, this.height, mouseX, mouseY, partialTick);
		} else {
			TitleScreenDef.MenuDef settings = def.menu(menu);
			if (settings == null || "KEEP".equals(settings.background))
				super.extractBackground(g, mouseX, mouseY, partialTick); // vanilla: the blurred game world
			if (settings != null)
				backdrop.renderMenu(g, settings, this.width, this.height, mouseX, mouseY, partialTick);
		}
		if (backgroundFade < 1F)
			g.fill(0, 0, this.width, this.height, TitleRender.argb(0x000000, 1F - backgroundFade));
	}

	/** Second part: every element, in the Layout tab's draw order. */
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = frameNow;
		layer.clearHitAreas();
		if (foregroundFade <= 0.01F)
			return;
		// page switches cross-fade everything that belongs to a page
		float pageFade = pageOpenedAtMs < 0 || def.pageTransitionMs <= 0 ? 1F : Mth.clamp((now - pageOpenedAtMs) / (float) def.pageTransitionMs, 0F, 1F);
		float itemFade = foregroundFade * pageFade;
		computeLayout();
		int screenMouseX = mouseX, screenMouseY = mouseY;
		mouseX = (int) Math.floor(mouseX / layoutScale);
		mouseY = (int) Math.floor(mouseY / layoutScale);
		g.pose().pushMatrix();
		g.pose().scale(layoutScale, layoutScale);
		for (TitleButtonWidget widget : styledButtons)
			widget.screenAlpha = itemFade;
		for (var child : this.children())
			if (child instanceof PlainTextButton plain)
				plain.setAlpha(foregroundFade);
		// every element in the Layout tab's order (buttons drawn one by one instead of by super's loop; each new
		// element that overlaps earlier ones is put above them); the logo and splash go just before the first button
		// or text, like the default layout
		boolean logoDone = !isTitle();
		for (Object item : TitleLayer.drawList(def)) {
			if (!logoDone && (item instanceof TitleScreenDef.ButtonDef || item instanceof TitleScreenDef.TextDef)) {
				logoDone = true;
				renderLogo(g, foregroundFade);
				renderSplash(g, foregroundFade, now);
			}
			if (item instanceof TitleScreenDef.ButtonDef b) {
				for (TitleButtonWidget widget : styledButtons)
					if (widget.def() == b)
						widget.extractRenderState(g, mouseX, mouseY, partialTick);
			} else {
				layer.renderItem(g, this.font, item, menu, currentPage, layoutW, layoutH, mouseX, mouseY, itemFade);
			}
		}
		if (!logoDone) {
			renderLogo(g, foregroundFade);
			renderSplash(g, foregroundFade, now);
		}
		if (copyrightButton != null)
			copyrightButton.extractRenderState(g, mouseX, mouseY, partialTick);
		g.pose().popMatrix();
		mouseX = screenMouseX;
		mouseY = screenMouseY;
		if (isTitle() && def.showBranding) {
			int alphaBits = Mth.ceil(foregroundFade * 255.0F) << 24;
			if ((alphaBits & -67108864) != 0) {
				net.neoforged.neoforge.internal.BrandingControl.forEachLine(true, true, (line, text) -> g.text(this.font, text, 2, this.height - (10 + line * (this.font.lineHeight + 1)), 16777215 | alphaBits));
				if (def.showCopyright)
					net.neoforged.neoforge.internal.BrandingControl
							.forEachAboveCopyrightLine((line, text) -> g.text(this.font, text, this.width - this.font.width(text), this.height - (10 + (line + 1) * (this.font.lineHeight + 1)), 16777215 | alphaBits));
			}
		}
	}

	private void renderLogo(GuiGraphicsExtractor g, float fade) {
		String type = def.logoType == null ? "VANILLA" : def.logoType;
		if ("NONE".equals(type)) {
			logoLeft = logoRight = layoutW / 2;
			logoTop = def.logoY;
			return;
		}
		int w, h;
		Identifier logoFont = TitleScreenDef.rl(def.logoFont);
		float textScale = (float) Math.max(0.1, def.logoTextScale);
		if ("IMAGE".equals(type) && def.logoImage != null) {
			w = def.logoWidth;
			h = def.logoHeight;
		} else if ("TEXT".equals(type)) {
			w = Math.round(TitleRender.textWidth(this.font, def.logoText, logoFont) * textScale);
			h = Math.round(8F * textScale);
		} else {
			w = 256;
			h = 44;
		}
		int x = TitleRender.resolveX(def.logoAnchor, def.logoX, w, layoutW);
		int y = TitleRender.resolveY(def.logoAnchor, def.logoY, h, layoutH);
		logoLeft = x;
		logoTop = y;
		logoRight = x + w;
		g.pose().pushMatrix();
		float motion = TitleRender.applyMotion(g.pose(), def.logoAnimation, def.logoAnimationSpeed, x + w / 2F, y + h / 2F);
		float alpha = fade * motion;
		if ("IMAGE".equals(type) && def.logoImage != null) {
			TitleRender.blitStretched(g, def.logoImage, x, y, w, h, alpha);
		} else if ("TEXT".equals(type)) {
			TitleRender.drawText(g, this.font, def.logoText, logoFont, x, y, def.logoTextColor, alpha, textScale, def.logoTextShadow);
		} else {
			// vanilla's LogoRenderer always centers on the screen; shift it so the anchor/offset still apply
			g.pose().translate(x - (layoutW / 2F - 128F), 0F);
			logoRenderer.extractRenderState(g, layoutW, alpha, y);
		}
		g.pose().popMatrix();
	}

	private void renderSplash(GuiGraphicsExtractor g, float fade, long now) {
		String splash = TitleScreenApi.splashOverride != null ? TitleScreenApi.splashOverride : this.splash;
		if (splash == null || (TitleScreenApi.splashOverride == null && "NONE".equals(def.splashMode)) || this.minecraft.options.hideSplashTexts().get())
			return;
		float cx, cy;
		if (def.splashAttachToLogo) {
			cx = logoRight + def.splashX;
			cy = logoTop + def.splashY;
		} else {
			cx = TitleRender.resolveX(def.splashAnchor, def.splashX, 0, layoutW);
			cy = TitleRender.resolveY(def.splashAnchor, def.splashY, 0, layoutH);
		}
		Identifier splashFont = TitleScreenDef.rl(def.splashFont);
		MutableComponent text = TitleRender.styled(TitlePlaceholders.resolve(splash), splashFont);
		float pulse = def.splashPulse ? 1.8F - Mth.abs(Mth.sin((float) (now % 1000L) / 1000.0F * (float) (Math.PI * 2)) * 0.1F) : 1.8F;
		float scale = pulse * 100.0F / (float) (this.font.width(text) + 32) * (float) Math.max(0.1, def.splashScale);
		int alphaBits = Mth.ceil(fade * 255.0F) << 24;
		if ((alphaBits & -67108864) == 0)
			return;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Math.toRadians((float) def.splashRotation));
		g.pose().scale(scale, scale);
		g.centeredText(this.font, text, 0, -8, (def.splashColor & 0xFFFFFF) | alphaBits);
		g.pose().popMatrix();
	}
}