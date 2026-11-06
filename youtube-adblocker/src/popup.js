const toggle = document.getElementById("enabled");
const status = document.getElementById("status");
const count = document.getElementById("count");

function render({ enabled = true, blockedCount = 0 }) {
  toggle.checked = enabled;
  status.textContent = enabled ? "Đang bật" : "Đã tắt";
  count.textContent = blockedCount.toLocaleString("vi-VN");
}

chrome.storage.local.get({ enabled: true, blockedCount: 0 }, render);

chrome.storage.onChanged.addListener(() => {
  chrome.storage.local.get({ enabled: true, blockedCount: 0 }, render);
});

toggle.addEventListener("change", () => {
  chrome.storage.local.set({ enabled: toggle.checked });
});

document.getElementById("reset").addEventListener("click", () => {
  chrome.storage.local.set({ blockedCount: 0 });
});
