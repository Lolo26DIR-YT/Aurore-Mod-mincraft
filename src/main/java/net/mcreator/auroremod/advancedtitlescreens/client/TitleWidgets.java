package net.mcreator.auroremod.advancedtitlescreens.client;

import org.joml.Vector3f;
import org.joml.Quaternionf;

import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

import java.util.function.Supplier;
import java.util.Map;
import java.util.IdentityHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.authlib.GameProfile;

/** Advanced Title Screens: renders widgets (panel, player skin, 3D cube, server browser). */
public final class TitleWidgets {
	private static final Identifier UNKNOWN_SERVER = Identifier.withDefaultNamespace("textures/misc/unknown_server.png");
	@Nullable
	private static Identifier glowTexture;
	@Nullable
	private static GameProfile profile;
	@Nullable
	private static Supplier<PlayerSkin> skinLookup;
	/** Each 3D player widget has its own model: part visibility/rotation is read when the frame is drawn. */
	private static final Map<TitleScreenDef.WidgetDef, PlayerModel> MODELS = new IdentityHashMap<>();
	private static final Map<TitleScreenDef.WidgetDef, Boolean> MODEL_SLIM = new IdentityHashMap<>();
	/** Cube faces: 4 corners each (x, y, z in -0.5..0.5). */
	private static final float[][][] FACES = {{{-0.5F, -0.5F, -0.5F}, {0.5F, -0.5F, -0.5F}, {0.5F, 0.5F, -0.5F}, {-0.5F, 0.5F, -0.5F}}, {{0.5F, -0.5F, 0.5F}, {-0.5F, -0.5F, 0.5F}, {-0.5F, 0.5F, 0.5F}, {0.5F, 0.5F, 0.5F}},
			{{-0.5F, -0.5F, 0.5F}, {-0.5F, -0.5F, -0.5F}, {-0.5F, 0.5F, -0.5F}, {-0.5F, 0.5F, 0.5F}}, {{0.5F, -0.5F, -0.5F}, {0.5F, -0.5F, 0.5F}, {0.5F, 0.5F, 0.5F}, {0.5F, 0.5F, -0.5F}},
			{{-0.5F, -0.5F, 0.5F}, {0.5F, -0.5F, 0.5F}, {0.5F, -0.5F, -0.5F}, {-0.5F, -0.5F, -0.5F}}, {{-0.5F, 0.5F, -0.5F}, {0.5F, 0.5F, -0.5F}, {0.5F, 0.5F, 0.5F}, {-0.5F, 0.5F, 0.5F}}};
	private static final float[][] NORMALS = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
	private static final float[] CUBE_U = {0, 1, 1, 0}, CUBE_V = {0, 0, 1, 1};
	private static final Vector3f LIGHT = new Vector3f(-0.35F, -0.6F, -0.7F).normalize();

	private TitleWidgets() {
	}

	public static void render(GuiGraphicsExtractor g, TitleScreenDef.WidgetDef w, int x, int y, int width, int height, int mouseX, int mouseY, float fade, long nowMs) {
		float alpha = fade * w.opacity / 100F;
		if (alpha <= 0.01F)
			return;
		switch (w.kind == null ? "PANEL" : w.kind) {
			case "PLAYER" -> renderPlayer(g, w, x, y, alpha, mouseX, mouseY, nowMs);
			case "CUBE" -> renderCube(g, w, x, y, alpha, nowMs);
			case "SERVERS" -> renderServers(g, w, x, y, mouseX, mouseY, fade);
			default -> renderPanel(g, w, x, y, width, height, alpha, fade);
		}
	}

	// ------------------------------------------------------------------ panel
	private static void renderPanel(GuiGraphicsExtractor g, TitleScreenDef.WidgetDef w, int x, int y, int width, int height, float alpha, float fade) {
		if (w.texture != null) {
			TitleRender.blitTinted(g, w.texture, x, y, width, height, w.tint, alpha);
		} else {
			int from = TitleRender.argb(w.color, alpha);
			int to = TitleRender.argb(w.color2, fade * w.opacity2 / 100F);
			switch (w.gradient == null ? "NONE" : w.gradient) {
				case "HORIZONTAL" -> quad(g, x, y, x + width, y + height, from, to, to, from);
				case "VERTICAL" -> quad(g, x, y, x + width, y + height, from, from, to, to);
				default -> g.fill(x, y, x + width, y + height, from);
			}
		}
		if (w.borderThickness > 0) {
			int t = w.borderThickness, c = TitleRender.argb(w.borderColor, fade);
			g.fill(x, y, x + width, y + t, c);
			g.fill(x, y + height - t, x + width, y + height, c);
			g.fill(x, y + t, x + t, y + height - t, c);
			g.fill(x + width - t, y + t, x + width, y + height - t, c);
		}
	}

	/** A rectangle with a color per corner (top-left, top-right, bottom-right, bottom-left). */
	public static void quad(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int tl, int tr, int br, int bl) {
		TitleRender.MeshBuilder mesh = new TitleRender.MeshBuilder(null, 1);
		mesh.quad(new float[]{x1, x1, x2, x2}, new float[]{y1, y2, y2, y1}, new float[4], new float[4], new int[]{tl, bl, br, tr});
		mesh.submit(g);
	}

	// ------------------------------------------------------------------ player skin
	private static PlayerSkin currentSkin() {
		Minecraft mc = Minecraft.getInstance();
		if (skinLookup == null || profile != mc.getGameProfile()) {
			profile = mc.getGameProfile();
			skinLookup = mc.getSkinManager().createLookup(profile, false);
		}
		return skinLookup.get();
	}

	private static void renderPlayer(GuiGraphicsExtractor g, TitleScreenDef.WidgetDef w, int x, int y, float alpha, int mouseX, int mouseY, long nowMs) {
		PlayerSkin skin = currentSkin();
		Identifier tex = skin.body().texturePath();
		boolean slim = skin.model() == PlayerModelType.SLIM;
		String mode = w.skinMode == null || "MODEL".equals(w.skinMode) ? "FULL" : w.skinMode;
		if (w.model3d || "MODEL".equals(w.skinMode)) {
			renderPlayerModel(g, w, tex, slim, mode, x, y, mouseX, mouseY, nowMs);
			return;
		}
		int rows = "FACE".equals(mode) ? 8 : "BUST".equals(mode) ? 16 : 32;
		int cols = "FACE".equals(mode) ? 8 : 16;
		// fitted to the widget, or a fixed size so the widget is only a window onto the skin
		float unit = w.skinFit ? Math.min(w.width / (float) cols, w.height / (float) rows) : (float) w.skinScale;
		float centerX = x + w.width / 2F + w.skinOffsetX, centerY = y + w.height / 2F + w.skinOffsetY;
		if (w.skinClip)
			g.enableScissor(x, y, x + w.width, y + w.height);
		int color = TitleRender.argb(0xFFFFFF, alpha);
		g.pose().pushMatrix();
		g.pose().translate(centerX - cols * unit / 2F, centerY - rows * unit / 2F);
		g.pose().scale(unit, unit);
		if ("FACE".equals(mode)) {
			part(g, tex, 0, 0, 8, 8, 8, 8, color);
			part(g, tex, 0, 0, 8, 8, 40, 8, color);
		} else {
			int body = "BUST".equals(mode) ? 8 : 12;
			int arm = slim ? 3 : 4;
			part(g, tex, 4, 0, 8, 8, 8, 8, color); // head
			part(g, tex, 4, 0, 8, 8, 40, 8, color); // hat
			part(g, tex, 4, 8, 8, body, 20, 20, color); // body
			part(g, tex, 4, 8, 8, body, 20, 36, color); // jacket
			part(g, tex, 4 - arm, 8, arm, body, 44, 20, color); // right arm (viewer's left)
			part(g, tex, 4 - arm, 8, arm, body, 44, 36, color);
			part(g, tex, 12, 8, arm, body, 36, 52, color); // left arm
			part(g, tex, 12, 8, arm, body, 52, 52, color);
			if ("FULL".equals(mode)) {
				part(g, tex, 4, 20, 4, 12, 4, 20, color); // right leg
				part(g, tex, 4, 20, 4, 12, 4, 36, color);
				part(g, tex, 8, 20, 4, 12, 20, 52, color); // left leg
				part(g, tex, 8, 20, 4, 12, 4, 52, color);
			}
		}
		g.pose().popMatrix();
		if (w.skinClip)
			g.disableScissor();
	}

	/**
	 * Player model geometry in the GUI skin renderer, in blocks above the bottom of its rectangle: feet 0.101, chest
	 * 1.101, neck 1.601, head middle 1.851, hat top 2.132 (it lifts the model 1.601 and draws it at full size).
	 */
	private static final float MODEL_HEAD_CENTER = 1.851F;
	/** Height of the rectangle handed to the skin renderer, in blocks (room for the whole model). */
	private static final float MODEL_RECT_HEIGHT = 2.4F;

	/**
	 * The real 3D player model with the player's skin, drawn by Minecraft's GUI skin renderer (the one the skin
	 * customization screen uses). Framing, size, offset and clipping work like in 1.21.1; the model can't fade.
	 */
	private static void renderPlayerModel(GuiGraphicsExtractor g, TitleScreenDef.WidgetDef w, Identifier texture, boolean slim, String mode, int x, int y, int mouseX, int mouseY, long nowMs) {
		PlayerModel model = MODELS.get(w);
		if (model == null || MODEL_SLIM.get(w) != slim) {
			Minecraft mc = Minecraft.getInstance();
			model = new PlayerModel(mc.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
			MODELS.put(w, model);
			MODEL_SLIM.put(w, slim);
		}
		boolean face = "FACE".equals(mode), full = "FULL".equals(mode);
		// framing in blocks above the rectangle bottom: the part shown fills the widget when fitted
		float center = face ? MODEL_HEAD_CENTER : full ? 1.1165F : 1.6165F;
		float span = face ? 0.75F : full ? 2.094F : 1.063F;
		float fitFrame = full ? w.height : Math.min(w.width, w.height);
		// pixels per block; a fixed size matches the flat skin (16 skin pixels per block)
		float scale = w.skinFit ? fitFrame / span : (float) w.skinScale * 16F;
		float cx = x + w.width / 2F + w.skinOffsetX;
		float frameCenterY = y + w.height / 2F + w.skinOffsetY;
		float headY = frameCenterY - (MODEL_HEAD_CENTER - center) * scale;
		// vanilla's follow-mouse angles: atan(offset / 40)
		float lookX = w.followMouse ? (float) Math.atan((cx - mouseX) / 40F) : 0F;
		float lookY = w.followMouse ? (float) Math.atan((headY - mouseY) / 40F) : 0F;
		float turn = w.followMouse ? 0F : (float) (w.modelYaw + w.modelSpin * (nowMs / 1000.0));
		double t = nowMs / 1000.0 * 20.0; // ticks
		for (ModelPart part : new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg})
			part.resetPose();
		// the head alone turns around its own center through the whole-model rotation; otherwise the head turns more
		// than the body, like the inventory player
		float yaw = face ? lookX * 40F : lookX * 20F;
		if (!face) {
			model.head.yRot = (float) Math.toRadians(lookX * 20F);
			model.head.xRot = (float) Math.toRadians(-lookY * 20F);
		}
		// vanilla's idle arm sway
		model.rightArm.zRot += (float) (Math.cos(t * 0.09) * 0.05 + 0.05);
		model.rightArm.xRot += (float) (Math.sin(t * 0.067) * 0.05);
		model.leftArm.zRot -= (float) (Math.cos(t * 0.09) * 0.05 + 0.05);
		model.leftArm.xRot -= (float) (Math.sin(t * 0.067) * 0.05);
		// face: head only; head and shoulders: no legs (the rest is clipped at the chest)
		model.body.visible = !face;
		model.rightArm.visible = !face;
		model.leftArm.visible = !face;
		model.rightLeg.visible = full;
		model.leftLeg.visible = full;
		// the renderer puts the model's feet at the bottom middle of the rectangle; place the rectangle so the
		// framing center lands on the widget center (+ offset)
		float bottom = frameCenterY + center * scale;
		int x0 = Math.round(cx - 1.25F * scale), x1 = Math.round(cx + 1.25F * scale);
		int y1 = Math.round(bottom), y0 = Math.round(bottom - MODEL_RECT_HEIGHT * scale);
		if (x1 - x0 < 2 || y1 - y0 < 2)
			return;
		if (w.skinClip)
			g.enableScissor(x, y, x + w.width, y + w.height);
		// pivot for looking up/down: the framing center, measured in blocks down from the rectangle top
		float pivotY = -(MODEL_RECT_HEIGHT - center);
		// the skin renderer turns the model by -rotationY: pass the angle negated so the body turns the same way as
		// the head's own turn (and as in 1.21.1) instead of cancelling it out
		g.skin(model, texture, scale, face ? lookY * 20F : lookY * 10F, turn - yaw, pivotY, x0, y0, x1, y1);
		if (w.skinClip)
			g.disableScissor();
	}

	private static void part(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h, int u, int v, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, (float) u, (float) v, w, h, 64, 64, color);
	}

	// ------------------------------------------------------------------ 3D cube
	private static void renderCube(GuiGraphicsExtractor g, TitleScreenDef.WidgetDef w, int x, int y, float alpha, long nowMs) {
		float size = Math.min(w.width, w.height);
		float cx = x + w.width / 2F, cy = y + w.height / 2F;
		if (w.glowOpacity > 0) {
			float glow = size * w.glowSize / 100F;
			TitleRender.blitTinted(g, glowTexture(), cx - glow / 2F, cy - glow / 2F, glow, glow, w.glowColor, alpha * w.glowOpacity / 100F);
		}
		double t = nowMs / 1000.0;
		Quaternionf rotation = new Quaternionf().rotateX((float) Math.toRadians(w.tiltX + w.spinX * t)).rotateY((float) Math.toRadians(w.tiltY + w.spinY * t)).rotateZ((float) Math.toRadians(w.spinZ * t));
		// the cube's diagonal is sqrt(3) x its edge; scale so the spinning cube stays inside the widget box
		float scale = size / 1.732F;
		for (int f = 0; f < 6; f++) {
			Vector3f normal = rotation.transform(new Vector3f(NORMALS[f]));
			if (normal.z >= 0F)
				continue; // faces away from the viewer; visible faces of a cube never overlap, so no sorting
			float light = w.shading ? 0.45F + 0.55F * Math.max(0F, normal.dot(LIGHT)) : 1F;
			int r = Math.round(((w.color >> 16) & 0xFF) * light), gr = Math.round(((w.color >> 8) & 0xFF) * light), bl = Math.round((w.color & 0xFF) * light);
			int color = TitleRender.argb((r << 16) | (gr << 8) | bl, alpha);
			Identifier tex = f >= 4 && w.texture2 != null ? w.texture2 : w.texture;
			float[] px = new float[4], py = new float[4];
			for (int c = 0; c < 4; c++) {
				Vector3f p = rotation.transform(new Vector3f(FACES[f][c]));
				px[c] = cx + p.x * scale;
				py[c] = cy + p.y * scale;
			}
			// a parallel projection keeps faces affine, so one textured quad per face is exact
			TitleRender.MeshBuilder mesh = new TitleRender.MeshBuilder(tex, 1);
			mesh.quad(px, py, CUBE_U, CUBE_V, new int[]{color, color, color, color});
			mesh.submit(g);
		}
	}

	/** Soft white radial glow, generated once. */
	private static Identifier glowTexture() {
		if (glowTexture == null) {
			int size = 128;
			NativeImage image = new NativeImage(size, size, false);
			for (int px = 0; px < size; px++) {
				for (int py = 0; py < size; py++) {
					double dx = (px + 0.5) / size * 2 - 1, dy = (py + 0.5) / size * 2 - 1;
					double d = Math.min(1.0, Math.sqrt(dx * dx + dy * dy));
					int a = (int) Math.round(Math.pow(1.0 - d, 2.2) * 255);
					image.setPixel(px, py, (a << 24) | 0xFFFFFF);
				}
			}
			glowTexture = TitleRender.dynamicTexture("advancedtitlescreens/glow", image);
		}
		return glowTexture;
	}

	// ------------------------------------------------------------------ server browser
	/** Index of the server row under the mouse, or -1. */
	public static int serverRowAt(TitleScreenDef.WidgetDef w, int x, int y, double mouseX, double mouseY) {
		if (mouseX < x || mouseX >= x + w.width || mouseY < y || mouseY >= y + w.height)
			return -1;
		int row = (int) ((mouseY - y) / (w.rowHeight + 2));
		if (row < 0 || row >= w.servers.size() || (row + 1) * (w.rowHeight + 2) - 2 > w.height)
			return -1;
		return mouseY - y - row * (w.rowHeight + 2) < w.rowHeight ? row : -1;
	}

	private static void renderServers(GuiGraphicsExtractor g, TitleScreenDef.WidgetDef w, int x, int y, int mouseX, int mouseY, float fade) {
		Font font = Minecraft.getInstance().font;
		int hovered = serverRowAt(w, x, y, mouseX, mouseY);
		float rowAlpha = fade * w.opacity / 100F;
		for (int i = 0; i < w.servers.size(); i++) {
			int ry = y + i * (w.rowHeight + 2);
			if (ry + w.rowHeight > y + w.height)
				break;
			TitleScreenDef.ServerDef server = w.servers.get(i);
			TitleServerStatus.Entry status = TitleServerStatus.get(server.address);
			g.fill(x, ry, x + w.width, ry + w.rowHeight, TitleRender.argb(i == hovered ? w.hoverColor : w.color, i == hovered ? Math.max(rowAlpha, 0.6F * fade) : rowAlpha));
			int icon = w.rowHeight - 4;
			Identifier iconTex = server.icon != null ? server.icon : status.icon();
			TitleRender.blitStretched(g, iconTex != null ? iconTex : UNKNOWN_SERVER, x + 2, ry + 2, icon, icon, fade);
			int textX = x + icon + 6;
			TitleRender.drawText(g, font, server.name, null, textX, ry + 3, w.textColor, fade, 1F, true);
			String players = status.online() ? status.playersOnline() + "/" + status.playersMax() : "";
			int barsX = x + w.width - 13;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, pingSprite(status), barsX, ry + 3, 10, 8, fade);
			if (!players.isEmpty())
				TitleRender.drawText(g, font, players, null, barsX - 3 - font.width(players), ry + 3, w.secondaryColor, fade, 1F, false);
			if (w.showMotd && w.rowHeight >= 20) {
				g.enableScissor(textX, ry, x + w.width - 2, ry + w.rowHeight);
				TitleRender.drawText(g, font, status.motd(), null, textX, ry + 14, w.secondaryColor, fade, 1F, false);
				g.disableScissor();
			}
		}
	}

	private static Identifier pingSprite(TitleServerStatus.Entry status) {
		long millis = net.minecraft.util.Util.getMillis();
		if (status.pinging())
			return Identifier.withDefaultNamespace("server_list/pinging_" + (millis / 100L % 8L < 4 ? millis / 100L % 4L + 1 : 8 - millis / 100L % 8L));
		if (!status.online())
			return Identifier.withDefaultNamespace("server_list/unreachable");
		long ping = status.ping();
		int bars = ping < 0 ? 1 : ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
		return Identifier.withDefaultNamespace("server_list/ping_" + bars);
	}
}