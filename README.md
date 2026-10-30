# Adblocker-
A local-DNS ad/tracker blocker for Android (VPN-API based; no traffic leaves your device except DNS lookups).

## Build
Push to GitHub and download the `AdBlocker-apk` artifact from the **Build APK** workflow, or run
`gradle :app:assembleRelease` with the Android SDK installed.

## Messaging apps (WhatsApp / Signal / Telegram)
These are excluded from the filter (`BlockerService.BYPASS_APPS`) so they always use the normal network.
Upstream DNS now comes from the active network (with 1.1.1.1 / 8.8.8.8 as fallback), and DNS-over-TCP
attempts get an immediate reset instead of hanging.

## YouTube
DNS filtering blocks `ads.youtube.com`, `doubleclick.net` and friends, but the YouTube app serves video ads
from the same `googlevideo.com` hosts as the videos themselves, so a DNS blocker cannot remove them without
breaking playback. Use a patched client (ReVanced / SmartTube) or a browser with uBlock Origin for that.
