package com.example.mipmapstabilizer;

import com.example.mipmapstabilizer.config.ModConfig;
import com.example.mipmapstabilizer.debug.DebugLogger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class MipmapStabilizerClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Memuat config lebih awal supaya semua mixin sudah punya nilai yang benar.
		ModConfig.getInstance();

		DebugLogger.info("Mipmap Stabilizer aktif. enabled=" + ModConfig.getInstance().enabled
				+ " maxSafeMipmapLevel=" + ModConfig.getInstance().maxSafeMipmapLevel);

		// Setiap kali resource pack di-reload, cache level mipmap HARUS dibuang,
		// karena resolusi texture pada GL id yang sama bisa saja berganti resource
		// pack dengan resolusi berbeda. Ini satu-satunya saat cache dibersihkan -
		// bukan setiap frame/tick.
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public Identifier getFabricId() {
						return Identifier.of("mipmapstabilizer", "cache_reset");
					}

					@Override
					public void reload(ResourceManager manager) {
						MipmapStabilizer.clearAll();
						DebugLogger.info("Resource pack di-reload, cache level mipmap direset.");
					}
				});
	}
}
