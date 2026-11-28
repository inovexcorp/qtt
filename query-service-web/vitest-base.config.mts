// Vitest settings merged into `ng test` (angular.json: test.options.runnerConfig).
// Tests run in a real browser, like they did under Karma: Chrome (the installed Google Chrome, via Playwright's
// "chrome" channel, so no browser download is needed), headless.
import { defineConfig, type Plugin } from 'vitest/config';
import { playwright } from '@vitest/browser-playwright';

// ngx-markdown loads marked-katex-extension with a dynamic import, only when [katex] is used (it isn't). It's an
// optional peer we don't install; Vite's import analysis still tries to resolve it, so resolve it to an empty module.
const optionalPeers: Plugin = {
  name: 'qtt-optional-peers',
  enforce: 'pre',
  resolveId: id => id === 'marked-katex-extension' ? '\0qtt-empty-module' : null,
  load: id => id === '\0qtt-empty-module' ? 'export default null;' : null,
};

export default defineConfig({
  plugins: [optionalPeers],
  test: {
    browser: {
      enabled: true,
      headless: true,
      provider: playwright({ launchOptions: { channel: 'chrome' } }),
      instances: [{ browser: 'chromium' }],
      viewport: { width: 1280, height: 800 },
    },
  },
});
