package com.example.mipmapstabilizer.mixin;

import com.example.mipmapstabilizer.config.ModConfig;
import com.example.mipmapstabilizer.debug.DebugLogger;
import net.minecraft.client.texture.AtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * INI MIXIN UTAMA MOD INI.
 *
 * TARGET: net.minecraft.client.texture.AtlasManager (nama Yarn untuk
 * Mojang-mapped "net.minecraft.client.renderer.texture.TextureAtlas"'s
 * pengatur pusat, class baru sejak sekitar 1.21.9) - method setMipmapLevels(int).
 * Berdasarkan riwayat mapping resmi Yarn untuk 1.21.11, method Mojang
 * "updateMaxMipLevel(int)" dipetakan Yarn menjadi "setMipmapLevels(int)".
 * Method ini dipanggil ketika opsi "Mipmap Levels" berubah / saat resource
 * pack di-reload, dan nilainya lalu dipakai untuk men-stitch ULANG semua
 * atlas texture.
 *
 * KENAPA INI MENCEGAH FLICKERING:
 * Flicker terjadi karena level mipmap yang diminta (requested level) tidak
 * konsisten dengan resolusi asli sprite pada resource pack yang dipakai -
 * menyebabkan Minecraft bolak-balik fallback ke resolusi lain saat stitching
 * ulang. Dengan meng-clamp nilai yang masuk ke method ini SEBELUM proses
 * stitching berjalan, atlas selalu di-stitch dengan level yang sudah
 * divalidasi aman, jadi tidak pernah ada kondisi "naik lalu turun lagi".
 *
 * KALAU BUILD GAGAL (nama method berbeda di build Yarn final kamu):
 * 1. Jalankan: ./gradlew genSources
 * 2. Buka file hasil decompile untuk AtlasManager.java
 *    (biasanya di .gradle/loom-cache/... atau lewat "Attach Sources" di IDE)
 * 3. Cari method dengan SATU parameter int yang menyimpan nilainya ke field
 *    bernama mirip "mipmapLevels" (field ini juga sudah dikonfirmasi ada
 *    lewat riwayat mapping resmi untuk 1.21.11).
 * 4. Ganti nilai `method = "setMipmapLevels"` di bawah sesuai nama aslinya.
 */
@Mixin(AtlasManager.class)
public class TextureAtlasMixin {

	@ModifyVariable(method = "setMipmapLevels", at = @At("HEAD"), argsOnly = true)
	private int mipmapstabilizer$clampMipmapLevels(int requestedLevel) {
		ModConfig cfg = ModConfig.getInstance();
		if (!cfg.enabled) {
			return requestedLevel;
		}

		int clamped = cfg.forceStableMipmapLevel
				? Math.min(cfg.stableLevelOverride, cfg.maxSafeMipmapLevel)
				: Math.min(requestedLevel, cfg.maxSafeMipmapLevel);

		if (clamped != requestedLevel) {
			DebugLogger.info("AtlasManager: mipmap level diminta=" + requestedLevel
					+ " -> di-clamp jadi " + clamped + " (lihat maxSafeMipmapLevel di config)");
		}
		return clamped;
	}
}
