# Govt Photo & Document Compressor

Native Android app (Kotlin + Jetpack Compose + Material 3) for Indian users who
need photos and documents sized for government portals — Voter Helpline, ECI,
Passport Seva, PAN, Parivahan, govt job portals.

## Build
```bash
./gradle-build.sh assembleDebug     # debug APK
./gradle-build.sh assembleRelease   # unsigned release APK
```
Release is signed with the shared Prince release key
(`~/workspace/reminder-app/release.keystore`, alias `reminder`) via zipalign +
apksigner — see the release steps in the project history. minSdk 26, targetSdk 34.

## Features (all real, all offline)
- Photo Compressor: exact KB targets 10 KB–1 MB + custom; binary-search quality
  + dimension scaling; reports the real achieved size, never claims a target
  unless the file actually meets it.
- Image Converter: JPG/PNG/WEBP/BMP (transparency kept for PNG/WEBP).
- Image Resizer: exact px, aspect lock, quick sizes (incl. 213×213 PAN, 413×531 passport).
- Signature Compressor: drag-to-crop, white-border trim, white background, KB target.
- Passport Photo Maker: 35×45 mm (413×531 @300 DPI), white bg, 4×6 (6) and A4 (20) print sheets.
- PDF tools (PDFBox-Android): compress (re-encodes embedded images), image→PDF,
  PDF→image, merge PDFs.
- Bulk Photo Compressor: WorkManager background job, per-file save + ZIP of all results.
- Govt presets (8, all editable in-app, DataStore): Voter ID photo/signature, ECI,
  Passport, PAN, Aadhaar, Driving Licence, Govt Job. Defaults are starting values
  only — the app never claims a portal's current rule.
- Storage: Photo Picker + SAF, saves to Downloads / Govt Photo Compressor, share
  via FileProvider. Dark mode. Privacy policy in-app.

## Honest limitations
- HEIC: decoded where the device supports it (Android 9+); no HEIC output — converts to JPG.
- TIFF: built-in minimal decoder (uncompressed + PackBits, 1/8-bit, chunky). Exotic
  TIFFs (LZW/JPEG compression, tiled) fail with a clear error instead of a fake result.
- BMP: decoded by the system; written by a built-in 24-bit BMP writer.
- PDF compression only shrinks image-heavy PDFs; text-only PDFs change little and
  the result card says so.
- PNG is lossless: very small KB targets may not be reachable — the result card
  shows the real size instead of claiming success.
- No emulator in the sandbox (no KVM): install on a real phone to test.
