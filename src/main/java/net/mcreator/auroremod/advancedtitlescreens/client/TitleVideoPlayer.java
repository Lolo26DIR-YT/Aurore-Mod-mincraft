package net.mcreator.auroremod.advancedtitlescreens.client;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import net.minecraft.util.Util;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;

import javax.annotation.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

import java.io.InputStream;

import java.awt.image.BufferedImage;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Advanced Title Screens: plays an imported video (a numbered JPEG/PNG frame sequence plus video.properties,
 * stored under assets/aurore_mod/advancedtitlescreens/videos/NAME/).
 *
 * <p>Frames are decoded a few frames ahead on a background thread and uploaded into ONE reused GPU texture on
 * the render thread, so memory use stays flat no matter how long the video is. Audio is a separate, normal
 * sound (the importer registers the video's audio track as a workspace sound to use as title music).</p>
 */
public final class TitleVideoPlayer {
	private static final Logger LOGGER = LoggerFactory.getLogger("AdvancedTitleScreens");
	private static final Map<String, TitleVideoPlayer> PLAYERS = new HashMap<>();
	private static final int PREFETCH = 8;
	private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Advanced Title Screens video decoder");
		thread.setDaemon(true);
		return thread;
	});
	private final String name;
	private final String folder;
	private boolean valid;
	private int frameCount;
	private double fps = 24;
	private String extension = "jpg";
	private final Map<Integer, NativeImage> ready = new ConcurrentHashMap<>();
	private final Set<Integer> pending = ConcurrentHashMap.newKeySet();
	@Nullable
	private DynamicTexture texture;
	private final Identifier textureId;
	private int textureWidth, textureHeight;
	private long startMs = -1;
	private volatile boolean failed;
	private volatile int currentTarget;
	private volatile boolean currentLoop = true;

	private TitleVideoPlayer(String name) {
		this.name = name;
		String safe = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
		this.folder = "advancedtitlescreens/videos/" + safe + "/";
		this.textureId = Identifier.fromNamespaceAndPath("aurore_mod", "advancedtitlescreens/video_frame/" + safe);
		loadMeta();
	}

	/** One shared player per video, so returning to the title screen resumes instead of reloading. */
	public static TitleVideoPlayer get(String name) {
		return PLAYERS.computeIfAbsent(name, TitleVideoPlayer::new);
	}

	private void loadMeta() {
		try (InputStream in = Minecraft.getInstance().getResourceManager().open(Identifier.fromNamespaceAndPath("aurore_mod", folder + "video.properties"))) {
			Properties properties = new Properties();
			properties.load(in);
			frameCount = Integer.parseInt(properties.getProperty("frames", "0").trim());
			fps = Double.parseDouble(properties.getProperty("fps", "24").trim());
			extension = properties.getProperty("format", "jpg").trim().toLowerCase(Locale.ROOT);
			valid = frameCount > 0 && fps > 0;
		} catch (Exception e) {
			LOGGER.warn("Title screen video '{}' could not be loaded: {}", name, e.toString());
			valid = false;
		}
	}

	public boolean isUsable() {
		return valid && !failed;
	}

	/** True once a non-looping video has shown its last frame. */
	public boolean hasEnded() {
		return startMs >= 0 && (Util.getMillis() - startMs) * fps / 1000.0 >= frameCount;
	}

	public void restart() {
		startMs = Util.getMillis();
	}

	/**
	 * Advances playback and draws the current frame. Returns false while nothing can be drawn yet (first frame
	 * still decoding, or the video failed) so the caller can draw a fallback.
	 */
	public boolean render(GuiGraphicsExtractor g, int x, int y, int w, int h, String scaling, float alpha, float zoom, float panX, float panY, boolean loop) {
		if (!isUsable())
			return false;
		long now = Util.getMillis();
		if (startMs < 0)
			startMs = now;
		int target = (int) ((now - startMs) * fps / 1000.0);
		if (loop)
			target = Math.floorMod(target, frameCount);
		else
			target = Math.min(target, frameCount - 1);
		currentTarget = target;
		currentLoop = loop;
		for (int i = 0; i < PREFETCH; i++)
			request(loop ? (target + i) % frameCount : Math.min(target + i, frameCount - 1));
		NativeImage frame = ready.remove(target);
		if (frame != null)
			upload(frame);
		discardStale(target, loop);
		if (texture == null)
			return false;
		TitleRender.drawScaled(g, textureId, textureWidth, textureHeight, scaling, x, y, w, h, alpha, zoom, panX, panY);
		return true;
	}

	private void upload(NativeImage frame) {
		if (texture == null || frame.getWidth() != textureWidth || frame.getHeight() != textureHeight) {
			if (texture != null)
				Minecraft.getInstance().getTextureManager().release(textureId);
			textureWidth = frame.getWidth();
			textureHeight = frame.getHeight();
			texture = new DynamicTexture(() -> "ats video " + name, frame);
			Minecraft.getInstance().getTextureManager().register(textureId, texture);
		} else {
			texture.setPixels(frame); // closes the previous frame's pixels
			texture.upload();
		}
	}

	private void discardStale(int target, boolean loop) {
		Iterator<Map.Entry<Integer, NativeImage>> it = ready.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, NativeImage> entry = it.next();
			int ahead = loop ? Math.floorMod(entry.getKey() - target, frameCount) : entry.getKey() - target;
			if (ahead < 0 || ahead > PREFETCH * 2) {
				it.remove();
				entry.getValue().close();
			}
		}
	}

	private void request(int index) {
		if (failed || ready.containsKey(index) || !pending.add(index))
			return;
		DECODER.execute(() -> {
			try {
				// Playback moved on while this was queued: skip it so a slow decoder catches up instead of lagging
				int ahead = currentLoop ? Math.floorMod(index - currentTarget, frameCount) : index - currentTarget;
				if (ahead < 0 || ahead > PREFETCH * 2)
					return;
				NativeImage image = decode(index);
				if (image != null) {
					NativeImage previous = ready.put(index, image);
					if (previous != null)
						previous.close();
				}
			} catch (Throwable t) {
				// ImageIO unavailable, missing frame, corrupt file... never crash the title screen over it
				if (!failed)
					LOGGER.warn("Title screen video '{}' failed at frame {}: {}", name, index, t.toString());
				failed = true;
			} finally {
				pending.remove(index);
			}
		});
	}

	@Nullable
	private NativeImage decode(int index) throws Exception {
		Identifier location = Identifier.fromNamespaceAndPath("aurore_mod", folder + String.format(Locale.ROOT, "frame_%05d.%s", index + 1, extension));
		try (InputStream in = Minecraft.getInstance().getResourceManager().open(location)) {
			if ("png".equals(extension))
				return NativeImage.read(in);
			BufferedImage decoded = ImageIO.read(in);
			if (decoded == null)
				return null;
			int w = decoded.getWidth(), h = decoded.getHeight();
			int[] argb = decoded.getRGB(0, 0, w, h, null, 0, w);
			NativeImage image = new NativeImage(w, h, false);
			for (int py = 0; py < h; py++) {
				int row = py * w;
				for (int px = 0; px < w; px++) {
					int c = argb[row + px];
					// forced opaque
					image.setPixel(px, py, 0xFF000000 | c); // setPixel takes ARGB (26.1)
				}
			}
			return image;
		}
	}
}