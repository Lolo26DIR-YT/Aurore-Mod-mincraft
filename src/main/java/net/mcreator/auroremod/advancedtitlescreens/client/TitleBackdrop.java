package net.mcreator.auroremod.advancedtitlescreens.client;

import net.minecraft.util.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Advanced Title Screens: the animated title screen background (panorama, image, slideshow, video, color...) plus
 * its effects and particles. Shared by the title screen and by the pause/options/mods menus that use the title
 * background.
 */
public class TitleBackdrop {
	private TitleScreenDef def;
	private TitleParticles particles;
	private final List<Identifier> slideOrder = new ArrayList<>();
	private long openedAtMs = -1;
	private long lastFrameMs = -1;
	private float panoramaSpin;

	public TitleBackdrop(TitleScreenDef def) {
		setDef(def);
	}

	/** Picks up a (possibly rebuilt) definition, e.g. after a procedure changed the background or particles. */
	public void setDef(TitleScreenDef def) {
		boolean slidesChanged = this.def == null || !this.def.slideshowImages.equals(def.slideshowImages);
		this.def = def;
		this.particles = new TitleParticles(def.particleType, def.particleColor, def.particleCount, def.particleSpeed, def.particleSize, def.particleTexture);
		if (slidesChanged) {
			slideOrder.clear();
			slideOrder.addAll(def.slideshowImages);
			if (def.slideshowShuffle)
				Collections.shuffle(slideOrder, new java.util.Random(RandomSource.create().nextLong()));
		}
	}

	/** When the backdrop was first drawn (animations such as the slideshow count from here). */
	public long openedAtMs() {
		if (openedAtMs < 0)
			openedAtMs = Util.getMillis();
		return openedAtMs;
	}

	/** Background, blur, overlay, vignette and particles, exactly as set up on the Background/Effects tabs. */
	public void renderAll(GuiGraphicsExtractor g, int width, int height, int mouseX, int mouseY, float partialTick) {
		renderBackground(g, width, height, mouseX, mouseY, partialTick);
		if (def.blurBackground)
			blur(g);
		if (def.overlayOpacity > 0)
			g.fill(0, 0, width, height, TitleRender.argb(def.overlayColor, def.overlayOpacity / 100F));
		if (def.vignette)
			TitleRender.drawVignette(g, width, height, def.vignetteStrength / 100F);
		renderParticles(g, width, height);
	}

	public void renderParticles(GuiGraphicsExtractor g, int width, int height) {
		particles.render(g, width, height, 1F, Util.getMillis());
	}

	/**
	 * Background of the pause/options/mods menu {@code m}. KEEP draws nothing (the vanilla background is already
	 * there), TITLE draws this backdrop, IMAGE/COLOR/GRADIENT the menu's own. Then the menu's effects.
	 */
	public void renderMenu(GuiGraphicsExtractor g, TitleScreenDef.MenuDef m, int width, int height, int mouseX, int mouseY, float partialTick) {
		String type = m.background == null ? "KEEP" : m.background;
		switch (type) {
			case "TITLE" -> renderAll(g, width, height, mouseX, mouseY, partialTick);
			case "IMAGE" -> {
				g.fill(0, 0, width, height, TitleRender.argb(m.color, 1F));
				if (m.image != null) {
					int[] size = TitleRender.textureSize(m.image);
					TitleRender.drawScaled(g, m.image, size[0], size[1], m.imageScaling, 0, 0, width, height, 1F, 1F, 0F, 0F);
				}
			}
			case "COLOR" -> g.fill(0, 0, width, height, TitleRender.argb(m.color, 1F));
			case "GRADIENT" -> g.fillGradient(0, 0, width, height, TitleRender.argb(m.gradientTopColor, 1F), TitleRender.argb(m.gradientBottomColor, 1F));
			default -> {
			}
		}
		if (m.blur && !"KEEP".equals(type))
			blur(g);
		if (m.overlayOpacity > 0)
			g.fill(0, 0, width, height, TitleRender.argb(m.overlayColor, m.overlayOpacity / 100F));
		if (m.vignette)
			TitleRender.drawVignette(g, width, height, m.vignetteStrength / 100F);
		if (m.particles && !"TITLE".equals(type))
			renderParticles(g, width, height);
	}

	/**
	 * Blurs everything drawn so far (Minecraft 26.1 blurs whole GUI strata, once per frame: when the vanilla menu
	 * background already blurred this frame, that blur is kept and this does nothing).
	 */
	public static void blur(GuiGraphicsExtractor g) {
		g.nextStratum();
		try {
			g.blurBeforeThisStratum();
		} catch (IllegalStateException alreadyBlurred) {
			// only one blur per frame
		}
	}

	/** Just the background itself (no blur/overlay/vignette/particles). */
	public void renderBackground(GuiGraphicsExtractor g, int width, int height, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		long opened = openedAtMs();
		float dt = lastFrameMs < 0 ? 0F : Math.min(0.1F, (now - lastFrameMs) / 1000F);
		lastFrameMs = now;
		float panX = 0F, panY = 0F, zoom = 1F;
		if (def.parallax && def.parallaxStrength > 0) {
			float strength = def.parallaxStrength;
			panX = -((mouseX - width / 2F) / Math.max(1F, width / 2F)) * strength;
			panY = -((mouseY - height / 2F) / Math.max(1F, height / 2F)) * strength;
			zoom = 1F + (strength * 2.2F) / Math.max(1F, Math.min(width, height));
		}
		switch (def.backgroundType == null ? "VANILLA_PANORAMA" : def.backgroundType) {
			case "PANORAMA" -> {
				panoramaSpin = (float) ((panoramaSpin + dt * def.panoramaSpeed) % 360.0);
				if (def.panoramaDistance > 0.01)
					fillColor(g, width, height);
				TitleRender.renderCube(g, width, height, def.panoramaFaces, (float) def.panoramaPitch, -panoramaSpin, 1F, (float) def.panoramaDistance, (float) def.panoramaFov);
				if (def.panoramaOverlay)
					g.blit(RenderPipelines.GUI_TEXTURED, Panorama.PANORAMA_OVERLAY, 0, 0, 0.0F, 0.0F, width, height, 16, 128, 16, 128);
			}
			case "IMAGE" -> {
				if (!drawImage(g, def.backgroundImage, width, height, zoom * kenBurnsZoom(now - opened, 20000), panX, panY))
					fillColor(g, width, height);
			}
			case "SLIDESHOW" -> renderSlideshow(g, width, height, now - opened, zoom, panX, panY);
			case "VIDEO" -> {
				TitleVideoPlayer player = def.video == null || def.video.isBlank() ? null : TitleVideoPlayer.get(def.video);
				boolean drawn = false;
				if (player != null && player.isUsable()) {
					if (!def.videoLoop && player.hasEnded() && "IMAGE".equals(def.videoEndBehavior))
						drawn = drawImage(g, def.backgroundImage, width, height, zoom, panX, panY);
					else
						drawn = player.render(g, 0, 0, width, height, def.imageScaling, 1F, zoom, panX, panY, def.videoLoop);
				}
				if (!drawn && !drawImage(g, def.backgroundImage, width, height, zoom, panX, panY))
					fillColor(g, width, height);
			}
			case "COLOR" -> fillColor(g, width, height);
			case "GRADIENT" -> g.fillGradient(0, 0, width, height, TitleRender.argb(def.gradientTopColor, 1F), TitleRender.argb(def.gradientBottomColor, 1F));
			default -> Minecraft.getInstance().gameRenderer.getPanorama().extractRenderState(g, width, height, true);
		}
	}

	private void fillColor(GuiGraphicsExtractor g, int width, int height) {
		g.fill(0, 0, width, height, TitleRender.argb(def.backgroundColor, 1F));
	}

	private boolean drawImage(GuiGraphicsExtractor g, @Nullable Identifier texture, int width, int height, float zoom, float panX, float panY) {
		if (texture == null)
			return false;
		if ("CONTAIN".equals(def.imageScaling) || "CENTER".equals(def.imageScaling))
			fillColor(g, width, height);
		int[] size = TitleRender.textureSize(texture);
		TitleRender.drawScaled(g, texture, size[0], size[1], def.imageScaling, 0, 0, width, height, 1F, zoom, panX, panY);
		return true;
	}

	private float kenBurnsZoom(long elapsedMs, long periodMs) {
		if (!def.kenBurns)
			return 1F;
		float t = (elapsedMs % Math.max(1L, periodMs)) / (float) periodMs;
		// a slow zoom in, then back out, so a single image never "jumps"
		return 1F + 0.08F * (float) (0.5 - 0.5 * Math.cos(t * Math.PI * 2));
	}

	private void renderSlideshow(GuiGraphicsExtractor g, int width, int height, long elapsed, float zoom, float panX, float panY) {
		if (slideOrder.isEmpty()) {
			fillColor(g, width, height);
			return;
		}
		long duration = Math.max(500L, def.slideDurationMs);
		long fade = Math.min(Math.max(0L, def.slideFadeMs), duration);
		int index = (int) ((elapsed / duration) % slideOrder.size());
		long inSlide = elapsed % duration;
		Identifier current = slideOrder.get(index);
		Identifier next = slideOrder.get((index + 1) % slideOrder.size());
		float currentZoom = def.kenBurns ? 1F + 0.1F * (inSlide / (float) duration) : 1F;
		float drift = def.kenBurns ? (index % 2 == 0 ? 1F : -1F) * 10F * (inSlide / (float) duration) : 0F;
		if ("CONTAIN".equals(def.imageScaling) || "CENTER".equals(def.imageScaling))
			fillColor(g, width, height);
		int[] size = TitleRender.textureSize(current);
		TitleRender.drawScaled(g, current, size[0], size[1], def.imageScaling, 0, 0, width, height, 1F, zoom * currentZoom, panX + drift, panY);
		if (fade > 0 && inSlide > duration - fade && slideOrder.size() > 1) {
			float t = (inSlide - (duration - fade)) / (float) fade;
			int[] nextSize = TitleRender.textureSize(next);
			TitleRender.drawScaled(g, next, nextSize[0], nextSize[1], def.imageScaling, 0, 0, width, height, t, zoom, panX, panY);
		}
	}
}