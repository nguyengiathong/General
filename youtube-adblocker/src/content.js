// YouTube Ad Blocker - content script
// Tự động bỏ qua quảng cáo video, đóng lớp phủ quảng cáo và đếm số quảng cáo đã chặn.
(() => {
  "use strict";

  const SKIP_SELECTORS = [
    ".ytp-skip-ad-button",
    ".ytp-ad-skip-button",
    ".ytp-ad-skip-button-modern",
    ".ytp-ad-skip-button-slot button",
    ".ytp-ad-skip-button-container button",
    ".videoAdUiSkipButton",
  ].join(",");

  const CLOSE_OVERLAY_SELECTORS = [
    ".ytp-ad-overlay-close-button",
    ".ytp-ad-overlay-close-container",
  ].join(",");

  let enabled = true;
  let inAd = false;
  let mutedByUs = false;
  let savedRate = 1;

  function applyEnabledState() {
    const root = document.documentElement;
    if (!root) return;
    if (enabled) root.removeAttribute("data-ytab-off");
    else root.setAttribute("data-ytab-off", "");
  }

  chrome.storage.local.get({ enabled: true }, (res) => {
    enabled = res.enabled !== false;
    applyEnabledState();
  });

  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === "local" && changes.enabled) {
      enabled = changes.enabled.newValue !== false;
      applyEnabledState();
    }
  });

  function incrementCounter() {
    try {
      chrome.runtime.sendMessage({ type: "ad-blocked" });
    } catch (_) {
      // Tiện ích vừa được tải lại; bỏ qua.
    }
  }

  function clickAll(selector) {
    document.querySelectorAll(selector).forEach((el) => {
      if (el instanceof HTMLElement) el.click();
    });
  }

  function handleVideoAd() {
    const player = document.querySelector(".html5-video-player");
    if (!player) return;
    const video = player.querySelector("video");
    const adShowing =
      player.classList.contains("ad-showing") ||
      player.classList.contains("ad-interrupting");

    if (adShowing && video) {
      if (!inAd) {
        inAd = true;
        savedRate = video.playbackRate || 1;
        if (!video.muted) {
          video.muted = true;
          mutedByUs = true;
        }
        incrementCounter();
      }
      // Bấm nút "Bỏ qua" nếu có.
      clickAll(SKIP_SELECTORS);
      // Tua nhanh tới cuối quảng cáo.
      try {
        video.playbackRate = 16;
      } catch (_) {}
      if (Number.isFinite(video.duration) && video.duration > 0) {
        video.currentTime = Math.max(video.duration - 0.1, 0);
      }
      if (video.paused) video.play().catch(() => {});
    } else if (inAd) {
      inAd = false;
      if (video) {
        try {
          video.playbackRate = savedRate;
        } catch (_) {}
        if (mutedByUs) video.muted = false;
      }
      mutedByUs = false;
    }
  }

  function handleOverlays() {
    clickAll(CLOSE_OVERLAY_SELECTORS);

    // Đóng hộp thoại cảnh báo "trình chặn quảng cáo" nếu YouTube hiển thị.
    const enforcement = document.querySelector("ytd-enforcement-message-view-model");
    if (enforcement) {
      const dialog = enforcement.closest("tp-yt-paper-dialog") || enforcement;
      dialog.remove();
      document.querySelectorAll("tp-yt-iron-overlay-backdrop").forEach((b) => b.remove());
      const video = document.querySelector(".html5-video-player video");
      if (video && video.paused) video.play().catch(() => {});
    }
  }

  function tick() {
    if (!enabled) return;
    handleVideoAd();
    handleOverlays();
  }

  const observer = new MutationObserver(tick);
  function start() {
    observer.observe(document.documentElement, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ["class"],
    });
    tick();
  }

  if (document.documentElement) start();
  else document.addEventListener("DOMContentLoaded", start, { once: true });

  // Kiểm tra định kỳ để chắc chắn không bỏ sót.
  setInterval(tick, 300);
})();
