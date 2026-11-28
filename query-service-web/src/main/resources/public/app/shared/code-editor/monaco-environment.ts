/**
 * Tells Monaco how to start its web workers: the editor worker (word suggestions, diff, links) and the JSON language
 * worker (validation, formatting). The worker entries are bundled by the Angular build from the `new Worker(new URL(...))`
 * expressions below. Without this, Monaco falls back to running worker code on the UI thread.
 */
const env = globalThis as typeof globalThis & { MonacoEnvironment?: { getWorker(workerId: string, label: string): Worker } };

env.MonacoEnvironment ??= {
  getWorker(_workerId: string, label: string): Worker {
    if (label === 'json') {
      return new Worker(new URL('./json.worker', import.meta.url), { type: 'module', name: label });
    }
    return new Worker(new URL('./editor.worker', import.meta.url), { type: 'module', name: label });
  }
};

/**
 * Monaco's stylesheet is built as its own file (angular.json: bundleName "monaco-editor", inject false), so pages
 * without an editor don't load it. Call this before creating an editor. The link goes first in <head> so the app's
 * own overrides still win; the query string changes with each build, since the file name has no hash.
 */
export function loadMonacoStyles(): void {
  if (typeof document === 'undefined' || document.querySelector('link[data-monaco-styles]')) {
    return;
  }
  const link = document.createElement('link');
  link.rel = 'stylesheet';
  link.dataset['monacoStyles'] = '';
  link.href = `monaco-editor.css?v=${encodeURIComponent(new URL(import.meta.url).pathname.split('/').pop() ?? '')}`;
  document.head.prepend(link);
}
