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

export {};
