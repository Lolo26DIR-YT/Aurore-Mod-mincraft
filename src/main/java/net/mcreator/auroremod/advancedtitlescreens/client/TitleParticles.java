package net.mcreator.auroremod.advancedtitlescreens.client;

import net.minecraft.util.RandomSource;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import javax.annotation.Nullable;

/** Advanced Title Screens: lightweight 2D ambient particles (snow, rain, embers, dust, fireflies, bubbles, stars). */
public final class TitleParticles {
	private final String type;
	private final int color;
	private final float speed;
	private final int size;
	@Nullable
	private final Identifier texture;
	private final RandomSource random = RandomSource.create();
	private final float[] x, y, vx, vy, scale, phase;
	private int width, height;
	private long lastMs;

	public TitleParticles(String type, int color, int count, double speed, int size, @Nullable Identifier texture) {
		this.type = type == null ? "NONE" : type;
		this.color = color;
		this.speed = (float) Math.max(0.05, speed);
		this.size = Math.max(1, size);
		this.texture = texture;
		int n = "NONE".equals(this.type) ? 0 : Math.max(0, Math.min(count, 1000));
		x = new float[n];
		y = new float[n];
		vx = new float[n];
		vy = new float[n];
		scale = new float[n];
		phase = new float[n];
	}

	private void spawn(int i, boolean anywhere) {
		scale[i] = 0.6F + random.nextFloat() * 0.8F;
		phase[i] = random.nextFloat() * 6.2831855F;
		x[i] = random.nextFloat() * width;
		y[i] = random.nextFloat() * height;
		switch (type) {
			case "SNOW" -> {
				vx[i] = (random.nextFloat() - 0.5F) * 6F;
				vy[i] = 10F + random.nextFloat() * 14F;
				if (!anywhere)
					y[i] = -8F;
			}
			case "RAIN" -> {
				vx[i] = -18F;
				vy[i] = 190F + random.nextFloat() * 80F;
				if (!anywhere)
					y[i] = -16F;
			}
			case "EMBERS" -> {
				vx[i] = (random.nextFloat() - 0.5F) * 14F;
				vy[i] = -(14F + random.nextFloat() * 26F);
				if (!anywhere)
					y[i] = height + 6F;
			}
			case "BUBBLES" -> {
				vx[i] = 0F;
				vy[i] = -(10F + random.nextFloat() * 18F);
				if (!anywhere)
					y[i] = height + 6F;
			}
			case "DUST", "FIREFLIES" -> {
				vx[i] = (random.nextFloat() - 0.5F) * 10F;
				vy[i] = (random.nextFloat() - 0.5F) * 10F;
			}
			default -> { // STARS - stationary
				vx[i] = 0F;
				vy[i] = 0F;
			}
		}
	}

	public void render(GuiGraphicsExtractor g, int screenWidth, int screenHeight, float alpha, long nowMs) {
		if (x.length == 0 || alpha <= 0.01F)
			return;
		if (width != screenWidth || height != screenHeight) {
			width = screenWidth;
			height = screenHeight;
			for (int i = 0; i < x.length; i++)
				spawn(i, true);
			lastMs = nowMs;
		}
		float dt = Math.min(0.1F, (nowMs - lastMs) / 1000F) * speed;
		lastMs = nowMs;
		float t = nowMs / 1000F;
		for (int i = 0; i < x.length; i++) {
			switch (type) {
				case "SNOW" -> x[i] += (float) Math.sin(t * 1.3F + phase[i]) * 8F * dt;
				case "EMBERS" -> vx[i] += (random.nextFloat() - 0.5F) * 30F * dt;
				case "BUBBLES" -> x[i] += (float) Math.sin(t * 2F + phase[i]) * 10F * dt;
				case "DUST", "FIREFLIES" -> {
					vx[i] += (random.nextFloat() - 0.5F) * 12F * dt;
					vy[i] += (random.nextFloat() - 0.5F) * 12F * dt;
					vx[i] = Math.max(-12F, Math.min(12F, vx[i]));
					vy[i] = Math.max(-12F, Math.min(12F, vy[i]));
				}
				default -> {
				}
			}
			x[i] += vx[i] * dt;
			y[i] += vy[i] * dt;
			boolean out = y[i] > height + 20 || y[i] < -20 || x[i] < -20 || x[i] > width + 20;
			if (out) {
				if ("DUST".equals(type) || "FIREFLIES".equals(type)) {
					x[i] = Math.floorMod((int) x[i], Math.max(1, width));
					y[i] = Math.floorMod((int) y[i], Math.max(1, height));
				} else {
					spawn(i, false);
				}
			}
			float a = (switch (type) {
				case "EMBERS" -> 0.55F + 0.45F * (float) Math.sin(t * 9F + phase[i]);
				case "DUST" -> 0.35F;
				case "FIREFLIES", "STARS" -> 0.25F + 0.75F * Math.max(0F, (float) Math.sin(t * 2.2F + phase[i]));
				case "RAIN" -> 0.55F;
				default -> 0.85F;
			}) * alpha;
			if (a <= 0.01F)
				continue;
			int s = Math.max(1, Math.round(size * scale[i]));
			int px = Math.round(x[i]), py = Math.round(y[i]);
			if (texture != null) {
				TitleRender.blitStretched(g, texture, px, py, s * 2, s * 2, a);
			} else if ("RAIN".equals(type)) {
				g.fill(px, py, px + 1, py + s * 4, TitleRender.argb(color, a));
			} else {
				if ("FIREFLIES".equals(type))
					g.fill(px - s, py - s, px + s * 2, py + s * 2, TitleRender.argb(color, a * 0.25F));
				g.fill(px, py, px + s, py + s, TitleRender.argb(color, a));
			}
		}
	}
}