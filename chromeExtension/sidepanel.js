import { getSettings } from './lib/settings.js';
import { mountSettingsForm } from './lib/settingsForm.js';

async function refreshStatusLine() {
  const settings = await getSettings();
  const statusLine = document.getElementById('status-line');
  statusLine.textContent =
    settings.openskyAuthMode === 'authenticated' && settings.openskyClientId
      ? `Authenticated OpenSky tier (${settings.openskyClientId})`
      : 'Anonymous OpenSky tier';
}

document.getElementById('open-radar').addEventListener('click', () => {
  chrome.tabs.create({ url: chrome.runtime.getURL('radar.html') });
});

await mountSettingsForm(document.getElementById('settings-root'));
await refreshStatusLine();

document.getElementById('settings-form').addEventListener('submit', () => {
  // settingsForm.js saves asynchronously before this bubbles; give it a tick.
  setTimeout(refreshStatusLine, 50);
});
