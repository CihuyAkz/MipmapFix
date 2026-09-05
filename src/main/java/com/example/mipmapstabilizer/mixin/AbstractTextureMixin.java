package com.example.mipmapstabilizer.mixin;

import com.example.mipmapstabilizer.debug.DebugLogger;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin ini sengaja HANYA untuk logging/debug, TIDAK mengubah perilaku apa
 * pun. Target: net.minecraft.client.texture.AbstractTexture#close().
 *
 * close() dipilih sebagai titik pengait karena method ini berasal dari
 * kontrak Java standar (AutoCloseable/Closeable) yang wajib ada di semua
 * texture GPU Minecraft supaya tidak bocor memori - nama dan keberadaannya
 * jauh lebih stabil lintas versi dibanding method internal lain, sehingga
 * risiko gagal build karena mixin ini sangat kecil.
 *
 * Kegunaan: membantu mode debug melacak siklus hidup texture (kapan sebuah
 * texture benar-benar dibuang dari GPU), untuk membedakan "texture di-upload
 * ulang karena reload resource pack yang wajar" vs "texture berulang kali
 * dibuat-hapus dalam waktu singkat" (indikasi lain dari flicker).
 */
@Mixin(AbstractTexture.class)
public abstract class AbstractTextureMixin {

	@Inject(method = "close", at = @At("HEAD"))
	private void mipmapstabilizer$onClose(CallbackInfo ci) {
		DebugLogger.reupload(this.getClass().getSimpleName());
	}
}
