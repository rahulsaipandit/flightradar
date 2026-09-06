import { getSettings, saveSettings } from './settings.js';

// Mounts the OpenSky credentials form into `root`. Shared between
// sidepanel.html and options.html so the two surfaces can't drift apart.
//
// Zoom range, poll cadence, and filter/units/pin state are NOT here — the watch app doesn't
// expose those as settings either (zoom is the in-canvas stepper, poll cadence is fixed at
// 108s, filters/units/pin live in the radar screen itself). Only the OpenSky credentials, which
// have no watch-side equivalent, belong in this form.
export async function mountSettingsForm(root) {
  root.innerHTML = `
    <form id="settings-form">
      <fieldset>
        <legend>OpenSky account</legend>
        <label class="radio-row">
          <input type="radio" name="authMode" value="anonymous" />
          Anonymous (default) — no account needed, lower rate limit
        </label>
        <label class="radio-row">
          <input type="radio" name="authMode" value="authenticated" />
          Use my OpenSky account
        </label>
        <div id="credential-fields" class="credential-fields">
          <label>
            Client ID
            <input type="text" id="clientId" autocomplete="off" />
          </label>
          <label>
            Client secret
            <input type="password" id="clientSecret" autocomplete="off" />
          </label>
          <p class="hint">
            Create an API client at
            <a href="https://opensky-network.org/my-opensky/account" target="_blank" rel="noopener">
              opensky-network.org/my-opensky
            </a>. Credentials are stored only in this browser's local extension
            storage — never sent anywhere but OpenSky's own auth server.
          </p>
        </div>
      </fieldset>

      <button type="submit">Save</button>
      <span id="save-status" class="save-status" role="status"></span>
    </form>
  `;

  const form = root.querySelector('#settings-form');
  const credentialFields = root.querySelector('#credential-fields');
  const clientIdInput = root.querySelector('#clientId');
  const clientSecretInput = root.querySelector('#clientSecret');
  const saveStatus = root.querySelector('#save-status');

  function updateCredentialFieldsVisibility(mode) {
    credentialFields.classList.toggle('disabled', mode !== 'authenticated');
    clientIdInput.disabled = mode !== 'authenticated';
    clientSecretInput.disabled = mode !== 'authenticated';
  }

  const settings = await getSettings();
  form.querySelector(`input[name="authMode"][value="${settings.openskyAuthMode}"]`).checked = true;
  clientIdInput.value = settings.openskyClientId;
  clientSecretInput.value = settings.openskyClientSecret;
  updateCredentialFieldsVisibility(settings.openskyAuthMode);

  form.querySelectorAll('input[name="authMode"]').forEach((radio) => {
    radio.addEventListener('change', (e) => updateCredentialFieldsVisibility(e.target.value));
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const authMode = form.querySelector('input[name="authMode"]:checked').value;
    await saveSettings({
      openskyAuthMode: authMode,
      openskyClientId: clientIdInput.value.trim(),
      openskyClientSecret: clientSecretInput.value,
    });
    saveStatus.textContent = 'Saved';
    setTimeout(() => {
      saveStatus.textContent = '';
    }, 2000);
  });
}
