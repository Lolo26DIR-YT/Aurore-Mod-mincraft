package net.mcreator.auroremod.advancedtitlescreens.client;

import net.minecraft.util.Util;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

/** Advanced Title Screens: a fully styled title screen button (vanilla, textured, flat or text-only). */
public class TitleButtonWidget extends AbstractButton {
	private final TitleScreenDef.ButtonDef def;
	private final Runnable action;
	@Nullable
	private final Identifier fontId;
	private float hover;
	private long lastFrameMs = -1;
	private boolean wasMouseOver;
	private final long appearAtMs;
	private final String entrance;
	private final int entranceMs;
	/** Set by the screen every frame (intro fade). */
	public float screenAlpha = 1F;
	/** Shown instead of the designed label (e.g. "Disconnect" instead of "Save and Quit to Title"). */
	@Nullable
	public String labelOverride;
	/** Drawn by our own code (drawNow) instead of the screen's widget loop, so it can sit anywhere in the draw order. */
	public boolean manualRender;
	private boolean drawing;

	public TitleButtonWidget(TitleScreenDef.ButtonDef def, int x, int y, Runnable action, long appearAtMs, String entrance, int entranceMs) {
		super(x, y, Math.max(1, def.width), Math.max(1, def.height), TitleRender.styled(def.label, null));
		this.def = def;
		this.action = action;
		this.fontId = TitleScreenDef.rl(def.font);
		this.appearAtMs = appearAtMs;
		this.entrance = entrance == null ? "NONE" : entrance;
		this.entranceMs = Math.max(1, entranceMs);
		if (def.tooltip != null && !def.tooltip.isBlank())
			setTooltip(Tooltip.create(TitleRender.styled(def.tooltip, null)));
	}

	public TitleScreenDef.ButtonDef def() {
		return def;
	}

	public void drawNow(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		drawing = true;
		try {
			extractRenderState(g, mouseX, mouseY, partialTick);
		} finally {
			drawing = false;
		}
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
		if (def.clickSound != null)
			soundManager.play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(def.clickSound), 1.0F));
		else
			super.playDownSound(soundManager);
	}

	/** 0..1 progress of the entrance animation. */
	private float entranceProgress(long now) {
		if ("NONE".equals(entrance))
			return now >= appearAtMs ? 1F : 0F;
		return TitleRender.clamp01((now - appearAtMs) / (float) entranceMs);
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return entranceProgress(Util.getMillis()) >= 0.5F && super.isMouseOver(mouseX, mouseY);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (manualRender && !drawing)
			return;
		long now = Util.getMillis();
		float dt = lastFrameMs < 0 ? 0F : Math.min(0.1F, (now - lastFrameMs) / 1000F);
		lastFrameMs = now;
		float progress = entranceProgress(now);
		if (progress <= 0F)
			return;
		float eased = TitleRender.easeOutCubic(progress);
		boolean highlighted = this.active && this.isHoveredOrFocused();
		hover += ((highlighted ? 1F : 0F) - hover) * Math.min(1F, dt * 12F);
		if (!highlighted && hover < 0.01F)
			hover = 0F;
		boolean mouseOver = this.active && this.isHovered();
		if (mouseOver && !wasMouseOver && def.hoverSound != null)
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(def.hoverSound), 1.0F));
		wasMouseOver = mouseOver;
		float alpha = screenAlpha * this.alpha;
		float offX = 0F, offY = 0F, scale = 1F;
		switch (entrance) {
			case "FADE" -> alpha *= eased;
			case "SLIDE_LEFT" -> {
				alpha *= eased;
				offX = -(1F - eased) * 60F;
			}
			case "SLIDE_RIGHT" -> {
				alpha *= eased;
				offX = (1F - eased) * 60F;
			}
			case "SLIDE_UP" -> {
				alpha *= eased;
				offY = (1F - eased) * 30F;
			}
			case "SLIDE_DOWN" -> {
				alpha *= eased;
				offY = -(1F - eased) * 30F;
			}
			case "POP" -> {
				alpha *= TitleRender.clamp01(progress * 2F);
				scale = 0.6F + 0.4F * TitleRender.easeOutBack(progress);
			}
			default -> {
			}
		}
		switch (def.hoverEffect == null ? "NONE" : def.hoverEffect) {
			case "GROW" -> scale *= 1F + 0.07F * hover;
			case "SLIDE" -> offX += 6F * hover;
			case "LIFT" -> offY -= 2F * hover;
			default -> {
			}
		}
		if (alpha <= 0.01F)
			return;
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		g.pose().pushMatrix();
		g.pose().translate(x + w / 2F + offX, y + h / 2F + offY);
		g.pose().scale(scale, scale);
		g.pose().translate(-(x + w / 2F), -(y + h / 2F));
		renderBody(g, x, y, w, h, alpha);
		renderContents(g, x, y, w, h, alpha);
		g.pose().popMatrix();
	}

	private void renderBody(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
		switch (def.style == null ? "VANILLA" : def.style) {
			case "TEXTURE" -> {
				boolean useHover = hover > 0.5F && def.hoverTexture != null;
				Identifier tex = useHover ? def.hoverTexture : def.texture;
				int tint = def.hoverTexture == null ? TitleRender.lerpColor(def.textureTint, def.hoverTextureTint, hover) : useHover ? def.hoverTextureTint : def.textureTint;
				if (tex != null)
					TitleRender.blitTinted(g, tex, x, y, w, h, tint, this.active ? alpha : alpha * 0.5F);
			}
			case "FLAT" -> {
				int bg = TitleRender.lerpColor(def.backgroundColor, def.hoverBackgroundColor, hover);
				float bgAlpha = (def.backgroundOpacity + (def.hoverBackgroundOpacity - def.backgroundOpacity) * hover) / 100F;
				if (!this.active)
					bgAlpha *= 0.5F;
				g.fill(x, y, x + w, y + h, TitleRender.argb(bg, bgAlpha * alpha));
				if (def.borderThickness > 0) {
					int t = def.borderThickness;
					int border = TitleRender.argb(TitleRender.lerpColor(def.borderColor, def.hoverBorderColor, hover), alpha);
					g.fill(x, y, x + w, y + t, border);
					g.fill(x, y + h - t, x + w, y + h, border);
					g.fill(x, y + t, x + t, y + h - t, border);
					g.fill(x + w - t, y + t, x + w, y + h - t, border);
				}
			}
			case "TEXT" -> {
			}
			default -> { // VANILLA
				g.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES.get(this.active, this.isHoveredOrFocused()), x, y, w, h, TitleRender.argb(0xFFFFFF, alpha));
			}
		}
		if ("GLOW".equals(def.hoverEffect) && hover > 0.01F) {
			int glow = TitleRender.argb(def.hoverBorderColor, 0.6F * hover * alpha);
			int soft = TitleRender.argb(def.hoverBorderColor, 0.25F * hover * alpha);
			g.fill(x - 1, y - 1, x + w + 1, y, glow);
			g.fill(x - 1, y + h, x + w + 1, y + h + 1, glow);
			g.fill(x - 1, y, x, y + h, glow);
			g.fill(x + w, y, x + w + 1, y + h, glow);
			g.fill(x - 2, y - 2, x + w + 2, y - 1, soft);
			g.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, soft);
			g.fill(x - 2, y - 1, x - 1, y + h + 1, soft);
			g.fill(x + w + 1, y - 1, x + w + 2, y + h + 1, soft);
		}
	}

	private void renderContents(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
		Font font = Minecraft.getInstance().font;
		String label = TitlePlaceholders.resolve(labelOverride != null ? labelOverride : def.label);
		float textScale = (float) Math.max(0.1, def.textScale);
		int textW = label.isEmpty() ? 0 : Math.round(TitleRender.textWidth(font, label, fontId) * textScale);
		boolean hasVanillaIcon = def.vanillaIcon != null && !"NONE".equals(def.vanillaIcon);
		boolean hasIcon = hasVanillaIcon || def.icon != null;
		int iconSize = hasVanillaIcon ? 15 : Math.max(1, def.iconSize);
		if (hasIcon) {
			int iconX = label.isEmpty() ? x + (w - iconSize) / 2 : x + 4;
			int iconY = y + (h - iconSize) / 2;
			if (hasVanillaIcon) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("ACCESSIBILITY".equals(def.vanillaIcon) ? "icon/accessibility" : "icon/language"), iconX, iconY, iconSize, iconSize, TitleRender.argb(0xFFFFFF, alpha));
			} else {
				TitleRender.blitStretched(g, def.icon, iconX, iconY, iconSize, iconSize, alpha);
			}
		}
		if (label.isEmpty())
			return;
		int padLeft = hasIcon ? iconSize + 8 : 4;
		float textX = switch (def.textAlign == null ? "CENTER" : def.textAlign) {
			case "LEFT" -> x + padLeft;
			case "RIGHT" -> x + w - 4 - textW;
			default -> x + (w - textW) / 2F + (hasIcon ? iconSize / 2F : 0F);
		};
		float textY = y + (h - 8F * textScale) / 2F;
		int color = this.active ? TitleRender.lerpColor(def.textColor, def.hoverTextColor, hover) : 0xA0A0A0;
		TitleRender.drawText(g, font, label, fontId, textX, textY, color, alpha, textScale, def.textShadow);
		if (def.underlineOnHover && hover > 0.01F) {
			int lineY = Math.round(textY + 9F * textScale);
			int lineW = Math.round(textW * hover);
			g.fill(Math.round(textX), lineY, Math.round(textX) + lineW, lineY + 1, TitleRender.argb(color, alpha));
		}
	}
}