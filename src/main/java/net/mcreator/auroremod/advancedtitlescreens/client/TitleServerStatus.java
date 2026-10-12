package net.mcreator.auroremod.advancedtitlescreens.client;

import net.minecraft.util.Util;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Advanced Title Screens: live status of servers (player count, MOTD, ping, favicon), shared by the server
 * browser widget and the %server_...% placeholders. Pings run off-thread like vanilla's server list and are
 * repeated every 30 seconds while something is showing them.
 */
public final class TitleServerStatus {
	private static final ServerStatusPinger PINGER = new ServerStatusPinger();
	private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
	private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
		Thread thread = new Thread(r, "Advanced Title Screens server pinger");
		thread.setDaemon(true);
		return thread;
	});
	private static final long REFRESH_MS = 30000;
	private static final long TIMEOUT_MS = 6000;

	private TitleServerStatus() {
	}

	public static final class Entry {
		private final String address;
		private volatile ServerData data;
		private volatile long pingStartedAt = -1;
		private volatile long lastAnswerAt = -1;
		@Nullable
		private Identifier iconTexture;
		@Nullable
		private byte[] iconBytes;

		Entry(String address) {
			this.address = address;
		}

		public boolean online() {
			return lastAnswerAt >= 0 && Util.getMillis() - lastAnswerAt < REFRESH_MS + TIMEOUT_MS && data != null && data.players != null;
		}

		public boolean pinging() {
			return lastAnswerAt < pingStartedAt && Util.getMillis() - pingStartedAt < TIMEOUT_MS;
		}

		public int playersOnline() {
			return data != null && data.players != null ? data.players.online() : 0;
		}

		public int playersMax() {
			return data != null && data.players != null ? data.players.max() : 0;
		}

		public long ping() {
			return data == null ? -1 : data.ping;
		}

		public String motd() {
			if (online() && data.motd != null)
				return data.motd.getString();
			return pinging() ? "Pinging..." : "Can't connect to server";
		}

		/** The server's own favicon, uploaded once it arrives (render thread only). */
		@Nullable
		public Identifier icon() {
			ServerData d = data;
			if (d == null || d.getIconBytes() == null)
				return iconTexture;
			if (d.getIconBytes() != iconBytes) {
				iconBytes = d.getIconBytes();
				try {
					NativeImage image = NativeImage.read(iconBytes);
					Identifier id = Identifier.fromNamespaceAndPath("aurore_mod", "advancedtitlescreens/server_icon/" + Integer.toHexString(address.toLowerCase(Locale.ROOT).hashCode()));
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ats server icon", image));
					iconTexture = id;
				} catch (Exception ignored) {
				}
			}
			return iconTexture;
		}

		void refreshIfStale() {
			long now = Util.getMillis();
			if (pingStartedAt >= 0 && now - pingStartedAt < REFRESH_MS)
				return;
			pingStartedAt = now;
			ServerData fresh = new ServerData(address, address, ServerData.Type.OTHER);
			if (data != null)
				fresh.setIconBytes(data.getIconBytes());
			boolean wasOnline = online();
			ServerData previous = data;
			if (!wasOnline || previous == null)
				data = fresh;
			POOL.execute(() -> {
				try {
					PINGER.pingServer(fresh, () -> {
					}, () -> {
						data = fresh;
						lastAnswerAt = Util.getMillis();
					}, net.minecraft.server.network.EventLoopGroupHolder.remote(Minecraft.getInstance().options.useNativeTransport()));
				} catch (Exception ignored) {
					// unknown host etc.: stays offline until the next refresh
				}
			});
		}
	}

	public static Entry get(String address) {
		Entry entry = ENTRIES.computeIfAbsent(address.toLowerCase(Locale.ROOT), Entry::new);
		entry.refreshIfStale();
		return entry;
	}

	/** Drives the network connections - call every client tick while a title screen is open. */
	public static void tick() {
		PINGER.tick();
	}
}