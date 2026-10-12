package net.mcreator.auroremod.advancedtitlescreens.client;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFW;

import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.util.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.util.ARGB;
import net.minecraft.resources.Identifier;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

import java.nio.ByteBuffer;

import java.io.InputStream;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.NativeImage;

/**
 * Advanced Title Screens: the Mouse Effects tab - a custom cursor (set as the real OS cursor, so it never lags),
 * a particle trail behind the mouse and click bursts/ripples, in the menus the title screen element picks.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class TitleCursor {
	private static final int MAX_PARTICLES = 700;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<Particle> PARTICLES = new ArrayList<>();
	private static final List<Ripple> RIPPLES = new ArrayList<>();
	private static double lastX = Double.NaN, lastY;
	private static double carry;
	private static long lastFrameMs = -1;
	private static long normalCursor, hoverCursor, activeCursor;
	/** The cursor wanted this frame (0 = none of ours); applied after vanilla picks its own cursor. */
	private static long frameCursor;
	private static String cursorKey = "";

	private static final class Particle {
		float x, y, vx, vy, size, phase;
		long born;
		int lifeMs;
		/** Click burst particles use the click color instead of the trail colors. */
		boolean burst;
	}

	private record Ripple(float x, float y, long born) {
	}

	private TitleCursor() {
	}

	/** True if the mouse effects are switched on for this screen. */
	private static boolean applies(TitleScreenDef def, Screen screen) {
		if (screen instanceof AdvancedTitleScreen title)
			return "PAUSE".equals(title.menu()) ? def.mouseFxPause : def.mouseFxTitle;
		if (screen instanceof PauseScreen)
			return def.mouseFxPause;
		if (screen.getClass() == OptionsScreen.class)
			return def.mouseFxOptions;
		if (screen.getClass() == ModListScreen.class)
			return def.mouseFxMods;
		// other menus only outside a world, never the inventory, chat & co.
		return def.mouseFxOther && Minecraft.getInstance().level == null;
	}

	private static boolean anyEffect(TitleScreenDef def) {
		return "TEXTURE".equals(def.cursorMode) || !"NONE".equals(def.trailType) || !"NONE".equals(def.clickEffect);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onRender(ScreenEvent.Render.Post event) {
		TitleScreenDef def = TitleScreenDefinitions.active();
		Screen screen = event.getScreen();
		boolean on = def != null && anyEffect(def) && applies(def, screen);
		double[] mouse = mouse();
		setCursor(on ? def : null, screen, mouse[0], mouse[1]);
		if (!on) {
			PARTICLES.clear();
			RIPPLES.clear();
			lastX = Double.NaN;
			return;
		}
		long now = Util.getMillis();
		float dt = lastFrameMs < 0 ? 0F : Math.min(0.1F, (now - lastFrameMs) / 1000F);
		lastFrameMs = now;
		emitTrail(def, mouse[0], mouse[1], now);
		GuiGraphicsExtractor g = event.getGuiGraphics();
		renderParticles(g, def, now, dt);
		renderRipples(g, def, now);
	}

	@SubscribeEvent
	public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
		TitleScreenDef def = TitleScreenDefinitions.active();
		if (def == null || "NONE".equals(def.clickEffect) || !applies(def, event.getScreen()))
			return;
		float x = (float) event.getMouseX(), y = (float) event.getMouseY();
		long now = Util.getMillis();
		if ("BURST".equals(def.clickEffect) || "BOTH".equals(def.clickEffect)) {
			int count = Math.max(1, Math.min(def.clickCount, 200));
			for (int i = 0; i < count && PARTICLES.size() < MAX_PARTICLES; i++) {
				Particle p = new Particle();
				double angle = (i + RANDOM.nextFloat() * 0.6) / count * Math.PI * 2;
				float speed = 45F + RANDOM.nextFloat() * 50F;
				p.x = x;
				p.y = y;
				p.vx = (float) Math.cos(angle) * speed;
				p.vy = (float) Math.sin(angle) * speed;
				p.size = Math.max(1, def.clickSize);
				p.born = now;
				p.lifeMs = 350 + RANDOM.nextInt(250);
				p.burst = true;
				PARTICLES.add(p);
			}
		}
		if ("RIPPLE".equals(def.clickEffect) || "BOTH".equals(def.clickEffect))
			RIPPLES.add(new Ripple(x, y, now));
	}

	/**
	 * Vanilla picks a cursor at the end of every GUI frame (a hand over buttons...), so ours is set after it.
	 * Without an open screen no Render.Post happens and frameCursor stays 0: the normal cursor comes back.
	 */
	@SubscribeEvent
	public static void onFrame(RenderFrameEvent.Post event) {
		long wanted = frameCursor;
		frameCursor = 0L;
		Window window = Minecraft.getInstance().getWindow();
		if (wanted != 0L) {
			GLFW.glfwSetCursor(window.handle(), wanted);
		} else if (activeCursor != 0L) {
			GLFW.glfwSetCursor(window.handle(), 0L);
			window.selectCursor(CursorType.DEFAULT); // vanilla re-applies its own choice from the next frame on
		}
		activeCursor = wanted;
	}

	// ------------------------------------------------------------------ trail
	private static void emitTrail(TitleScreenDef def, double mx, double my, long now) {
		if (Double.isNaN(lastX) || "NONE".equals(def.trailType)) {
			lastX = mx;
			lastY = my;
			return;
		}
		double dx = mx - lastX, dy = my - lastY;
		double distance = Math.sqrt(dx * dx + dy * dy);
		carry += distance * Math.max(1, def.trailDensity) / 10.0;
		int n = Math.min((int) carry, 60);
		carry -= (int) carry;
		for (int i = 0; i < n && PARTICLES.size() < MAX_PARTICLES; i++) {
			float t = (i + RANDOM.nextFloat()) / n;
			Particle p = new Particle();
			p.x = (float) (lastX + dx * t) + (RANDOM.nextFloat() - 0.5F) * 2F;
			p.y = (float) (lastY + dy * t) + (RANDOM.nextFloat() - 0.5F) * 2F;
			p.phase = RANDOM.nextFloat() * 6.2831855F;
			p.size = Math.max(1, def.trailSize) * (0.7F + RANDOM.nextFloat() * 0.6F);
			p.born = now;
			p.lifeMs = Math.max(50, (int) (def.trailLifetimeMs * (0.7F + RANDOM.nextFloat() * 0.6F)));
			float spread = (RANDOM.nextFloat() - 0.5F);
			switch (def.trailType) {
				case "SPARKLE" -> {
					p.vx = spread * 50F;
					p.vy = (RANDOM.nextFloat() - 0.5F) * 50F;
				}
				case "EMBERS" -> {
					p.vx = spread * 16F;
					p.vy = -(12F + RANDOM.nextFloat() * 22F);
				}
				case "SMOKE" -> {
					p.vx = spread * 8F;
					p.vy = -(6F + RANDOM.nextFloat() * 8F);
				}
				case "SNOW" -> {
					p.vx = spread * 8F;
					p.vy = 10F + RANDOM.nextFloat() * 14F;
				}
				case "BUBBLES" -> {
					p.vx = spread * 6F;
					p.vy = -(10F + RANDOM.nextFloat() * 14F);
				}
				case "GLOW", "STARS" -> {
					p.vx = spread * 4F;
					p.vy = (RANDOM.nextFloat() - 0.5F) * 4F;
				}
				default -> { // TEXTURE
					p.vx = spread * 20F;
					p.vy = (RANDOM.nextFloat() - 0.5F) * 20F;
				}
			}
			PARTICLES.add(p);
		}
		lastX = mx;
		lastY = my;
	}

	private static void renderParticles(GuiGraphicsExtractor g, TitleScreenDef def, long now, float dt) {
		Iterator<Particle> it = PARTICLES.iterator();
		String type = def.trailType == null ? "NONE" : def.trailType;
		while (it.hasNext()) {
			Particle p = it.next();
			float t = (now - p.born) / (float) p.lifeMs;
			if (t >= 1F) {
				it.remove();
				continue;
			}
			p.vy += (float) def.trailGravity * dt;
			if (p.burst)
				p.vx *= 1F - Math.min(1F, dt * 3F);
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			if (p.burst) {
				float s = p.size * (1F - t * 0.5F);
				square(g, p.x, p.y, s, TitleRender.argb(def.clickColor, 1F - t));
				continue;
			}
			int color = lerpColor(def.trailColor, def.trailColor2, t);
			float alpha = 1F - t;
			float s = p.size;
			switch (type) {
				case "SPARKLE" -> {
					s *= 1F - t * 0.7F;
					square(g, p.x, p.y, s, TitleRender.argb(color, alpha));
					square(g, p.x - s, p.y, s, TitleRender.argb(color, alpha * 0.5F));
					square(g, p.x + s, p.y, s, TitleRender.argb(color, alpha * 0.5F));
					square(g, p.x, p.y - s, s, TitleRender.argb(color, alpha * 0.5F));
					square(g, p.x, p.y + s, s, TitleRender.argb(color, alpha * 0.5F));
				}
				case "GLOW" -> {
					square(g, p.x, p.y, s * 3F, TitleRender.argb(color, alpha * 0.2F));
					square(g, p.x, p.y, s * 1.8F, TitleRender.argb(color, alpha * 0.35F));
					square(g, p.x, p.y, s, TitleRender.argb(color, alpha));
				}
				case "SMOKE" -> square(g, p.x, p.y, s * (1F + t * 2F), TitleRender.argb(color, alpha * 0.45F));
				case "STARS" -> {
					float twinkle = 0.4F + 0.6F * Math.abs((float) Math.sin(p.phase + (now - p.born) * 0.012F));
					square(g, p.x, p.y, s, TitleRender.argb(color, alpha * twinkle));
					square(g, p.x - s, p.y, s, TitleRender.argb(color, alpha * twinkle * 0.4F));
					square(g, p.x + s, p.y, s, TitleRender.argb(color, alpha * twinkle * 0.4F));
					square(g, p.x, p.y - s, s, TitleRender.argb(color, alpha * twinkle * 0.4F));
					square(g, p.x, p.y + s, s, TitleRender.argb(color, alpha * twinkle * 0.4F));
				}
				case "BUBBLES" -> ring(g, p.x, p.y, s * 1.5F, TitleRender.argb(color, alpha));
				case "TEXTURE" -> {
					if (def.trailTexture != null) {
						g.pose().pushMatrix();
						g.pose().translate(p.x - s * 2F, p.y - s * 2F);
						TitleRender.blitTinted(g, def.trailTexture, 0, 0, Math.max(1, Math.round(s * 4F)), Math.max(1, Math.round(s * 4F)), color, alpha);
						g.pose().popMatrix();
					} else {
						square(g, p.x, p.y, s, TitleRender.argb(color, alpha));
					}
				}
				default -> square(g, p.x, p.y, s, TitleRender.argb(color, alpha));
			}
		}
	}

	private static void renderRipples(GuiGraphicsExtractor g, TitleScreenDef def, long now) {
		Iterator<Ripple> it = RIPPLES.iterator();
		while (it.hasNext()) {
			Ripple r = it.next();
			float t = (now - r.born()) / 450F;
			if (t >= 1F) {
				it.remove();
				continue;
			}
			float radius = 2F + t * (10F + def.clickSize * 4F);
			int color = TitleRender.argb(def.clickColor, 1F - t);
			int dots = 40;
			for (int i = 0; i < dots; i++) {
				double a = i / (double) dots * Math.PI * 2;
				square(g, r.x() + (float) Math.cos(a) * radius, r.y() + (float) Math.sin(a) * radius, 1F, color);
			}
		}
	}

	/** A filled square centered on (x, y), at sub-pixel precision. */
	private static void square(GuiGraphicsExtractor g, float x, float y, float size, int argb) {
		if ((argb >>> 24) == 0 || size <= 0)
			return;
		g.pose().pushMatrix();
		g.pose().translate(x - size / 2F, y - size / 2F);
		g.pose().scale(size, size);
		g.fill(0, 0, 1, 1, argb);
		g.pose().popMatrix();
	}

	/** A hollow square (bubble) centered on (x, y). */
	private static void ring(GuiGraphicsExtractor g, float x, float y, float size, int argb) {
		float half = size / 2F;
		g.pose().pushMatrix();
		g.pose().translate(x - half, y - half);
		int s = Math.max(2, Math.round(size));
		g.fill(0, 0, s, 1, argb);
		g.fill(0, s - 1, s, s, argb);
		g.fill(0, 1, 1, s - 1, argb);
		g.fill(s - 1, 1, s, s - 1, argb);
		g.pose().popMatrix();
	}

	private static int lerpColor(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
		int gr = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
		int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
		return (r << 16) | (gr << 8) | bl;
	}

	/** Mouse position in GUI coordinates, with sub-pixel precision. */
	private static double[] mouse() {
		Minecraft minecraft = Minecraft.getInstance();
		Window window = minecraft.getWindow();
		return new double[]{minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth()), minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight())};
	}

	// ------------------------------------------------------------------ cursor
	/** Picks the custom (or hover) cursor for this frame; {@code def} null = the normal system cursor. */
	private static void setCursor(@Nullable TitleScreenDef def, Screen screen, double mx, double my) {
		long wanted = 0L;
		if (def != null && "TEXTURE".equals(def.cursorMode) && def.cursorTexture != null) {
			prepareCursors(def);
			wanted = hoverCursor != 0L && hovering(screen, mx, my) ? hoverCursor : normalCursor;
		}
		applyCursor(wanted);
	}

	private static void applyCursor(long cursor) {
		frameCursor = cursor;
	}

	/** (Re)creates the native cursors when the texture, size or GUI scale changed. */
	private static void prepareCursors(TitleScreenDef def) {
		double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
		String key = def.cursorTexture + "|" + def.cursorHoverTexture + "|" + def.cursorSize + "|" + guiScale + "|" + def.cursorHotspotX + "|" + def.cursorHotspotY;
		if (key.equals(cursorKey))
			return;
		cursorKey = key;
		if (activeCursor != 0L) { // never destroy the cursor GLFW is showing
			GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), 0L);
			activeCursor = 0L;
		}
		frameCursor = 0L;
		if (normalCursor != 0L)
			GLFW.glfwDestroyCursor(normalCursor);
		if (hoverCursor != 0L)
			GLFW.glfwDestroyCursor(hoverCursor);
		int size = Math.max(4, (int) Math.round(Math.max(1, def.cursorSize) * guiScale));
		int hotX = (int) Math.round(def.cursorHotspotX * guiScale), hotY = (int) Math.round(def.cursorHotspotY * guiScale);
		normalCursor = createCursor(def.cursorTexture, size, hotX, hotY);
		hoverCursor = def.cursorHoverTexture == null ? 0L : createCursor(def.cursorHoverTexture, size, hotX, hotY);
	}

	private static long createCursor(Identifier texture, int width, int hotX, int hotY) {
		try (InputStream in = Minecraft.getInstance().getResourceManager().open(texture); NativeImage source = NativeImage.read(in)) {
			int height = Math.max(1, Math.round(width * source.getHeight() / (float) source.getWidth()));
			ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
			try {
				// nearest-neighbour scaling keeps pixel-art cursors crisp; GLFW wants R,G,B,A bytes (ABGR as a
				// little-endian int)
				for (int y = 0; y < height; y++)
					for (int x = 0; x < width; x++)
						pixels.putInt(ARGB.toABGR(source.getPixel(x * source.getWidth() / width, y * source.getHeight() / height)));
				pixels.flip();
				try (GLFWImage image = GLFWImage.malloc()) {
					image.set(width, height, pixels);
					return GLFW.glfwCreateCursor(image, Math.max(0, Math.min(hotX, width - 1)), Math.max(0, Math.min(hotY, height - 1)));
				}
			} finally {
				MemoryUtil.memFree(pixels);
			}
		} catch (Exception e) {
			org.slf4j.LoggerFactory.getLogger("AdvancedTitleScreens").warn("Could not load cursor {}", texture, e);
			return 0L;
		}
	}

	/** Is the mouse over something clickable (button, slider, link)? */
	private static boolean hovering(Screen screen, double mx, double my) {
		if (screen instanceof AdvancedTitleScreen title)
			return title.hoversClickable(mx, my);
		for (GuiEventListener child : screen.children())
			if (clickable(child, mx, my))
				return true;
		return false;
	}

	static boolean clickable(GuiEventListener child, double mx, double my) {
		return (child instanceof AbstractButton || child instanceof AbstractSliderButton) && child instanceof AbstractWidget widget && widget.visible && widget.active && widget.isMouseOver(mx, my);
	}
}