package com.example.mipmapstabilizer.debug;

import com.example.mipmapstabilizer.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logger ringan. Semua pemanggilan debug() dicek dulu lewat config.debugLogging
 * SEBELUM membangun string apa pun, supaya saat debug mode mati, overhead-nya
 * cuma satu pengecekan boolean per panggilan (aman dipanggil dari render path).
 */
public final class DebugLogger {

	private static final Logger LOGGER = LoggerFactory.getLogger("MipmapStabilizer");

	// Menghitung berapa kali sebuah texture di-upload ulang, untuk mode debug.
	private static final ConcurrentHashMap<String, AtomicInteger> REUPLOAD_COUNTS = new ConcurrentHashMap<>();

	private DebugLogger() {
	}

	public static void info(String msg) {
		LOGGER.info(msg);
	}

	public static void warn(String msg) {
		LOGGER.warn(msg);
	}

	/** Log detail texture/mipmap, hanya aktif kalau debugLogging = true di config. */
	public static void textureEvent(String textureId, int origWidth, int origHeight,
			int requestedLevel, int usedLevel, String reason) {
		if (!ModConfig.getInstance().debugLogging) {
			return;
		}
		LOGGER.info("[texture={}] resolusi={}x{} requestedLevel={} usedLevel={} alasan={}",
				textureId, origWidth, origHeight, requestedLevel, usedLevel, reason);
	}

	public static void mipLevelChanged(String textureId, int oldLevel, int newLevel) {
		if (!ModConfig.getInstance().debugLogging) {
			return;
		}
		LOGGER.info("[texture={}] mipmap level berubah: {} -> {} (dicegah jika berulang tiap frame)",
				textureId, oldLevel, newLevel);
	}

	public static void reupload(String textureId) {
		if (!ModConfig.getInstance().debugLogging) {
			return;
		}
		int count = REUPLOAD_COUNTS.computeIfAbsent(textureId, k -> new AtomicInteger()).incrementAndGet();
		LOGGER.info("[texture={}] re-upload ke GPU, total sejauh ini: {}", textureId, count);
	}
}
