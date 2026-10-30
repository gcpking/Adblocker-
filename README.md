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

## Ad-free browser
"Open ad-free browser" in the app loads sites in a built-in WebView that drops requests to blocklisted hosts,
hides leftover ad elements, and on YouTube web skips/fast-forwards ads. Ad sites change often, so some may slip through.

## Experimental: cut YouTube during ads
With the ad skipper enabled and blocking turned on, the "cut YouTube during ads" switch drops the YouTube app's
connection for ~2.5 s when an ad marker is seen on screen, hoping YouTube resumes without the ad. Untested; the on-screen
markers (`Ad ·`, `Visit advertiser`, `Sponsored`) are best guesses and may need adjusting per YouTube version.

## Experimental: cut before ad markers
Android 11+. While the YouTube seek bar is visible, the skipper takes a screenshot, finds the yellow ad-break markers on the bar,
converts them to times using the player's time labels, and cuts YouTube's connection from 5 s before each marker for 7 s.
View ids (`time_bar`, `time_bar_current_time`) and the marker colour are best guesses; a toast shows "Ad markers at ..." when found.
