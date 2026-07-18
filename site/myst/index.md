---
title: The Blueprint
description: A reference architecture for docs with a single source of truth that still ships an interactive site — proven by rendering itself.
exports:
  - format: typst
    template: lapreprint-typst
    output: exports/index.pdf
---

Most documentation stacks make you choose: author in a simple, structured
format and get a static site, or hand-write MDX/HTML and get interactivity.
This project is a reference architecture for **not choosing** — MyST Markdown
stays the single source of truth, and a purpose-built [Kotlin Multiplatform
transpiler](./tool.md) turns its resolved AST into native Starlight pages,
interactivity included.

The page you're reading is the proof, not a pitch: everything below was
authored in MyST, transpiled to MDX by that tool, and built by Astro
Starlight — the pipeline demonstrating itself with its own output.

**[Download this site as a PDF ↓](/blueprint.pdf)** — the same MyST source,
exported through `mystmd`'s Typst pipeline and merged in CI; no separate
content to maintain.

## What "native" buys you

The transpiler doesn't drop down to raw HTML unless it has to. MyST
admonitions become Starlight's own `<Aside>` component — same theme, same
dark-mode behavior, no separate CSS to maintain:

:::{note}
This admonition is authored as a MyST directive (`:::{note}`). The transpiler
rewrites it to Starlight's native `<Aside>`, so it inherits Starlight's
styling with no shim.
:::

:::{danger}
MyST has more admonition kinds than Starlight has aside types. They collapse
through a table the tool owns — `danger` and `error` both map to `danger`.
See [the tool page](./tool.md) for the full mapping, demonstrated live.
:::

## Interactivity, without leaving MyST

A plain code fence stays a plain code fence — Expressive Code, same frame and
copy button as any other Starlight site. But MyST's `js-eval` fences are
special-cased: the transpiler swaps them for a `<CodeMirrorEval>` island, a
sandboxed Web Worker running underneath a real editor. No React, nothing
handwritten in the page source below the fence.

```js-eval
const fib = n => n < 2 ? n : fib(n - 1) + fib(n - 2);
console.log([...Array(10)].map((_, i) => fib(i)).join(', '));
```

Edit the code above and press Run — it evaluates in-browser, off the main
thread.

```js
// A normal fenced block goes through Expressive Code, exactly like the rest of
// the site — same frame, same copy button, same theme.
export const answer = 42;
```

## Next

[Read how the transpiler works →](./tool.md) — the native-vs-fallback
contract, the four escaping channels that keep MDX generation from breaking,
and why the tool compiles to both a JVM CLI and a JS playground from one
shared core.
