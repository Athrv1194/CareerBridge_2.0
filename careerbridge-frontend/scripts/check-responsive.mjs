#!/usr/bin/env node
/**
 * Responsive overflow probe.
 *
 * Walks every route at every target device width and fails on any element wider than
 * the viewport -- the machine-checkable half of "does it look good on a phone".
 *
 * No new dependencies: drives an already-installed Chrome over the DevTools Protocol
 * using Node's built-in WebSocket (Node 22+). Playwright would do the same thing and
 * cost a ~300MB browser download.
 *
 *   node scripts/check-responsive.mjs                  # all routes, all widths
 *   node scripts/check-responsive.mjs --route=/         # one route
 *   node scripts/check-responsive.mjs --width=344       # one width
 *   node scripts/check-responsive.mjs --base=http://localhost:5173
 *
 * Exits 1 if anything overflows, so it works as a CI gate.
 */
import { spawn } from 'node:child_process';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { existsSync } from 'node:fs';

// Widths are the Chrome DevTools device presets we support, narrowest first.
// 344 = Galaxy Z Fold 5 folded, the narrowest thing anyone will open this on.
const WIDTHS = [344, 360, 375, 390, 412, 414, 430, 440, 540, 768, 820, 904, 1024, 1280];

// Public routes render without a token. Everything else redirects to /login when
// unauthenticated -- still worth probing, since the login screen must fit too, but a
// real pass over the app shell needs TOKEN= set (see readme note at the bottom).
const ROUTES = [
  '/', '/login', '/register', '/forgot-password', '/set-password', '/plans',
  '/register-institution', '/onboarding', '/assessment', '/recommendations',
  '/roadmap', '/dashboard', '/opportunities', '/profile', '/resume', '/coach',
  '/mentors', '/notifications', '/college-dashboard', '/placement-console',
  '/recruiter-console', '/candidate/1', '/mentor-console', '/super-admin',
];

const CHROME_CANDIDATES = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  `${process.env.LOCALAPPDATA}/Google/Chrome/Application/chrome.exe`,
  '/usr/bin/google-chrome',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
];

const arg = (name, fallback) => {
  const hit = process.argv.find((a) => a.startsWith(`--${name}=`));
  return hit ? hit.slice(name.length + 3) : fallback;
};

const BASE = arg('base', 'http://localhost');
const onlyRoute = arg('route', null);
const onlyWidth = arg('width', null);
const routes = onlyRoute ? [onlyRoute] : ROUTES;
const widths = onlyWidth ? [Number(onlyWidth)] : WIDTHS;

/**
 * Runs in the page. Reports every element that sticks out past the viewport, plus the
 * nearest ancestor chain, so the output names the thing to fix rather than the leaf
 * that happened to be pushed.
 */
const PROBE = `(() => {
  const vw = document.documentElement.clientWidth;
  const seen = new Map();
  for (const el of document.querySelectorAll('body *')) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) continue;
    const overhang = Math.round(r.right - vw);
    const tooWide = Math.round(r.width - vw);
    if (overhang <= 1 && tooWide <= 1) continue;
    const cs = getComputedStyle(el);
    // An element inside a deliberately scrollable box is not a page-level bug.
    let p = el.parentElement, inScroller = false;
    while (p && p !== document.body) {
      const pcs = getComputedStyle(p);
      if (pcs.overflowX === 'auto' || pcs.overflowX === 'scroll' || pcs.overflowX === 'hidden') { inScroller = true; break; }
      p = p.parentElement;
    }
    if (inScroller) continue;
    const key = el.tagName + '.' + (el.className && el.className.toString ? el.className.toString().trim().slice(0, 60) : '');
    const entry = {
      sel: key,
      width: Math.round(r.width),
      overhang,
      grid: cs.display === 'grid' ? cs.gridTemplateColumns.slice(0, 70) : '',
      fontSize: cs.fontSize,
      text: (el.textContent || '').trim().replace(/\\s+/g, ' ').slice(0, 42),
    };
    if (!seen.has(key)) seen.set(key, entry);
  }
  return JSON.stringify({
    vw,
    scrollW: document.documentElement.scrollWidth,
    docOverflow: document.documentElement.scrollWidth - vw,
    offenders: [...seen.values()].sort((a, b) => b.overhang - a.overhang).slice(0, 6),
  });
})()`;

function chromePath() {
  const found = CHROME_CANDIDATES.find((p) => p && existsSync(p));
  if (!found) {
    console.error('Chrome not found. Pass one of:', CHROME_CANDIDATES.join(', '));
    process.exit(2);
  }
  return found;
}

async function connect(port) {
  // Chrome needs a moment to write its debugging port; poll rather than sleep blindly.
  for (let i = 0; i < 60; i += 1) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/json/version`);
      const { webSocketDebuggerUrl } = await res.json();
      if (webSocketDebuggerUrl) return webSocketDebuggerUrl;
    } catch { /* not up yet */ }
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error('Chrome did not expose a debugging endpoint within 15s');
}

function cdpSession(ws) {
  let nextId = 1;
  const pending = new Map();
  const events = new Map();
  ws.addEventListener('message', (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      if (msg.error) reject(new Error(msg.error.message));
      else resolve(msg.result);
    } else if (msg.method && events.has(msg.method)) {
      events.get(msg.method).forEach((fn) => fn(msg.params));
      events.set(msg.method, []);
    }
  });
  return {
    send(method, params = {}, sessionId) {
      const id = nextId += 1;
      return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
        ws.send(JSON.stringify({ id, method, params, sessionId }));
      });
    },
    once(method) {
      return new Promise((resolve) => {
        events.set(method, [...(events.get(method) || []), resolve]);
      });
    },
  };
}

async function main() {
  const port = 9222 + Math.floor(Math.random() * 500);
  const userDataDir = await mkdtemp(join(tmpdir(), 'cb-resp-'));
  const chrome = spawn(chromePath(), [
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${userDataDir}`,
    '--headless=new',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-extensions',
    '--hide-scrollbars', // otherwise a 15px scrollbar counts as overflow on every page
    'about:blank',
  ], { stdio: 'ignore' });

  const cleanup = async () => {
    chrome.kill();
    await rm(userDataDir, { recursive: true, force: true }).catch(() => {});
  };

  let failures = 0;
  let checked = 0;

  try {
    const wsUrl = await connect(port);
    const ws = new WebSocket(wsUrl);
    await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
    const cdp = cdpSession(ws);

    const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
    await cdp.send('Page.enable', {}, sessionId);
    await cdp.send('Runtime.enable', {}, sessionId);

    for (const width of widths) {
      await cdp.send('Emulation.setDeviceMetricsOverride', {
        width, height: 900, deviceScaleFactor: 1, mobile: width < 768,
      }, sessionId);

      for (const route of routes) {
        const loaded = cdp.once('Page.loadEventFired');
        await cdp.send('Page.navigate', { url: BASE + route }, sessionId);
        await Promise.race([loaded, new Promise((r) => setTimeout(r, 8000))]);
        // Let fonts settle and any reveal/IntersectionObserver animation land.
        await new Promise((r) => setTimeout(r, 450));

        const { result } = await cdp.send('Runtime.evaluate', {
          expression: PROBE, returnByValue: true, awaitPromise: false,
        }, sessionId);

        checked += 1;
        const data = JSON.parse(result.value);
        const bad = data.docOverflow > 1 || data.offenders.length > 0;
        if (bad) {
          failures += 1;
          console.log(`\nFAIL  ${width}px  ${route}`);
          console.log(`      page scrolls ${data.docOverflow}px past the viewport`);
          for (const o of data.offenders) {
            const grid = o.grid ? `  grid=[${o.grid}]` : '';
            console.log(`      +${o.overhang}px  ${o.sel}  w=${o.width}${grid}`);
            if (o.text) console.log(`               "${o.text}"`);
          }
        } else {
          process.stdout.write('.');
        }
      }
    }
  } finally {
    await cleanup();
  }

  console.log(`\n\n${checked - failures}/${checked} route x width combinations fit.`);
  if (failures) {
    console.log(`${failures} failing. Each FAIL above names the widest offending element.`);
    process.exit(1);
  }
  console.log('No horizontal overflow anywhere.');
}

main().catch((err) => { console.error(err); process.exit(2); });
