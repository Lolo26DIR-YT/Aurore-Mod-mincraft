package net.mcreator.auroremod.advancedtitlescreens.client;

import net.neoforged.fml.ModList;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.SharedConstants;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.HashMap;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

import java.net.URI;
import java.net.HttpURLConnection;

import java.io.Reader;
import java.io.InputStream;
import java.io.File;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

/**
 * Advanced Title Screens: replaces %placeholders% in texts and button labels, and turns &amp;-color codes
 * (e.g. "&amp;cRed") into Minecraft formatting.
 *
 * <ul>
 * <li>%player%, %version%, %modversion%, %modname%, %mods%, %date%, %time%, %worlds%</li>
 * <li>Stats summed over the player's singleplayer worlds: %deaths%, %mob_kills%, %player_kills%, %kd%,
 * %play_time% (hours), %distance% (km), %jumps%, %damage_dealt%, %kills:minecraft:zombie%,
 * %stat:minecraft:mined/minecraft:diamond_ore% (any statistic as category/id)</li>
 * <li>Live servers: %server_online:host%, %server_max:host%, %server_players:host%, %server_status:host%,
 * %server_motd:host%, %server_ping:host%</li>
 * <li>%web:https://url% (first line of the response) or %web:https://url|json.path% (a JSON field)</li>
 * </ul>
 */
public final class TitlePlaceholders {
	private static final Pattern TOKEN = Pattern.compile("%([a-z_]+)(?::([^%]*))?%");
	private static final Pattern COLOR = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
	private static volatile Map<String, Long> stats = Map.of();
	private static volatile int worldCount;
	private static volatile long statsLoadedAt = -1;
	private static volatile boolean statsLoading;

	private record Web(String value, long fetchedAt, boolean loading) {
	}

	private static final Map<String, Web> WEB = new ConcurrentHashMap<>();

	private TitlePlaceholders() {
	}

	public static String resolve(String text) {
		if (text == null || text.isEmpty())
			return "";
		String out = text;
		if (out.indexOf('%') >= 0) {
			Matcher m = TOKEN.matcher(out);
			StringBuilder sb = new StringBuilder();
			while (m.find()) {
				String value = value(m.group(1), m.group(2));
				m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? m.group() : value));
			}
			m.appendTail(sb);
			out = sb.toString();
		}
		if (out.indexOf('&') >= 0)
			out = COLOR.matcher(out).replaceAll("§$1");
		return out;
	}

	private static String value(String key, String arg) {
		Minecraft mc = Minecraft.getInstance();
		switch (key) {
			case "player" :
				return mc.getUser().getName();
			case "version" :
				return SharedConstants.getCurrentVersion().name();
			case "modversion" :
				return ModList.get().getModContainerById("aurore_mod").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
			case "modname" :
				return ModList.get().getModContainerById("aurore_mod").map(c -> c.getModInfo().getDisplayName()).orElse("aurore_mod");
			case "mods" :
				return Integer.toString(ModList.get().size());
			case "date" :
				return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
			case "time" :
				return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
			default :
				break;
		}
		if (key.startsWith("server_"))
			return server(key, arg);
		if ("web".equals(key))
			return web(arg);
		if ("var".equals(key)) {
			String v = arg == null ? null : TitleScreenApi.getVariable(arg.trim());
			return v == null ? "" : v;
		}
		ensureStats();
		switch (key) {
			case "worlds" :
				return Integer.toString(worldCount);
			case "deaths" :
				return fmt(stat("minecraft:custom/minecraft:deaths"));
			case "mob_kills" :
				return fmt(stat("minecraft:custom/minecraft:mob_kills"));
			case "player_kills" :
				return fmt(stat("minecraft:custom/minecraft:player_kills"));
			case "jumps" :
				return fmt(stat("minecraft:custom/minecraft:jump"));
			case "damage_dealt" :
				return fmt(stat("minecraft:custom/minecraft:damage_dealt") / 10);
			case "play_time" :
				return String.format(Locale.ROOT, "%.1f", stat("minecraft:custom/minecraft:play_time") / 72000.0);
			case "distance" : {
				long cm = 0;
				for (Map.Entry<String, Long> e : stats.entrySet())
					if (e.getKey().startsWith("minecraft:custom/") && e.getKey().endsWith("_one_cm"))
						cm += e.getValue();
				return String.format(Locale.ROOT, "%.1f", cm / 100000.0);
			}
			case "kd" : {
				long kills = stat("minecraft:custom/minecraft:player_kills");
				long deaths = stat("minecraft:custom/minecraft:deaths");
				return String.format(Locale.ROOT, "%.2f", deaths == 0 ? kills : kills / (double) deaths);
			}
			case "kills" :
				return arg == null ? null : fmt(stat("minecraft:killed/" + arg));
			case "stat" :
				return arg == null ? null : fmt(stat(arg));
			default :
				return null;
		}
	}

	private static String fmt(long v) {
		return String.format(Locale.ROOT, "%,d", v);
	}

	private static long stat(String key) {
		return stats.getOrDefault(key, 0L);
	}

	/** Sums saves/WORLD/stats/UUID.json of every singleplayer world, off-thread, refreshed every 30 s. */
	private static void ensureStats() {
		long now = Util.getMillis();
		if (statsLoading || (statsLoadedAt >= 0 && now - statsLoadedAt < 30000))
			return;
		statsLoading = true;
		File saves = new File(Minecraft.getInstance().gameDirectory, "saves");
		String uuid = Minecraft.getInstance().getUser().getProfileId().toString();
		Util.backgroundExecutor().execute(() -> {
			Map<String, Long> totals = new HashMap<>();
			int worlds = 0;
			try {
				File[] dirs = saves.listFiles(File::isDirectory);
				if (dirs != null) {
					for (File dir : dirs) {
						if (!new File(dir, "level.dat").isFile())
							continue;
						worlds++;
						File statsFile = new File(dir, "stats/" + uuid + ".json");
						if (!statsFile.isFile())
							continue;
						try (Reader reader = Files.newBufferedReader(statsFile.toPath(), StandardCharsets.UTF_8)) {
							JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
							JsonObject categories = root.getAsJsonObject("stats");
							if (categories == null)
								continue;
							for (Map.Entry<String, JsonElement> category : categories.entrySet())
								for (Map.Entry<String, JsonElement> entry : category.getValue().getAsJsonObject().entrySet())
									totals.merge(category.getKey() + "/" + entry.getKey(), entry.getValue().getAsLong(), Long::sum);
						} catch (Exception ignored) {
						}
					}
				}
			} finally {
				stats = totals;
				worldCount = worlds;
				statsLoadedAt = Util.getMillis();
				statsLoading = false;
			}
		});
	}

	private static String server(String key, String address) {
		if (address == null || address.isBlank())
			return null;
		TitleServerStatus.Entry e = TitleServerStatus.get(address.trim());
		return switch (key) {
			case "server_online" -> e.online() ? Integer.toString(e.playersOnline()) : "0";
			case "server_max" -> e.online() ? Integer.toString(e.playersMax()) : "0";
			case "server_players" -> e.online() ? e.playersOnline() + "/" + e.playersMax() : "-";
			case "server_status" -> e.online() ? "Online" : e.pinging() ? "..." : "Offline";
			case "server_motd" -> e.motd();
			case "server_ping" -> e.online() ? Long.toString(e.ping()) : "-";
			default -> null;
		};
	}

	/** Text from a URL, fetched off-thread and refreshed every 60 s; "..." until the first answer. */
	private static String web(String arg) {
		if (arg == null || arg.isBlank())
			return null;
		String url = arg, path = null;
		int bar = arg.lastIndexOf('|');
		if (bar > 0) {
			url = arg.substring(0, bar).trim();
			path = arg.substring(bar + 1).trim();
		}
		Web cached = WEB.get(arg);
		long now = Util.getMillis();
		if (cached == null || (!cached.loading() && now - cached.fetchedAt() > 60000)) {
			String value = cached == null ? "..." : cached.value();
			WEB.put(arg, new Web(value, now, true));
			String finalUrl = url, finalPath = path;
			Util.nonCriticalIoPool().execute(() -> WEB.put(arg, new Web(fetch(finalUrl, finalPath, value), Util.getMillis(), false)));
		}
		return WEB.get(arg).value();
	}

	private static String fetch(String url, String path, String fallback) {
		try {
			HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.setRequestProperty("User-Agent", "Minecraft-AdvancedTitleScreens");
			try (InputStream in = connection.getInputStream()) {
				String body = new String(in.readNBytes(65536), StandardCharsets.UTF_8);
				if (path == null || path.isEmpty()) {
					String line = body.strip().lines().findFirst().orElse("");
					return line.length() > 200 ? line.substring(0, 200) : line;
				}
				JsonElement node = JsonParser.parseString(body);
				for (String part : path.split("\\.")) {
					if (node == null || node.isJsonNull())
						return fallback;
					if (node.isJsonArray())
						node = node.getAsJsonArray().get(Integer.parseInt(part));
					else
						node = node.getAsJsonObject().get(part);
				}
				return node == null || node.isJsonNull() ? fallback : node.isJsonPrimitive() ? node.getAsString() : node.toString();
			}
		} catch (Exception e) {
			return fallback;
		}
	}
}