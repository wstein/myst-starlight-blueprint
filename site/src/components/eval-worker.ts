// eval-worker.ts — evaluates example JS off the main thread.
// A Worker has no DOM/window access, which is adequate isolation for TRUSTED
// documentation examples. It is NOT a security boundary for untrusted code.
self.onmessage = (e: MessageEvent<string>) => {
  const logs: string[] = [];
  const capture = (...args: unknown[]) =>
    logs.push(args.map((a) => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' '));
  const sandboxConsole = { log: capture, info: capture, warn: capture, error: capture };

  try {
    // eslint-disable-next-line no-new-func
    const fn = new Function('console', `"use strict";\n${e.data}`);
    const result = fn(sandboxConsole);
    if (result !== undefined) capture(result);
    (self as unknown as Worker).postMessage({ ok: true, logs });
  } catch (err) {
    (self as unknown as Worker).postMessage({ ok: false, error: String(err) });
  }
};
