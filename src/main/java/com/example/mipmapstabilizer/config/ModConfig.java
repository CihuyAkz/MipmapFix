package com.example.mipmapstabilizer.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Config sengaja memakai java.util.Properties (bawaan JDK), BUKAN library
 * pihak ketiga, supaya file ini kompatibel dengan mapping/versi apa pun
 * tanpa bergantung pada API Minecraft yang bisa berubah nama.
 *
 * File disimpan di: config/mipmapstabilizer.properties
 */
public final class ModConfig {

	private static final String FILE_NAME = "mipmapstabilizer.properties";

	// ---- Nilai default (aman, tidak mengubah kualitas texture secara ekstrem) ----
	public boolean enabled = true;
	public int maxSafeMipmapLevel = 4;        // batas atas level mipmap yang diizinkan
	public boolean forceStableMipmapLevel = false; // jika true, semua atlas dipaksa pakai stableLevelOverride
	public int stableLevelOverride = 4;
	public boolean validateTextureResolution = true; // deteksi resolusi non power-of-two
	public boolean debugLogging = false;
	public float lodBiasStabilize = -0.15f;   // bias kecil untuk mencegah "mip crawl" tepat di batas level

	private static ModConfig instance;

	private ModConfig() {
	}

	public static ModConfig getInstance() {
		if (instance == null) {
			instance = new ModConfig();
			instance.load();
		}
		return instance;
	}

	private Path configPath() {
		// FabricLoader.getInstance().getConfigDir() adalah cara resmi Fabric API
		// untuk lokasi folder config, method ini stabil sejak awal Fabric API.
		return net.fabricmc.loader.api.FabricLoader.getInstance()
				.getConfigDir()
				.resolve(FILE_NAME);
	}

	public void load() {
		Path path = configPath();
		Properties props = new Properties();

		if (Files.exists(path)) {
			try (InputStream in = Files.newInputStream(path)) {
				props.load(in);
			} catch (IOException e) {
				com.example.mipmapstabilizer.debug.DebugLogger.warn(
						"Gagal membaca config, memakai nilai default: " + e.getMessage());
			}
		}

		enabled = parseBool(props, "enabled", enabled);
		maxSafeMipmapLevel = parseInt(props, "maxSafeMipmapLevel", maxSafeMipmapLevel);
		forceStableMipmapLevel = parseBool(props, "forceStableMipmapLevel", forceStableMipmapLevel);
		stableLevelOverride = parseInt(props, "stableLevelOverride", stableLevelOverride);
		validateTextureResolution = parseBool(props, "validateTextureResolution", validateTextureResolution);
		debugLogging = parseBool(props, "debugLogging", debugLogging);
		lodBiasStabilize = parseFloat(props, "lodBiasStabilize", lodBiasStabilize);

		// Selalu tulis ulang supaya file config berisi semua key + komentar,
		// termasuk saat pertama kali dibuat.
		save();
	}

	public void save() {
		Path path = configPath();
		Properties props = new Properties();
		props.setProperty("enabled", String.valueOf(enabled));
		props.setProperty("maxSafeMipmapLevel", String.valueOf(maxSafeMipmapLevel));
		props.setProperty("forceStableMipmapLevel", String.valueOf(forceStableMipmapLevel));
		props.setProperty("stableLevelOverride", String.valueOf(stableLevelOverride));
		props.setProperty("validateTextureResolution", String.valueOf(validateTextureResolution));
		props.setProperty("debugLogging", String.valueOf(debugLogging));
		props.setProperty("lodBiasStabilize", String.valueOf(lodBiasStabilize));

		try {
			Files.createDirectories(path.getParent());
			try (OutputStream out = Files.newOutputStream(path)) {
				props.store(out,
						" Mipmap Stabilizer config\n" +
						" enabled: aktif/nonaktifkan seluruh mod\n" +
						" maxSafeMipmapLevel: batas atas level mipmap yang boleh dipakai (default 4, sama seperti vanilla)\n" +
						" forceStableMipmapLevel: paksa semua atlas texture memakai stableLevelOverride\n" +
						" stableLevelOverride: level yang dipakai kalau forceStableMipmapLevel = true\n" +
						" validateTextureResolution: deteksi texture non power-of-two sebelum menghitung mip level aman\n" +
						" debugLogging: catat detail texture/mipmap ke log\n" +
						" lodBiasStabilize: GL_TEXTURE_LOD_BIAS kecil untuk mencegah lompat-lompat level tepat di batas");
			}
		} catch (IOException e) {
			com.example.mipmapstabilizer.debug.DebugLogger.warn(
					"Gagal menyimpan config: " + e.getMessage());
		}
	}

	private static boolean parseBool(Properties p, String key, boolean def) {
		String v = p.getProperty(key);
		return v == null ? def : Boolean.parseBoolean(v);
	}

	private static int parseInt(Properties p, String key, int def) {
		String v = p.getProperty(key);
		if (v == null) return def;
		try {
			return Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static float parseFloat(Properties p, String key, float def) {
		String v = p.getProperty(key);
		if (v == null) return def;
		try {
			return Float.parseFloat(v.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
