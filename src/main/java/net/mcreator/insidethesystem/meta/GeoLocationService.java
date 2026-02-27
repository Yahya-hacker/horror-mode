package net.mcreator.insidethesystem.meta;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GeoLocationService — Resolves the player's real-world location from their public IP.
 *
 * Uses the free ip-api.com service (no API key needed, 45 req/min limit).
 * Results are cached after the first successful lookup.
 *
 * PRIVACY NOTE: This sends ONE HTTP request to ip-api.com to resolve the player's
 * public IP to a city/region/country. The result is cached in-memory only and never
 * written to disk or sent anywhere else. This is used purely for horror immersion
 * (making the entity appear to "know" where you live).
 */
public class GeoLocationService {
    private static final Logger LOGGER = LogManager.getLogger("SentientCoolplayer-Geo");
    private static final Gson GSON = new Gson();

    // Cached geolocation data (fetched once)
    private static volatile GeoData cachedData = null;
    private static final AtomicBoolean fetchInProgress = new AtomicBoolean(false);

    /**
     * Immutable container for geolocation data.
     */
    public static record GeoData(
        String ip,
        String city,
        String regionName,
        String country,
        String timezone,
        String isp,
        double lat,
        double lon
    ) {
        /** Short display: "City, Region" (e.g., "Algiers, Algiers") */
        public String shortLocation() {
            if (city != null && !city.isEmpty() && regionName != null && !regionName.isEmpty()) {
                return city + ", " + regionName;
            }
            if (city != null && !city.isEmpty()) return city;
            if (regionName != null && !regionName.isEmpty()) return regionName;
            return country != null ? country : "Unknown";
        }

        /** Full display: "City, Region, Country" */
        public String fullLocation() {
            StringBuilder sb = new StringBuilder();
            if (city != null && !city.isEmpty()) sb.append(city);
            if (regionName != null && !regionName.isEmpty()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(regionName);
            }
            if (country != null && !country.isEmpty()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(country);
            }
            return sb.isEmpty() ? "Unknown" : sb.toString();
        }
    }

    /**
     * Returns cached geolocation data, or null if not yet fetched.
     * Call {@link #fetchAsync()} to trigger the initial lookup.
     */
    public static GeoData getCachedLocation() {
        return cachedData;
    }

    /**
     * Returns true if geolocation data is available.
     */
    public static boolean isAvailable() {
        return cachedData != null;
    }

    /**
     * Triggers an async geolocation lookup. Safe to call multiple times —
     * only the first call actually fetches; subsequent calls return the cached future.
     */
    public static CompletableFuture<GeoData> fetchAsync() {
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData);
        }
        if (!fetchInProgress.compareAndSet(false, true)) {
            // Another thread is already fetching — return a future that polls
            return CompletableFuture.supplyAsync(() -> {
                for (int i = 0; i < 50; i++) {
                    if (cachedData != null) return cachedData;
                    try { Thread.sleep(200); } catch (InterruptedException e) { break; }
                }
                return cachedData; // may still be null
            });
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                GeoData data = fetchFromIpApi();
                if (data != null) {
                    cachedData = data;
                    LOGGER.info("[Geo] Location resolved: {} ({})", data.shortLocation(), data.ip());
                }
                return data;
            } catch (Exception e) {
                LOGGER.warn("[Geo] Geolocation lookup failed", e);
                return null;
            } finally {
                fetchInProgress.set(false);
            }
        });
    }

    /**
     * Synchronous fetch from ip-api.com. Returns null on failure.
     * Format: http://ip-api.com/json/?fields=status,message,country,regionName,city,lat,lon,timezone,isp,query
     */
    private static GeoData fetchFromIpApi() {
        // Try primary endpoint
        String[] endpoints = {
            "http://ip-api.com/json/?fields=status,message,country,regionName,city,lat,lon,timezone,isp,query",
            "https://ipwho.is/"  // Fallback API (HTTPS, no key needed)
        };

        for (String url : endpoints) {
            try {
                GeoData result = url.contains("ip-api.com") ? fetchIpApi(url) : fetchIpWhoIs(url);
                if (result != null) return result;
            } catch (Exception e) {
                LOGGER.debug("[Geo] Endpoint {} failed: {}", url, e.getMessage());
            }
        }
        return null;
    }

    private static GeoData fetchIpApi(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "SentientCoolplayer/1.0.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() != 200) return null;

            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (!"success".equals(getStr(json, "status"))) return null;

                return new GeoData(
                    getStr(json, "query"),
                    getStr(json, "city"),
                    getStr(json, "regionName"),
                    getStr(json, "country"),
                    getStr(json, "timezone"),
                    getStr(json, "isp"),
                    json.has("lat") ? json.get("lat").getAsDouble() : 0,
                    json.has("lon") ? json.get("lon").getAsDouble() : 0
                );
            }
        } finally {
            conn.disconnect();
        }
    }

    private static GeoData fetchIpWhoIs(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "SentientCoolplayer/1.0.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() != 200) return null;

            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (!json.has("success") || !json.get("success").getAsBoolean()) return null;

                return new GeoData(
                    getStr(json, "ip"),
                    getStr(json, "city"),
                    getStr(json, "region"),
                    getStr(json, "country"),
                    getStr(json,  "timezone", "timezone_id"),
                    getStr(json, "connection", "isp"),
                    json.has("latitude") ? json.get("latitude").getAsDouble() : 0,
                    json.has("longitude") ? json.get("longitude").getAsDouble() : 0
                );
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String getStr(JsonObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key)) {
                var elem = json.get(key);
                if (elem.isJsonPrimitive()) return elem.getAsString();
                if (elem.isJsonObject()) {
                    // For nested objects like ipwhois's "connection.isp"
                    JsonObject nested = elem.getAsJsonObject();
                    for (String nestedKey : new String[]{"id", "name", "isp"}) {
                        if (nested.has(nestedKey)) return nested.get(nestedKey).getAsString();
                    }
                }
            }
        }
        return "";
    }
}
