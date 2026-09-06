// Port of kmp/wearApp's RadarViewModel + RadarScreen (Compose) to canvas + vanilla DOM.
// Every constant/color/formula below is taken directly from that source so this matches the
// watch app's behavior and look, not an approximation of it.

import { getSettings } from './lib/settings.js';
import { fetchNearbyAircraft, FETCH_RADIUS_KM } from './lib/opensky.js';
import {
  boundingBox,
  distanceAndBearing,
  projectOrNull,
  projectPoint,
  offsetGeoPoint,
  targetPosition,
} from './lib/radarMath.js';
import { categoriesFor } from './lib/aircraftClassification.js';
import { lookupAircraftMetadata } from './lib/aircraftMetadata.js';

// ---- Colors (RadarScreen.kt L84-109) ----
const GRID_GREEN = '#00A000';
const BRIGHT_GREEN = '#00FF00';
const LABEL_GREEN = '#00E000';
const MILITARY_GREEN = '#4B5320';
const AIRLINER_YELLOW = '#FFD700';
const PRIVATE_RED = '#E53935';
const CALLSIGN_WHITE = '#FFFFFF';
const INFO_BLUE = '#4FC3F7';
const CLIMB_ORANGE = '#FFA000';
const PIN_PURPLE = '#CE93D8';
const GPS_BLUE = '#2196F3';

// ---- Constants (RadarViewModel.kt / RadarScreen.kt) ----
const DEFAULT_DISPLAY_RANGE_KM = 20.0;
const MIN_DISPLAY_RANGE_KM = 5.0;
const MAX_DISPLAY_RANGE_KM = FETCH_RADIUS_KM; // 200 — same superset fetched and the zoom ceiling
const ZOOM_STEP_KM = 5.0;
const KM_TO_MILES = 0.621371;
const HIT_AREA_PADDING_PX = 12;
const GRID_RING_COUNT = 7;
const SWEEP_TRAIL_SEGMENTS = 48;
const SWEEP_TRAIL_SPAN_RAD = Math.PI / 2;
const SWEEP_PERIOD_MS = 4000;
const POLL_INTERVAL_GUEST_MS = 108_000;
const POLL_INTERVAL_RATE_LIMITED_MS = 60_000;
const POLL_INTERVAL_ERROR_MS = 15_000;

const ICON_FILES = {
  helicopter: 'aircraft-icons/aircraft_helicopter.png',
  military: 'aircraft-icons/aircraft_military.png',
  airliner: 'aircraft-icons/aircraft_airliner.png',
  private: 'aircraft-icons/aircraft_private.png',
};
const ICON_BASE_ROTATION_DEG = { helicopter: 90, military: 0, airliner: 0, private: 0 };

// ---- Label helpers (RadarScreen.kt L118-139, L303-306) ----
function altitudeLabel(aircraft) {
  return aircraft.altitudeMeters != null ? `${Math.round(aircraft.altitudeMeters * 3.28084)}ft` : 'GND';
}
function speedLabel(aircraft) {
  return aircraft.velocityMs != null ? `${Math.round(aircraft.velocityMs * 1.94384)}kt` : '?';
}
function climbArrow(aircraft) {
  const rate = aircraft.verticalRateMs;
  if (rate == null) return '';
  if (rate > 0.5) return '↑';
  if (rate < -0.5) return '↓';
  return '';
}
function headingCompass(trueTrackDeg) {
  if (trueTrackDeg == null) return '?';
  const directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
  const index = Math.round((((trueTrackDeg % 360) + 360) % 360) / 45) % 8;
  return directions[index];
}
function distanceLabel(km, useMiles) {
  return useMiles ? `${Math.round(km * KM_TO_MILES)} mi` : `${Math.round(km)} km`;
}

// ---- Icon shape/tint (RadarScreen.kt L662-675) ----
function aircraftIconKey(categories) {
  if (categories.has('HELICOPTER')) return 'helicopter';
  if (categories.has('MILITARY')) return 'military';
  if (categories.has('AIRLINER')) return 'airliner';
  return 'private';
}
function aircraftTint(categories) {
  if (categories.has('MILITARY')) return MILITARY_GREEN;
  if (categories.has('AIRLINER')) return AIRLINER_YELLOW;
  return PRIVATE_RED;
}

// ---- DOM ----
const container = document.getElementById('radar-container');
const canvas = document.getElementById('radar-canvas');
const ctx = canvas.getContext('2d');
const stateMessage = document.getElementById('state-message');
const hamburgerBtn = document.getElementById('hamburger-btn');
const filterMenu = document.getElementById('filter-menu');
const zoomStepper = document.getElementById('zoom-stepper');
const zoomOutBtn = document.getElementById('zoom-out');
const zoomInBtn = document.getElementById('zoom-in');
const zoomLabel = document.getElementById('zoom-label');
const centerReset = document.getElementById('center-reset');
const detailOverlay = document.getElementById('detail-overlay');
const settingsCornerBtn = document.getElementById('settings-corner');

settingsCornerBtn.addEventListener('click', () => chrome.runtime.openOptionsPage());

// ---- State (mirrors RadarViewModel) ----
const state = {
  uiState: 'permission-required', // 'permission-required' | 'loading' | 'data'
  gpsLocation: null, // {latitude, longitude}
  panOverride: null, // {latitude, longitude} | null
  displayRangeKm: DEFAULT_DISPLAY_RANGE_KM,
  activeFilters: new Set(), // empty = show all
  useMiles: false,
  pinnedIcao24: null,
  metadataByIcao24: new Map(), // icao24 -> {status:'loading'|'loaded', metadata}
  rawAircraft: [], // last poll's Aircraft[], already filtered to the fetch-time center
  targets: [], // [{aircraft, distanceKm, bearingRad}] reprojected against the *current* center
  selectedTarget: null,
};

function effectiveCenter() {
  return state.panOverride ?? state.gpsLocation;
}
function isPanned() {
  return state.panOverride != null;
}

function reprojectTargets() {
  const center = effectiveCenter();
  state.targets = center
    ? state.rawAircraft.map((a) => projectOrNull(center, a, MAX_DISPLAY_RANGE_KM)).filter(Boolean)
    : [];
}

function visibleTargets() {
  return state.targets.filter(
    (t) =>
      t.distanceKm <= state.displayRangeKm &&
      (state.activeFilters.size === 0 ||
        [...categoriesFor(t.aircraft)].some((c) => state.activeFilters.has(c))),
  );
}

function myLocationMarker() {
  if (state.panOverride != null && state.gpsLocation != null) {
    return projectPoint(state.panOverride, state.gpsLocation);
  }
  return null;
}

// ---- Zoom / pan / filters / units / pin (RadarViewModel.kt) ----
function zoomBy(deltaKm) {
  state.displayRangeKm = Math.min(
    MAX_DISPLAY_RANGE_KM,
    Math.max(MIN_DISPLAY_RANGE_KM, state.displayRangeKm + deltaKm),
  );
  renderChrome();
}
function panBy(eastwardKm, northwardKm) {
  const base = state.panOverride ?? state.gpsLocation;
  if (!base) return;
  state.panOverride = offsetGeoPoint(base, eastwardKm, northwardKm);
  reprojectTargets();
  renderChrome();
}
function resetView() {
  state.panOverride = null;
  state.displayRangeKm = DEFAULT_DISPLAY_RANGE_KM;
  reprojectTargets();
  renderChrome();
}
function toggleFilter(category) {
  if (state.activeFilters.has(category)) state.activeFilters.delete(category);
  else state.activeFilters.add(category);
  renderFilterMenu();
}
function toggleUnits() {
  state.useMiles = !state.useMiles;
  renderFilterMenu();
  renderChrome();
}
function togglePin(icao24) {
  state.pinnedIcao24 = state.pinnedIcao24 === icao24 ? null : icao24;
  renderDetailOverlay();
}
function requestMetadata(icao24) {
  if (state.metadataByIcao24.has(icao24)) return;
  state.metadataByIcao24.set(icao24, { status: 'loading' });
  lookupAircraftMetadata(icao24).then((metadata) => {
    state.metadataByIcao24.set(icao24, { status: 'loaded', metadata });
    renderDetailOverlay();
  });
}

// ---- Chrome (zoom stepper label + center-reset color) ----
function renderChrome() {
  zoomLabel.textContent = distanceLabel(state.displayRangeKm, state.useMiles);
  const awayFromDefault = isPanned() || state.displayRangeKm !== DEFAULT_DISPLAY_RANGE_KM;
  centerReset.classList.toggle('away-from-default', awayFromDefault);
}

// ---- Filter menu (RadarScreen.kt L268-301) ----
const FILTER_OPTIONS = [
  ['MILITARY', 'Military'],
  ['HELICOPTER', 'Helicopters'],
  ['AIRLINER', 'Airliners'],
  ['PRIVATE', 'Private'],
];

function renderFilterMenu() {
  filterMenu.innerHTML = '';
  for (const [category, label] of FILTER_OPTIONS) {
    const row = document.createElement('div');
    row.className = 'filter-row';
    const active = state.activeFilters.has(category);
    row.textContent = (active ? '✓ ' : '  ') + label;
    row.addEventListener('click', () => toggleFilter(category));
    filterMenu.appendChild(row);
  }
  const unitsRow = document.createElement('div');
  unitsRow.className = 'filter-row';
  unitsRow.textContent = `Units: ${state.useMiles ? 'mi' : 'km'}`;
  unitsRow.addEventListener('click', toggleUnits);
  filterMenu.appendChild(unitsRow);
}
renderFilterMenu();

hamburgerBtn.addEventListener('click', () => {
  filterMenu.hidden = !filterMenu.hidden;
});
zoomOutBtn.addEventListener('click', () => zoomBy(-ZOOM_STEP_KM));
zoomInBtn.addEventListener('click', () => zoomBy(ZOOM_STEP_KM));
centerReset.addEventListener('click', resetView);

// ---- Detail overlay (RadarScreen.kt L345-417) ----
function renderDetailOverlay() {
  const target = state.selectedTarget;
  if (!target) {
    detailOverlay.hidden = true;
    detailOverlay.innerHTML = '';
    return;
  }
  const isPinned = target.aircraft.icao24 === state.pinnedIcao24;
  const lookup = state.metadataByIcao24.get(target.aircraft.icao24);
  let metadataLine;
  if (!lookup || lookup.status === 'loading') {
    metadataLine = 'Looking up registration…';
  } else if (lookup.metadata) {
    const parts = [lookup.metadata.model, lookup.metadata.registration].filter(Boolean);
    metadataLine = parts.length ? parts.join(' | ') : 'No data found';
  } else {
    metadataLine = 'No data found';
  }

  const arrow = climbArrow(target.aircraft);
  detailOverlay.innerHTML = `
    <div class="detail-box">
      <div class="detail-callsign ${isPinned ? 'pinned' : ''}">${escapeHtml(target.aircraft.callsign)}</div>
      <div class="detail-line-blue">${escapeHtml(`${altitudeLabel(target.aircraft)} ${arrow}`.trim())} | ${escapeHtml(speedLabel(target.aircraft))}</div>
      <div class="detail-line-green">${escapeHtml(headingCompass(target.aircraft.trueTrackDeg))} | ${escapeHtml(distanceLabel(target.distanceKm, state.useMiles))} away</div>
      <div class="detail-line-metadata">${escapeHtml(metadataLine)}</div>
      <div class="detail-buttons">
        <button class="detail-button ${isPinned ? 'pinned' : ''}" id="pin-btn">${isPinned ? 'PINNED' : 'PIN'}</button>
        <button class="detail-button" id="close-btn">CLOSE</button>
      </div>
    </div>
  `;
  detailOverlay.hidden = false;
  detailOverlay.querySelector('#pin-btn').addEventListener('click', () => togglePin(target.aircraft.icao24));
  detailOverlay.querySelector('#close-btn').addEventListener('click', () => {
    state.selectedTarget = null;
    renderDetailOverlay();
  });
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function selectTarget(target) {
  state.selectedTarget = target;
  renderDetailOverlay();
  requestMetadata(target.aircraft.icao24);
}

// ---- Icon loading + tinting (RadarScreen.kt L94-102, L676-698) ----
const iconImages = {}; // key -> HTMLImageElement
const tintedIconCache = new Map(); // `${key}|${tint}` -> HTMLCanvasElement

function loadIcons() {
  return Promise.all(
    Object.entries(ICON_FILES).map(
      ([key, path]) =>
        new Promise((resolve) => {
          const img = new Image();
          img.onload = () => {
            iconImages[key] = img;
            resolve();
          };
          img.src = chrome.runtime.getURL(path);
        }),
    ),
  );
}

function tintedIcon(key, tint) {
  const cacheKey = `${key}|${tint}`;
  if (tintedIconCache.has(cacheKey)) return tintedIconCache.get(cacheKey);
  const img = iconImages[key];
  const off = document.createElement('canvas');
  off.width = img.width;
  off.height = img.height;
  const octx = off.getContext('2d');
  octx.drawImage(img, 0, 0);
  octx.globalCompositeOperation = 'source-in';
  octx.fillStyle = tint;
  octx.fillRect(0, 0, off.width, off.height);
  tintedIconCache.set(cacheKey, off);
  return off;
}

// ---- Canvas sizing ----
let canvasWidth = 0;
let canvasHeight = 0;
function resizeCanvas() {
  const dpr = window.devicePixelRatio || 1;
  canvasWidth = container.clientWidth;
  canvasHeight = container.clientHeight;
  canvas.width = canvasWidth * dpr;
  canvas.height = canvasHeight * dpr;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  positionChromeOverlays();
}
window.addEventListener('resize', resizeCanvas);

// The watch's fixed top:30dp/bottom:32dp offsets were measured against a square screen where
// the radar circle nearly fills it edge-to-edge. Our canvas is a wide rectangle, so the circle
// (min(width,height)-based) no longer touches the container's top/bottom edges the same way —
// anchoring the hamburger/stepper to the *circle's* own edge (26px inset, the watch's actual
// button-center-to-circle-edge distance) keeps them sitting just inside the ring regardless of
// window shape, instead of the fixed screen-edge offset pushing them outside it.
const CIRCLE_EDGE_INSET_PX = 26;
const HAMBURGER_HEIGHT_PX = 28;
const STEPPER_HEIGHT_PX = 28;
const FILTER_MENU_GAP_PX = 4;

function positionChromeOverlays() {
  const cy = canvasHeight / 2;
  const maxRadius = (Math.min(canvasWidth, canvasHeight) / 2) * 0.92;
  const circleTopY = cy - maxRadius;
  const circleBottomY = cy + maxRadius;

  const hamburgerCenterY = circleTopY + CIRCLE_EDGE_INSET_PX;
  const hamburgerTopY = hamburgerCenterY - HAMBURGER_HEIGHT_PX / 2;
  hamburgerBtn.style.top = `${hamburgerTopY}px`;
  filterMenu.style.top = `${hamburgerTopY + HAMBURGER_HEIGHT_PX + FILTER_MENU_GAP_PX}px`;

  const stepperCenterY = circleBottomY - CIRCLE_EDGE_INSET_PX;
  zoomStepper.style.top = `${stepperCenterY - STEPPER_HEIGHT_PX / 2}px`;
}

// ---- Grid (RadarScreen.kt L615-639) ----
function drawGrid(cx, cy, maxRadius) {
  for (let ring = 1; ring <= GRID_RING_COUNT; ring++) {
    const fraction = ring / GRID_RING_COUNT;
    ctx.beginPath();
    ctx.arc(cx, cy, maxRadius * fraction, 0, Math.PI * 2);
    ctx.strokeStyle = ring === GRID_RING_COUNT ? BRIGHT_GREEN : GRID_GREEN;
    ctx.lineWidth = 1.5;
    ctx.stroke();
  }
  ctx.strokeStyle = GRID_GREEN;
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(cx, cy - maxRadius);
  ctx.lineTo(cx, cy + maxRadius);
  ctx.moveTo(cx - maxRadius, cy);
  ctx.lineTo(cx + maxRadius, cy);
  ctx.stroke();

  const edgeMargin = 12;
  ctx.fillStyle = GRID_GREEN;
  ctx.font = '10px Roboto, sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText('N', cx, cy - maxRadius + edgeMargin);
  ctx.fillText('S', cx, cy + maxRadius - edgeMargin);
  ctx.fillText('E', cx + maxRadius - edgeMargin, cy);
  ctx.fillText('W', cx - maxRadius + edgeMargin, cy);
}

// ---- Sweep (RadarScreen.kt L641-660) ----
function drawSweep(cx, cy, maxRadius, sweepAngle) {
  for (let i = 0; i < SWEEP_TRAIL_SEGMENTS; i++) {
    const fraction = i / SWEEP_TRAIL_SEGMENTS;
    const trailAngle = sweepAngle - fraction * SWEEP_TRAIL_SPAN_RAD;
    const alpha = (1 - fraction) * 0.55;
    const tx = cx + maxRadius * Math.sin(trailAngle);
    const ty = cy - maxRadius * Math.cos(trailAngle);
    ctx.strokeStyle = hexWithAlpha(GRID_GREEN, alpha);
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(tx, ty);
    ctx.stroke();
  }
  const endX = cx + maxRadius * Math.sin(sweepAngle);
  const endY = cy - maxRadius * Math.cos(sweepAngle);
  ctx.strokeStyle = BRIGHT_GREEN;
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  ctx.moveTo(cx, cy);
  ctx.lineTo(endX, endY);
  ctx.stroke();
}
function hexWithAlpha(hex, alpha) {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

// ---- Target rendering (RadarScreen.kt L677-703) ----
function drawTarget(position, target, maxRadius) {
  const headingDeg = target.aircraft.trueTrackDeg ?? 0;
  const iconSpan = maxRadius * 0.26;
  const categories = categoriesFor(target.aircraft);
  const key = aircraftIconKey(categories);
  const tint = aircraftTint(categories);
  const baseRotation = ICON_BASE_ROTATION_DEG[key];

  const source = iconImages[key];
  const tinted = tintedIcon(key, tint);
  const aspect = source.width / source.height;
  const dstWidth = aspect >= 1 ? iconSpan : iconSpan * aspect;
  const dstHeight = aspect >= 1 ? iconSpan / aspect : iconSpan;

  ctx.save();
  ctx.translate(position.x, position.y);
  ctx.rotate(((headingDeg + baseRotation) * Math.PI) / 180);
  ctx.drawImage(tinted, -dstWidth / 2, -dstHeight / 2, dstWidth, dstHeight);
  ctx.restore();

  drawLabel(position, target, categories);
}

function drawLabel(position, target, categories) {
  const isPinned = target.aircraft.icao24 === state.pinnedIcao24;
  const arrow = climbArrow(target.aircraft);
  const x = position.x + 4;
  const line1Y = position.y - 12;
  const lineHeight = 13;

  ctx.textAlign = 'left';
  ctx.textBaseline = 'top';
  ctx.font = '11px Roboto, sans-serif';

  ctx.fillStyle = isPinned ? PIN_PURPLE : CALLSIGN_WHITE;
  ctx.fillText(target.aircraft.callsign, x, line1Y);

  let cursorX = x;
  const line2Y = line1Y + lineHeight;
  const segments = [{ text: altitudeLabel(target.aircraft), color: INFO_BLUE }];
  if (arrow) segments.push({ text: ` ${arrow}`, color: CLIMB_ORANGE });
  segments.push({ text: ' | ', color: LABEL_GREEN });
  segments.push({ text: speedLabel(target.aircraft), color: INFO_BLUE });
  for (const seg of segments) {
    ctx.fillStyle = seg.color;
    ctx.fillText(seg.text, cursorX, line2Y);
    cursorX += ctx.measureText(seg.text).width;
  }
}

// ---- Hit-testing (RadarScreen.kt L514-533) ----
function computeHitAreas(rendered, maxRadius) {
  const iconRadius = maxRadius * 0.11;
  const areas = [];
  for (const { target, position } of rendered) {
    ctx.font = '11px Roboto, sans-serif';
    const labelWidth = Math.max(
      ctx.measureText(target.aircraft.callsign).width,
      ctx.measureText(`${altitudeLabel(target.aircraft)} | ${speedLabel(target.aircraft)}`).width,
    );
    const labelLeft = position.x + 4;
    const labelTop = position.y - 12;
    const left = Math.min(position.x - iconRadius, labelLeft) - HIT_AREA_PADDING_PX;
    const top = Math.min(position.y - iconRadius, labelTop) - HIT_AREA_PADDING_PX;
    const right = Math.max(position.x + iconRadius, labelLeft + labelWidth) + HIT_AREA_PADDING_PX;
    const bottom = Math.max(position.y + iconRadius, labelTop + 26) + HIT_AREA_PADDING_PX;
    areas.push({ target, left, top, right, bottom });
  }
  return areas;
}

let currentHitAreas = [];

// ---- Main draw (RadarScreen.kt L586-605) ----
const sweepStart = performance.now();
function draw(now) {
  ctx.clearRect(0, 0, canvasWidth, canvasHeight);
  ctx.fillStyle = '#000000';
  ctx.fillRect(0, 0, canvasWidth, canvasHeight);

  if (state.uiState === 'data' || state.uiState === 'loading') {
    const cx = canvasWidth / 2;
    const cy = canvasHeight / 2;
    const maxRadius = (Math.min(canvasWidth, canvasHeight) / 2) * 0.92;
    const sweepAngle = (((now - sweepStart) % SWEEP_PERIOD_MS) / SWEEP_PERIOD_MS) * 2 * Math.PI;

    drawGrid(cx, cy, maxRadius);
    drawSweep(cx, cy, maxRadius, sweepAngle);

    const marker = myLocationMarker();
    if (marker && marker.distanceKm <= state.displayRangeKm) {
      const pixelRadius = Math.min(Math.max(marker.distanceKm / state.displayRangeKm, 0), 1) * maxRadius;
      const dotX = cx + pixelRadius * Math.sin(marker.bearingRad);
      const dotY = cy - pixelRadius * Math.cos(marker.bearingRad);
      ctx.fillStyle = GPS_BLUE;
      ctx.beginPath();
      ctx.arc(dotX, dotY, maxRadius * 0.035, 0, Math.PI * 2);
      ctx.fill();
    }

    const visible = visibleTargets();
    const rendered = visible.map((target) => ({
      target,
      position: targetPosition(cx, cy, maxRadius, target.distanceKm, target.bearingRad, state.displayRangeKm),
    }));
    for (const { target, position } of rendered) {
      drawTarget(position, target, maxRadius);
    }
    currentHitAreas = computeHitAreas(rendered, maxRadius);
  }

  requestAnimationFrame(draw);
}

// ---- Pointer input: tap vs drag, matching RadarScreen.kt's touch-slop logic (L552-585) ----
const TOUCH_SLOP_PX = 6;
let pointerDown = null; // {x, y}
let dragging = false;

canvas.addEventListener('pointerdown', (e) => {
  if (state.uiState === 'permission-required') return;
  pointerDown = { x: e.offsetX, y: e.offsetY };
  dragging = false;
  canvas.setPointerCapture(e.pointerId);
});

canvas.addEventListener('pointermove', (e) => {
  if (!pointerDown) return;
  const dx = e.offsetX - pointerDown.x;
  const dy = e.offsetY - pointerDown.y;
  if (!dragging && Math.hypot(dx, dy) > TOUCH_SLOP_PX) {
    dragging = true;
  }
  if (dragging) {
    const maxRadius = (Math.min(canvasWidth, canvasHeight) / 2) * 0.92;
    const kmPerPixel = maxRadius > 0 ? state.displayRangeKm / maxRadius : 0;
    const movementX = e.movementX;
    const movementY = e.movementY;
    panBy(-movementX * kmPerPixel, movementY * kmPerPixel);
  }
});

canvas.addEventListener('pointerup', (e) => {
  if (!pointerDown) return;
  if (!dragging) {
    const hit = currentHitAreas.find(
      (a) => pointerDown.x >= a.left && pointerDown.x <= a.right && pointerDown.y >= a.top && pointerDown.y <= a.bottom,
    );
    if (hit) selectTarget(hit.target);
  }
  pointerDown = null;
  dragging = false;
});

// ---- Geolocation + OpenSky polling ----
function showState(text) {
  stateMessage.hidden = false;
  stateMessage.textContent = text;
}
function hideState() {
  stateMessage.hidden = true;
}
function showChrome(visible) {
  hamburgerBtn.hidden = !visible;
  zoomStepper.hidden = !visible;
  centerReset.hidden = !visible;
  if (!visible) filterMenu.hidden = true;
}

function requestLocation() {
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
      (err) => reject(err),
      { enableHighAccuracy: false, timeout: 15000 },
    );
  });
}

let pollTimer = null;
async function pollOnce() {
  const settings = await getSettings();
  const center = effectiveCenter();
  if (!center) return;
  try {
    const aircraft = await fetchNearbyAircraft(center, settings);
    state.rawAircraft = aircraft
      .map((a) => projectOrNull(center, a, MAX_DISPLAY_RANGE_KM))
      .filter(Boolean)
      .map((t) => t.aircraft);
    reprojectTargets();
    if (state.uiState !== 'data') {
      state.uiState = 'data';
      hideState();
    }
    schedulePoll(POLL_INTERVAL_GUEST_MS);
  } catch (err) {
    schedulePoll(POLL_INTERVAL_ERROR_MS);
  }
}
function schedulePoll(delayMs) {
  clearTimeout(pollTimer);
  pollTimer = setTimeout(pollOnce, delayMs);
}

async function main() {
  await loadIcons();
  resizeCanvas();
  requestAnimationFrame(draw);

  // Calling getCurrentPosition immediately triggers Chrome's native permission dialog directly —
  // no click needed first (unlike Android, the web doesn't require a user gesture for this).
  startLocationFlow();
  stateMessage.addEventListener('click', startLocationFlow);
}

async function startLocationFlow() {
  showState('GETTING LOCATION...');
  try {
    const location = await requestLocation();
    state.gpsLocation = location;
    state.uiState = 'loading';
    reprojectTargets();
    showChrome(true);
    renderChrome();
    pollOnce();

    navigator.geolocation.watchPosition(
      (pos) => {
        state.gpsLocation = { latitude: pos.coords.latitude, longitude: pos.coords.longitude };
        if (!isPanned()) reprojectTargets();
      },
      () => {},
      { enableHighAccuracy: false },
    );
  } catch (err) {
    showState('Location permission denied or unavailable.\nTap to try again.');
  }
}

main();
