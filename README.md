# Mipmap Stabilizer (Fabric, Minecraft 1.21.11)

Mod client-side untuk mengatasi texture flickering saat **Mipmap Level**
dinaikkan, terutama dengan resource pack yang resolusinya tidak ideal
(bukan power-of-two bersih relatif terhadap grid 16px vanilla).

## ⚠️ Baca ini dulu: batasan jujur soal versi 1.21.11

1.21.11 dirilis **setelah** batas pengetahuan model yang membuat mod ini,
jadi nama class/metode di bawah diverifikasi lewat pencarian dokumentasi &
riwayat mapping publik saat kode ini ditulis (bukan dari ingatan/hafalan).
Yang sudah terverifikasi lewat sumber resmi:

- 1.21.11 **masih memakai obfuskasi + Yarn mappings** (versi ini adalah
  rilis terakhir yang diobfuskasi sebelum Mojang pindah ke versioning
  `26.1` tanpa obfuskasi).
- Class `net.minecraft.client.texture.AtlasManager` (Yarn) ada sejak
  ~1.21.9 dan **masih ada di 1.21.11**, dengan method `setMipmapLevels(int)`
  yang memetakan ke nama Mojang `updateMaxMipLevel(int)`.
- `TextureAtlas` (Yarn: `SpriteAtlasTexture`) di 1.21.11 sudah punya
  field `GpuTextureView[] mipViews` dan `GpuBuffer` — artinya Minecraft
  sekarang mengelola texture lewat lapisan abstraksi GPU sendiri
  (Blaze3D `GpuTexture`/`GpuDevice`), **bukan** lagi panggilan
  `GL11`/`GL30` langsung seperti versi lama.

Karena poin terakhir itu, mod ini **sengaja tidak** menyentuh
`GL_TEXTURE_LOD_BIAS` / `GL_TEXTURE_MAX_LEVEL` lewat LWJGL secara manual —
pendekatan itu valid di Minecraft versi lama tapi kemungkinan besar tidak
berpengaruh (atau salah) di pipeline baru 1.21.11. Sebagai gantinya, mod
ini mencegah level mipmap yang tidak stabil **sebelum** diminta ke sistem
stitching atlas (lihat "Cara Kerja" di bawah).

**Sebelum build pertama**, jalankan `./gradlew genSources` dan cek langsung
di IDE kamu apakah nama method `setMipmapLevels` di `AtlasManager` sudah
sesuai — kalau build Yarn final untuk 1.21.11 yang kamu pakai memakai nama
lain, sesuaikan `method = "..."` di
`TextureAtlasMixin.java` (sudah ada instruksi detail di komentar file itu).

## Root cause (analisis)

Ada dua sumber flicker yang berbeda dan mod ini menyasar keduanya secara
tidak langsung:

1. **Ketidakstabilan level mipmap saat stitching atlas.** Jumlah level
   mipmap yang dipakai atlas dihitung berdasarkan resolusi sprite yang
   dimuat. Kalau resource pack punya sprite dengan resolusi yang tidak
   habis dibagi 2 secara bersih sebanyak N kali (N = mipmap level yang
   diminta), proses reload/restitch bisa menghasilkan level efektif yang
   berbeda antar reload — inilah yang terlihat sebagai "naik-turun
   resolusi".
2. **Mip crawl di level GPU/driver**, terutama di perangkat kelas bawah
   (kamu menyebutkan POCO C65 dengan resolusi render 1072×482, yang
   mengindikasikan Minecraft Java dijalankan lewat launcher Android /
   translation layer). Saat rasio minifikasi texture persis berada di
   batas dua level mip, GPU bisa berpindah level tiap frame tergantung
   pergerakan kamera sekecil apa pun. Ini adalah perilaku sampling GPU,
   bukan bug logika Java — mod ini **tidak bisa** memperbaiki ini secara
   langsung di 1.21.11 tanpa akses ke internal `GpuDevice` yang jauh lebih
   dalam dan berisiko tinggi untuk ditebak lewat mixin. Yang mod ini
   lakukan adalah menghilangkan penyebab #1 sepenuhnya, yang pada praktiknya
   juga mengurangi peluang kondisi batas untuk #2 karena level yang dipakai
   jadi konsisten antar reload.

## Cara kerja

1. `TextureAtlasMixin` meng-clamp nilai yang masuk ke
   `AtlasManager.setMipmapLevels(int)` ke `maxSafeMipmapLevel` dari config
   **sebelum** proses stitching atlas berjalan. Mipmap tetap aktif — ini
   bukan `mipmapLevels=0`, hanya membatasi batas atasnya.
2. `MipmapStabilizer` menyediakan cache per-atlas (`getOrComputeStableLevel`)
   supaya kalau kamu memperluas mod ini untuk validasi per-resolusi yang
   lebih detail, hasilnya tidak dihitung ulang tiap frame/tick — hanya
   dihitung ulang saat resource pack benar-benar di-reload.
3. `AbstractTextureMixin` mengait `close()` (method siklus hidup texture
   yang stabil lintas versi) murni untuk logging debug, tidak mengubah
   perilaku apa pun.
4. Cache mipmap direset otomatis setiap resource reload lewat
   `SimpleSynchronousResourceReloadListener` di `MipmapStabilizerClient`.

## Struktur project

```
mipmap-stabilizer/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── LICENSE
├── README.md
└── src/main/
    ├── resources/
    │   ├── fabric.mod.json
    │   └── mipmapstabilizer.mixins.json
    └── java/com/example/mipmapstabilizer/
        ├── MipmapStabilizerClient.java   (entrypoint client)
        ├── MipmapStabilizer.java         (logika inti + cache)
        ├── config/ModConfig.java         (config berbasis .properties)
        ├── debug/DebugLogger.java        (logging kondisional)
        └── mixin/
            ├── TextureAtlasMixin.java    (mixin utama: clamp mipmap level)
            └── AbstractTextureMixin.java (mixin debug, aman & non-invasif)
```

## Build

Butuh JDK 21.

```bash
./gradlew build
```

Hasil jar ada di `build/libs/mipmap-stabilizer-1.0.0.jar`.

> Project ini belum menyertakan `gradlew`/`gradle-wrapper.jar` (file biner).
> Cara tercepat mendapatkannya: download Fabric Example Mod template dari
> https://github.com/FabricMC/fabric-example-mod, salin folder `gradle/`
> beserta `gradlew` dan `gradlew.bat` dari situ ke root project ini, lalu
> jalankan `./gradlew wrapper --gradle-version <versi_terbaru>` sekali agar
> wrapper menyesuaikan dengan Loom versi terbaru.

## Instalasi

1. Pasang **Fabric Loader** ≥ 0.19.2 untuk Minecraft 1.21.11.
2. Pasang **Fabric API** versi yang cocok (mis. `0.140.0+1.21.11`) ke
   folder `mods/`.
3. Salin `mipmap-stabilizer-1.0.0.jar` hasil build ke folder `mods/`.
4. Jalankan game, lalu atur Mipmap Level seperti biasa di
   Video Settings.

## Konfigurasi

File dibuat otomatis di `config/mipmapstabilizer.properties`:

| Key | Default | Keterangan |
|---|---|---|
| `enabled` | `true` | Aktif/nonaktifkan seluruh mod |
| `maxSafeMipmapLevel` | `4` | Batas atas level mipmap yang boleh dipakai |
| `forceStableMipmapLevel` | `false` | Paksa semua atlas pakai `stableLevelOverride` |
| `stableLevelOverride` | `4` | Dipakai kalau `forceStableMipmapLevel=true` |
| `validateTextureResolution` | `true` | Deteksi resolusi non power-of-two |
| `debugLogging` | `false` | Catat detail texture/mipmap ke log |
| `lodBiasStabilize` | `-0.15` | Disimpan untuk kompatibilitas versi lama; tidak dipakai di jalur render 1.21.11 (lihat catatan Blaze3D di atas) |

## Mode debug

Set `debugLogging=true` lalu cek `logs/latest.log`, kamu akan melihat baris
seperti:

```
[texture=minecraft:textures/atlas/blocks.png] resolusi=1024x1024 requestedLevel=4 usedLevel=4 alasan=ok
AtlasManager: mipmap level diminta=4 -> di-clamp jadi 3 (lihat maxSafeMipmapLevel di config)
```

## Keterbatasan

- Deteksi resolusi **per-sprite individual** (bukan per-atlas) butuh hook
  ke data internal proses stitching (`SpriteLoader.Preparations`) yang
  strukturnya kemungkinan besar berubah antar snapshot 1.21.x — sengaja
  tidak diimplementasikan di versi ini supaya mixin tetap bisa di-build
  dengan andal. `MipmapStabilizer.computeSafeMipmapLevel()` sudah
  disediakan sebagai fungsi murni kalau kamu ingin menyambungkannya sendiri
  setelah verifikasi mapping.
- Kalau penyebab flicker di perangkatmu murni "mip crawl" di level driver
  GPU (poin #2 di root cause), mod ini mengurangi peluangnya tapi tidak
  menjaminnya hilang 100%, karena itu sudah di luar kendali kode Java.
