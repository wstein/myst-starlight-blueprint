---
title: The Blueprint
description: This page is written in MyST and rendered by the pipeline it documents.
---

# The blueprint renders itself

Everything you are reading was authored in **MyST Markdown**, transpiled to MDX
by a Kotlin Multiplatform tool, and built by Astro Starlight. The page is the
pipeline's own working example.

:::{note}
This admonition is authored as a MyST directive. The transpiler rewrites it to
Starlight's native `<Aside>`, so it inherits Starlight's styling with no shim.
:::

:::{danger}
MyST has more admonition kinds than Starlight has aside types. They collapse
through a table the tool owns — `danger` and `error` both map to `danger`.
:::

## Live evaluation

The block below is a MyST `js-eval` code block. The transpiler maps it to a
CodeMirror island with a sandboxed Web Worker — edit it and press Run.

```js-eval
const fib = n => n < 2 ? n : fib(n - 1) + fib(n - 2);
console.log([...Array(10)].map((_, i) => fib(i)).join(', '));
```

## Ordinary code stays native

```js
// A normal fenced block goes through Expressive Code, exactly like the rest of
// the site — same frame, same copy button, same theme.
export const answer = 42;
```
