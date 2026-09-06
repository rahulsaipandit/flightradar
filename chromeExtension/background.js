import { getSettings } from './lib/settings.js';

chrome.runtime.onInstalled.addListener(async () => {
  // Ensure defaults exist so the side panel/options page has something to
  // read on first load without special-casing "no settings yet".
  await getSettings().then((s) => chrome.storage.local.set(s));

  chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});
});
