package com.example.mipmapstabilizer;

import com.example.mipmapstabilizer.config.ModConfig;
import com.example.mipmapstabilizer.debug.DebugLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logika inti mod ini.
 *
 * CATATAN DESAIN PENTING:
 * Versi 1.21.11 sudah memakai lapisan abstraksi GPU Mojang sendiri
 * (GpuTexture/GpuTextureView/GpuDevice, terlihat di field-field baru
 * TextureAtlas seperti "mipViews"), BUKAN lagi panggilan GL11/GL30 langsung
 * dari kode texture seperti versi lama. Karena itu, mod ini SENGAJA TIDAK
 * memanggil GL_TEXTURE_LOD_BIAS/GL_TEXTURE_MAX_LEVEL secara manual (itu
 * pendekatan yang valid di versi lama tapi berisiko tidak berpengaruh atau
 * malah salah di pipeline baru).
 *
 * Sebagai gantinya, strategi mod ini adalah MENCEGAH level mipmap yang tidak
 * stabil supaya tidak pernah diminta sama sekali:
 * 1. Sekali per atlas/texture (bukan sekali per frame), hitung "level mipmap
 *    aman" berdasarkan resolusi asli texture (harus habis dibagi 2 sebanyak
 *    N kali tanpa sisa untuk N level), lalu CACHE hasilnya.
 * 2. Clamp nilai mipmap level yang dipakai AtlasManager/TextureAtlas ke nilai
 *    aman itu SEBELUM proses stitching, sehingga Minecraft tidak pernah
 *    menghasilkan mip level yang membuatnya fallback/rescale bolak-balik.
 * 3. Level yang sudah di-cache TIDAK dihitung ulang kecuali resource pack
 *    benar-benar di-reload (lihat MipmapStabilizerClient), jadi tidak ada
 *    biaya CPU tambahan tiap frame/tick.
 */
public final class MipmapStabilizer {

	private static final Map<String, CachedLevel> STABLE_LEVEL_BY_ATLAS_ID = new ConcurrentHashMap<>();

	private MipmapStabilizer() {
	}

	private record CachedLevel(int width, int height, int level) {
	}

	/**
	 * Hitung level mipmap paling tinggi yang "bersih" (width dan height masih
	 * bisa dibagi 2 tanpa sisa) untuk resolusi tertentu, dibatasi oleh
	 * maxSafeMipmapLevel dari config. Ini mencegah Minecraft mencoba
	 * menghasilkan level mip untuk resolusi yang tidak habis dibagi rata,
	 * yang pada resource pack non power-of-two adalah salah satu pemicu
	 * fallback/rescale bolak-balik.
	 */
	public static int computeSafeMipmapLevel(int width, int height, int requestedLevel) {
		ModConfig cfg = ModConfig.getInstance();

		if (cfg.forceStableMipmapLevel) {
			return Math.min(cfg.stableLevelOverride, cfg.maxSafeMipmapLevel);
		}

		int hardCap = Math.min(requestedLevel, cfg.maxSafeMipmapLevel);
		if (!cfg.validateTextureResolution) {
			return hardCap;
		}

		int safe = 0;
		int w = width;
		int h = height;
		while (safe < hardCap && w % 2 == 0 && h % 2 == 0 && w > 1 && h > 1) {
			w /= 2;
			h /= 2;
			safe++;
		}
		return safe;
	}

	/**
	 * Ambil level yang sudah di-cache untuk atlas ini (dikunci berdasarkan
	 * Identifier atlas, misal "minecraft:textures/atlas/blocks.png"), atau
	 * hitung dan simpan kalau ini pertama kali / resolusinya berubah sejak
	 * terakhir kali. Mengembalikan level yang SAMA selama resolusi sumber
	 * tidak berubah, supaya tidak ada osilasi antar reload/frame.
	 */
	public static int getOrComputeStableLevel(String atlasId, int width, int height, int requestedLevel) {
		if (!ModConfig.getInstance().enabled) {
			return requestedLevel;
		}

		CachedLevel cached = STABLE_LEVEL_BY_ATLAS_ID.get(atlasId);
		if (cached != null && cached.width() == width && cached.height() == height) {
			return cached.level();
		}

		int level = computeSafeMipmapLevel(width, height, requestedLevel);
		STABLE_LEVEL_BY_ATLAS_ID.put(atlasId, new CachedLevel(width, height, level));

		if (cached != null && cached.level() != level) {
			DebugLogger.mipLevelChanged(atlasId, cached.level(), level);
		}
		DebugLogger.textureEvent(atlasId, width, height, requestedLevel, level,
				(width % (1 << Math.max(requestedLevel, 1)) != 0
						|| height % (1 << Math.max(requestedLevel, 1)) != 0)
						? "resolusi tidak habis dibagi rata untuk requestedLevel, level diturunkan agar stabil"
						: "ok, resolusi mendukung requestedLevel sepenuhnya");
		return level;
	}

	public static void forgetAtlas(String atlasId) {
		STABLE_LEVEL_BY_ATLAS_ID.remove(atlasId);
	}

	public static void clearAll() {
		STABLE_LEVEL_BY_ATLAS_ID.clear();
	}
}
