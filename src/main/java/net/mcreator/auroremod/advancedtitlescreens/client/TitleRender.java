package net.mcreator.auroremod.advancedtitlescreens.client;

import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2f;

import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

import java.io.InputStream;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * Advanced Title Screens: drawing helpers shared by the title screen, its buttons and backgrounds.
 * (Minecraft 26.1: GUI drawing is collected as render states - 2D poses, pipelines, no immediate 3D.)
 */
public final class TitleRender {
	private static final Map<Identifier, int[]> TEXTURE_SIZES = new HashMap<>();
	@Nullable
	private static Identifier vignetteTexture;

	private TitleRender() {
	}

	// ------------------------------------------------------------------ colors
	/** Packs 0xRRGGBB + alpha (0..1) into 0xAARRGGBB. */
	public static int argb(int rgb, float alpha) {
		int a = Math.round(Math.max(0F, Math.min(1F, alpha)) * 255F);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	public static int lerpColor(int from, int to, float t) {
		t = Math.max(0F, Math.min(1F, t));
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}

	public static float clamp01(float v) {
		return v < 0F ? 0F : Math.min(v, 1F);
	}

	public static float easeOutCubic(float t) {
		float f = 1F - clamp01(t);
		return 1F - f * f * f;
	}

	public static float easeOutBack(float t) {
		t = clamp01(t);
		float c1 = 1.70158F;
		float c3 = c1 + 1F;
		return 1F + c3 * (float) Math.pow(t - 1F, 3) + c1 * (float) Math.pow(t - 1F, 2);
	}

	// ------------------------------------------------------------------ placement
	public static int resolveX(String anchor, int x, int w, int screenW) {
		String a = anchor == null ? "CENTER" : anchor;
		if (a.endsWith("LEFT"))
			return x;
		if (a.endsWith("RIGHT"))
			return screenW - w + x;
		return screenW / 2 - w / 2 + x;
	}

	public static int resolveY(String anchor, int y, int h, int screenH) {
		String a = anchor == null ? "CENTER" : anchor;
		if (a.startsWith("TOP"))
			return y;
		if (a.startsWith("BOTTOM"))
			return screenH - h + y;
		return screenH / 2 - h / 2 + y;
	}

	// ------------------------------------------------------------------ textures
	/** Real pixel size of a texture resource, read once from its PNG data and cached. */
	public static int[] textureSize(Identifier texture) {
		return TEXTURE_SIZES.computeIfAbsent(texture, rl -> {
			try {
				Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(rl);
				if (resource.isPresent()) {
					try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
						return new int[]{image.getWidth(), image.getHeight()};
					}
				}
			} catch (Exception ignored) {
			}
			return new int[]{256, 256};
		});
	}

	/** Draws the whole texture stretched into the given rectangle. */
	public static void blitStretched(GuiGraphicsExtractor g, Identifier texture, float x, float y, float w, float h, float alpha) {
		blitTinted(g, texture, x, y, w, h, 0xFFFFFF, alpha);
	}

	/** Like blitStretched, multiplying the texture by an 0xRRGGBB tint. */
	public static void blitTinted(GuiGraphicsExtractor g, Identifier texture, float x, float y, float w, float h, int rgb, float alpha) {
		if (alpha <= 0.004F || w <= 0 || h <= 0)
			return;
		// a 1024x1024 blit scaled down keeps sub-pixel positions and sizes (blit itself takes ints)
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(w / 1024F, h / 1024F);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0F, 0F, 1024, 1024, 1, 1, 1, 1, argb(rgb, alpha));
		g.pose().popMatrix();
	}

	/**
	 * Draws a texture into an area with one of the image scaling modes (COVER, CONTAIN, STRETCH, TILE, CENTER).
	 * {@code zoom} enlarges it around the area's center (used by Ken Burns / parallax) and {@code panX/panY}
	 * shift it in GUI pixels.
	 */
	public static void drawScaled(GuiGraphicsExtractor g, Identifier texture, int texW, int texH, String mode, int x, int y, int w, int h, float alpha, float zoom, float panX, float panY) {
		if (alpha <= 0.004F)
			return;
		switch (mode == null ? "COVER" : mode) {
			case "STRETCH" -> blitStretched(g, texture, x - (w * (zoom - 1F)) / 2F + panX, y - (h * (zoom - 1F)) / 2F + panY, w * zoom, h * zoom, alpha);
			case "CONTAIN" -> {
				float scale = Math.min(w / (float) texW, h / (float) texH) * zoom;
				float dw = texW * scale, dh = texH * scale;
				blitStretched(g, texture, x + (w - dw) / 2F + panX, y + (h - dh) / 2F + panY, dw, dh, alpha);
			}
			case "CENTER" -> blitStretched(g, texture, x + (w - texW * zoom) / 2F + panX, y + (h - texH * zoom) / 2F + panY, texW * zoom, texH * zoom, alpha);
			case "TILE" -> {
				float tw = Math.max(1F, texW * zoom), th = Math.max(1F, texH * zoom);
				float startX = x + (panX % tw) - tw, startY = y + (panY % th) - th;
				for (float tx = startX; tx < x + w; tx += tw)
					for (float ty = startY; ty < y + h; ty += th)
						blitStretched(g, texture, tx, ty, tw, th, alpha);
			}
			default -> { // COVER
				float scale = Math.max(w / (float) texW, h / (float) texH) * zoom;
				float dw = texW * scale, dh = texH * scale;
				blitStretched(g, texture, x + (w - dw) / 2F + panX, y + (h - dh) / 2F + panY, dw, dh, alpha);
			}
		}
	}

	/** Registers a generated image as a texture (once). */
	public static Identifier dynamicTexture(String path, NativeImage image) {
		Identifier id = Identifier.fromNamespaceAndPath("aurore_mod", path);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ats " + path, image));
		return id;
	}

	/** A soft black radial gradient generated once and stretched over the screen. */
	public static void drawVignette(GuiGraphicsExtractor g, int w, int h, float strength) {
		if (strength <= 0.004F)
			return;
		if (vignetteTexture == null) {
			int size = 256;
			NativeImage image = new NativeImage(size, size, false);
			for (int px = 0; px < size; px++) {
				for (int py = 0; py < size; py++) {
					double dx = (px + 0.5) / size * 2.0 - 1.0;
					double dy = (py + 0.5) / size * 2.0 - 1.0;
					double d = Math.sqrt(dx * dx + dy * dy) / Math.sqrt(2.0);
					double v = Math.max(0.0, Math.min(1.0, (d - 0.3) / 0.7));
					int a = (int) Math.round(Math.pow(v, 1.6) * 255.0);
					image.setPixel(px, py, a << 24); // ARGB: black with alpha
				}
			}
			vignetteTexture = dynamicTexture("advancedtitlescreens/vignette", image);
		}
		blitStretched(g, vignetteTexture, 0, 0, w, h, clamp01(strength));
	}

	// ------------------------------------------------------------------ text
	/** Text as a component. "lang:some.key" becomes a translatable component (e.g. "lang:menu.singleplayer"). */
	public static MutableComponent styled(String text, @Nullable Identifier font) {
		String value = text == null ? "" : text;
		MutableComponent component = value.startsWith("lang:") ? Component.translatable(value.substring(5).trim()) : Component.literal(value);
		if (font != null)
			component = component.withStyle(style -> style.withFont(new FontDescription.Resource(font)));
		return component;
	}

	public static int textWidth(Font font, String text, @Nullable Identifier fontId) {
		return font.width(styled(text, fontId));
	}

	/** Draws text at a scale. {@code x}/{@code y} is the unscaled top-left. */
	public static void drawText(GuiGraphicsExtractor g, Font font, String text, @Nullable Identifier fontId, float x, float y, int rgb, float alpha, float scale, boolean shadow) {
		if (alpha < 0.02F || text == null || text.isEmpty())
			return;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, styled(text, fontId), 0, 0, argb(rgb, alpha), shadow);
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ motion animations
	/**
	 * Applies a looping motion animation (BOB, PULSE, SWAY, SPIN, FLOAT, BLINK) to the pose stack, pivoting on
	 * ({@code cx},{@code cy}). Returns the alpha multiplier the animation wants (only BLINK changes it).
	 */
	public static float applyMotion(Matrix3x2fStack pose, String animation, double speed, float cx, float cy) {
		if (animation == null || "NONE".equals(animation))
			return 1F;
		double t = (net.minecraft.util.Util.getMillis() / 1000.0) * Math.max(0.01, speed);
		pose.translate(cx, cy);
		float alpha = 1F;
		switch (animation) {
			case "BOB" -> pose.translate(0F, (float) (Math.sin(t * Math.PI) * 3.0));
			case "PULSE" -> {
				float s = (float) (1.0 + Math.sin(t * Math.PI * 1.5) * 0.04);
				pose.scale(s, s);
			}
			case "SWAY" -> pose.rotate((float) Math.toRadians(Math.sin(t * Math.PI) * 3.0));
			case "SPIN" -> pose.rotate((float) Math.toRadians((t * 45.0) % 360.0));
			case "FLOAT" -> pose.translate((float) (Math.sin(t * 0.7 * Math.PI) * 4.0), (float) (Math.cos(t * 0.5 * Math.PI) * 5.0));
			case "BLINK" -> alpha = (float) (0.55 + 0.45 * Math.sin(t * Math.PI * 1.5));
			default -> {
			}
		}
		pose.translate(-cx, -cy);
		return alpha;
	}

	// ------------------------------------------------------------------ meshes (3D drawn as 2D quads)
	/**
	 * Quads with their own vertices (x, y, u, v and color per vertex, 4 per quad), submitted as one GUI element -
	 * how 3D shapes are drawn now that GUI rendering is 2D render states: the vertices are projected on the CPU.
	 */
	private record QuadMesh(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float[] xyuv, int[] colors, boolean textured, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer consumer) {
			for (int i = 0; i < colors.length; i++) {
				VertexConsumer vertex = consumer.addVertexWith2DPose(pose, xyuv[i * 4], xyuv[i * 4 + 1]);
				if (textured)
					vertex.setUv(xyuv[i * 4 + 2], xyuv[i * 4 + 3]);
				vertex.setColor(colors[i]);
			}
		}
	}

	/** Collects quads for one texture (or plain color when {@code texture} is null) and submits them. */
	public static final class MeshBuilder {
		private final float[] data;
		private final int[] colors;
		private int count;
		@Nullable
		private final Identifier texture;

		public MeshBuilder(@Nullable Identifier texture, int maxQuads) {
			this.texture = texture;
			this.data = new float[maxQuads * 16];
			this.colors = new int[maxQuads * 4];
		}

		/** One quad from 4 corners (screen x, y, texture u, v); winding is fixed up for back-face culling. */
		public void quad(float[] x, float[] y, float[] u, float[] v, int[] argb) {
			if (count * 4 + 4 > colors.length)
				return;
			// GUI pipelines cull back faces: keep the same (negative) screen-space winding as vanilla's blits
			float area = 0F;
			for (int i = 0; i < 4; i++)
				area += x[i] * y[(i + 1) % 4] - x[(i + 1) % 4] * y[i];
			for (int k = 0; k < 4; k++) {
				int i = area > 0F ? 3 - k : k;
				int at = count * 4 + k;
				data[at * 4] = x[i];
				data[at * 4 + 1] = y[i];
				data[at * 4 + 2] = u[i];
				data[at * 4 + 3] = v[i];
				colors[at] = argb[i];
			}
			count++;
		}

		public void submit(GuiGraphicsExtractor g) {
			if (count == 0)
				return;
			int n = count * 4;
			float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				minX = Math.min(minX, data[i * 4]);
				maxX = Math.max(maxX, data[i * 4]);
				minY = Math.min(minY, data[i * 4 + 1]);
				maxY = Math.max(maxY, data[i * 4 + 1]);
			}
			Matrix3x2f pose = new Matrix3x2f(g.pose());
			ScreenRectangle scissor = g.peekScissorStack();
			ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY), Math.max(1, (int) Math.ceil(maxX - minX)), Math.max(1, (int) Math.ceil(maxY - minY))).transformMaxBounds(pose);
			if (scissor != null)
				bounds = scissor.intersection(bounds);
			float[] xyuv = java.util.Arrays.copyOf(data, n * 4);
			int[] argb = java.util.Arrays.copyOf(colors, n);
			if (texture != null) {
				AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
				g.submitGuiElementRenderState(new QuadMesh(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler()), pose, xyuv, argb, true, scissor, bounds));
			} else {
				g.submitGuiElementRenderState(new QuadMesh(RenderPipelines.GUI, TextureSetup.noTexture(), pose, xyuv, argb, false, scissor, bounds));
			}
		}
	}

	// ------------------------------------------------------------------ panorama
	/** Each panorama face's 4 corners (x, y, z, u, v), in vanilla CubeMap order. */
	private static final float[][][] PANORAMA_FACES = {{{-1, -1, 1, 0, 0}, {-1, 1, 1, 0, 1}, {1, 1, 1, 1, 1}, {1, -1, 1, 1, 0}}, {{1, -1, 1, 0, 0}, {1, 1, 1, 0, 1}, {1, 1, -1, 1, 1}, {1, -1, -1, 1, 0}},
			{{1, -1, -1, 0, 0}, {1, 1, -1, 0, 1}, {-1, 1, -1, 1, 1}, {-1, -1, -1, 1, 0}}, {{-1, -1, -1, 0, 0}, {-1, 1, -1, 0, 1}, {-1, 1, 1, 1, 1}, {-1, -1, 1, 1, 0}}, {{-1, -1, -1, 0, 0}, {-1, -1, 1, 0, 1}, {1, -1, 1, 1, 1}, {1, -1, -1, 1, 0}},
			{{-1, 1, 1, 0, 0}, {-1, 1, -1, 0, 1}, {1, 1, -1, 1, 1}, {1, 1, 1, 1, 0}}};
	/** Grid cells per face edge: fine enough that affine texture mapping looks perspective-correct. */
	private static final int PANORAMA_GRID = 16;

	/**
	 * Renders a six-faced panorama cube over a {@code width} x {@code height} GUI area. With {@code distance} 0 the
	 * camera is at the center looking out (like vanilla's panorama); with a positive distance the cube is seen from
	 * OUTSIDE as a huge rotating block (edge length 2). The cube is projected on the CPU and each face drawn as a
	 * fine grid of textured quads, far faces first.
	 */
	public static void renderCube(GuiGraphicsExtractor g, int width, int height, Identifier[] faces, float pitch, float yaw, float alpha, float distance, float fovDegrees) {
		boolean outside = distance > 0.01F;
		float fov = fovDegrees > 1F ? (float) Math.toRadians(Math.min(170F, fovDegrees)) : 1.4835298F;
		float focal = (height / 2F) / (float) Math.tan(fov / 2F);
		Matrix4f view = new Matrix4f().rotationX((float) Math.PI).translate(0F, 0F, outside ? distance : 0F).rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
		Integer[] order = {0, 1, 2, 3, 4, 5};
		if (outside) {
			float[] depth = new float[6];
			for (int k = 0; k < 6; k++) {
				Vector3f c = view.transformPosition(new Vector3f(FACE_CENTERS[k][0], FACE_CENTERS[k][1], FACE_CENTERS[k][2]));
				depth[k] = c.lengthSquared();
			}
			java.util.Arrays.sort(order, (a, b) -> Float.compare(depth[b], depth[a]));
		}
		int color = argb(0xFFFFFF, alpha);
		int[] colors = {color, color, color, color};
		int n = PANORAMA_GRID;
		for (int k : order) {
			if (faces[k] == null)
				continue;
			float[][] corners = PANORAMA_FACES[k];
			// grid of projected points; NaN where behind the camera
			float[][] sx = new float[n + 1][n + 1], sy = new float[n + 1][n + 1];
			for (int i = 0; i <= n; i++) {
				for (int j = 0; j <= n; j++) {
					float s = i / (float) n, t = j / (float) n;
					float[] p = new float[3];
					for (int a = 0; a < 3; a++) {
						// bilinear between the corners: 0=(u0,v0) 1=(u0,v1) 2=(u1,v1) 3=(u1,v0)
						p[a] = (1 - s) * (1 - t) * corners[0][a] + (1 - s) * t * corners[1][a] + s * t * corners[2][a] + s * (1 - t) * corners[3][a];
					}
					Vector3f c = view.transformPosition(new Vector3f(p[0], p[1], p[2]));
					if (c.z > -0.05F) {
						sx[i][j] = Float.NaN;
						continue;
					}
					sx[i][j] = width / 2F + c.x / -c.z * focal;
					sy[i][j] = height / 2F - c.y / -c.z * focal;
				}
			}
			MeshBuilder mesh = new MeshBuilder(faces[k], n * n);
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					if (Float.isNaN(sx[i][j]) || Float.isNaN(sx[i + 1][j]) || Float.isNaN(sx[i][j + 1]) || Float.isNaN(sx[i + 1][j + 1]))
						continue;
					float u0 = i / (float) n, u1 = (i + 1) / (float) n, v0 = j / (float) n, v1 = (j + 1) / (float) n;
					mesh.quad(new float[]{sx[i][j], sx[i][j + 1], sx[i + 1][j + 1], sx[i + 1][j]}, new float[]{sy[i][j], sy[i][j + 1], sy[i + 1][j + 1], sy[i + 1][j]}, new float[]{u0, u0, u1, u1}, new float[]{v0, v1, v1, v0}, colors);
				}
			}
			mesh.submit(g);
		}
	}

	/** Center of each panorama face (same order as PANORAMA_FACES). */
	private static final float[][] FACE_CENTERS = {{0, 0, 1}, {1, 0, 0}, {0, 0, -1}, {-1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
}