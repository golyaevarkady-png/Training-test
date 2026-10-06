const fs = require('fs');
const path = require('path');
const { loadApp, APP_PATH, HTML_PATH } = require('./setup/loadApp');

const CSS_PATH = path.join(path.dirname(HTML_PATH), 'style.css');
const COLOUR_LITERAL = /#[0-9a-fA-F]{3,8}\b|rgba?\(/;

function click(document, id) {
  document.getElementById(id).dispatchEvent(new window.Event('click', { bubbles: true }));
}

function theme() {
  return document.documentElement.getAttribute('data-theme');
}

/** Splits style.css into { selector, body } blocks (comments removed; the file has no nesting). */
function cssBlocks() {
  const css = fs.readFileSync(CSS_PATH, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
  const blocks = [];
  const re = /([^{}]+)\{([^}]*)\}/g;
  let match;
  while ((match = re.exec(css)) !== null) {
    blocks.push({ selector: match[1].trim(), body: match[2] });
  }
  return blocks;
}

function isThemeBlock(selector) {
  return /:root|\[data-theme=/.test(selector);
}

function variablesIn(selectorTest) {
  return cssBlocks()
    .filter((b) => selectorTest(b.selector))
    .flatMap((b) => Array.from(b.body.matchAll(/(--[\w-]+)\s*:/g), (m) => m[1]));
}

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
});

afterEach(() => {
  delete window.matchMedia;
});

describe('theme toggle (AC-1)', () => {
  test('is a button in the header', async () => {
    const { document } = await loadApp();
    const toggle = document.getElementById('theme-toggle');
    expect(toggle).not.toBeNull();
    expect(toggle.tagName).toBe('BUTTON');
    expect(document.getElementById('app-header').contains(toggle)).toBe(true);
  });

  test('clicking switches dark to light and back', async () => {
    const { document } = await loadApp();
    expect(theme()).toBe('dark');
    click(document, 'theme-toggle');
    expect(theme()).toBe('light');
    click(document, 'theme-toggle');
    expect(theme()).toBe('dark');
  });

  test('the label names the theme a click will give', async () => {
    const { document } = await loadApp();
    const toggle = document.getElementById('theme-toggle');
    expect(toggle.textContent.toLowerCase()).toContain('light');
    click(document, 'theme-toggle');
    expect(toggle.textContent.toLowerCase()).toContain('dark');
  });
});

describe('theme through data-theme and CSS variables (AC-2)', () => {
  test('data-theme on <html> is light or dark after load', async () => {
    await loadApp();
    expect(['light', 'dark']).toContain(theme());
  });

  test('every colour in style.css is defined in a theme variable block', () => {
    const offenders = cssBlocks()
      .filter((b) => !isThemeBlock(b.selector) && COLOUR_LITERAL.test(b.body))
      .map((b) => b.selector);
    expect(offenders).toEqual([]);
  });

  test('both charts take their bar, label and value colours from variables', () => {
    const blocks = cssBlocks();
    ['.chart-svg .bar', '.chart-svg .bar.warn', '.chart-svg .bar-label', '.chart-svg .bar-value'].forEach((selector) => {
      const block = blocks.find((b) => b.selector === selector);
      expect(block).toBeDefined();
      expect(block.body).toMatch(/fill:\s*var\(--/);
    });
  });

  test('the dark theme defines every variable the light theme defines', () => {
    const light = new Set(variablesIn((s) => /:root|\[data-theme="light"\]/.test(s)));
    const dark = new Set(variablesIn((s) => /\[data-theme="dark"\]/.test(s)));
    expect(light.size).toBeGreaterThan(0);
    expect([...light].filter((v) => !dark.has(v))).toEqual([]);
  });

  test('app.js contains no colour values', () => {
    expect(fs.readFileSync(APP_PATH, 'utf8')).not.toMatch(COLOUR_LITERAL);
  });
});

describe('theme persistence (AC-3)', () => {
  test('clicking stores the new theme in localStorage', async () => {
    const { document } = await loadApp();
    click(document, 'theme-toggle');
    expect(window.localStorage.getItem('ops-theme')).toBe('light');
    click(document, 'theme-toggle');
    expect(window.localStorage.getItem('ops-theme')).toBe('dark');
  });

  test('a stored theme is restored on load', async () => {
    window.localStorage.setItem('ops-theme', 'light');
    const { document } = await loadApp();
    expect(theme()).toBe('light');
    expect(document.getElementById('theme-toggle').textContent.toLowerCase()).toContain('dark');
  });
});

describe('default theme (AC-4)', () => {
  test('starts dark when nothing is stored, even if the OS prefers light', async () => {
    window.matchMedia = jest.fn((query) => ({
      matches: query.includes('light'),
      media: query,
      addEventListener() {},
      removeEventListener() {}
    }));
    await loadApp();
    expect(theme()).toBe('dark');
  });

  test('starts dark when the stored value is not a theme', async () => {
    window.localStorage.setItem('ops-theme', 'purple');
    await loadApp();
    expect(theme()).toBe('dark');
  });
});

describe('theme without the API', () => {
  test('the toggle works when the health check fails', async () => {
    const { document } = await loadApp({ failing: ['/api/health'] });
    expect(theme()).toBe('dark');
    click(document, 'theme-toggle');
    expect(theme()).toBe('light');
  });
});
