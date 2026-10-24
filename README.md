# ChobiVault — গ্যালারি + ভল্ট

Android gallery app with a built-in encrypted vault. Bangla-first UI,
fully offline, no account needed.

- **গ্যালারি:** ফোনের সব ছবি/ভিডিও আপনাআপনি দেখাবে (MediaStore — কোনো কপি নয়)
- **অ্যালবাম:** ফোল্ডার ধরে সাজানো
- **ভল্ট:** PIN/ফিঙ্গারপ্রিন্ট দিয়ে সুরক্ষিত, AES-256/GCM এনক্রিপশন
  (PhotoLock-এর প্রমাণিত CryptoManager/PinManager reuse করা)

## Status

- v0.1.0 scaffold (2026-10-24): project shape, bottom-nav shell,
  launcher icon (gradient pinwheel + lock), vault crypto module.
  Next: real MediaStore grid, albums, viewer, vault import/export screens.
- Store title: **ChobiVault: Gallery + Vault**
- Package: `com.sowrobrana.chobivault`
- Logo: `branding/icon-final-512.png` / `branding/icon-final-1024.png`

## Build

GitHub Actions builds a debug APK on every push to main
(`.github/workflows/build-debug.yml`) — same pattern as PhotoLock.
