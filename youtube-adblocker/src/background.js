// YouTube Ad Blocker - service worker
const RULESET_ID = "ad_rules";

async function syncRuleset(enabled) {
  await chrome.declarativeNetRequest.updateEnabledRulesets(
    enabled ? { enableRulesetIds: [RULESET_ID] } : { disableRulesetIds: [RULESET_ID] }
  );
  await chrome.action.setBadgeText({ text: enabled ? "" : "OFF" });
  await chrome.action.setBadgeBackgroundColor({ color: "#888" });
}

chrome.runtime.onInstalled.addListener(async () => {
  const { enabled = true } = await chrome.storage.local.get("enabled");
  await chrome.storage.local.set({ enabled });
  await syncRuleset(enabled);
});

chrome.runtime.onStartup.addListener(async () => {
  const { enabled = true } = await chrome.storage.local.get("enabled");
  await syncRuleset(enabled);
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes.enabled) {
    syncRuleset(changes.enabled.newValue !== false);
  }
});

chrome.runtime.onMessage.addListener((msg) => {
  if (msg && msg.type === "ad-blocked") {
    chrome.storage.local.get({ blockedCount: 0 }, ({ blockedCount }) => {
      chrome.storage.local.set({ blockedCount: blockedCount + 1 });
    });
  }
});
