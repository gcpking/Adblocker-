# Adblocker-
A local-DNS ad/tracker blocker for Android (VPN-API based; no traffic leaves your device except DNS lookups).

## Build
Push to GitHub and download the `AdBlocker-apk` artifact from the **Build APK** workflow, or run
`gradle :app:assembleRelease` with the Android SDK installed.

## Messaging apps (WhatsApp / Signal / Telegram)
These are excluded from the filter (`BlockerService.BYPASS_APPS`) so they always use the normal network.
Upstream DNS now comes from the active network (with 1.1.1.1 / 8.8.8.8 as fallback), and DNS-over-TCP
attempts get an immediate reset instead of hanging.

## YouTube (ad skipper)
Enable "YouTube ad skipper" in the app (Accessibility settings). It taps the Skip ad button the moment it appears.
Unskippable ads still play: DNS filtering cannot remove them because YouTube serves ads from the same
`googlevideo.com` hosts as videos. For full removal use a patched client (ReVanced / SmartTube) or uBlock Origin in a browser.
