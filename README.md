# MyST → Starlight Blueprint

A documentation stack that **is its own worked example**. The docs are authored in
MyST Markdown, transpiled to MDX by a Kotlin Multiplatform tool, and built by Astro
Starlight into a static site that deploys to GitHub Pages. The site you get out is
the specification of the pipeline that produced it — blueprint and template in one.

This repository is the concrete form of a longer design discussion. The short
version of *why each piece is here* is below; the code is the rest of the argument.

## The pipeline

```
  site/myst/*.md          MyST source — the single source of truth (you edit this)
        │
        │  mystmd  (myst build --site)      parse + RESOLVE xrefs, numbering, TOC
        ▼
  _build/site/content/*.json      resolved MyST AST  (mdast-based)
        │
        │  KMP transpiler  (shared core → JVM CLI)     native-first, HTML-fallback
        ▼
  site/src/content/docs/*.mdx     generated MDX  — a BUILD ARTIFACT, never edited
        │
        │  astro build   (Starlight + MDX)             ← this step is the MDX compile gate
        ▼
  site/dist/                      static site → GitHub Pages
```

Everything downstream of the MyST source is regenerated on every build. The MDX is
gitignored on purpose: it is output, not source.

## Why this shape (the load-bearing decisions)

| Decision | Choice | Why |
|---|---|---|
| Web framework | **Astro Starlight** | Static-first, islands, Pagefind search, non-React |
| Structured source | **mystmd** (standalone, not Sphinx) | Semantic authoring; emits a resolved AST as JSON |
| Integration model | **Transpile to MDX**, don't inject HTML | MDX pages become *native* Starlight pages → shared theme, topbar, search, sidebar for free |
| Emit target | **MDX** (not Markdoc) | Human-reviewable output; escaping is solved, not hoped away |
| Tool language | **Kotlin Multiplatform** | One `commonMain` core → JVM **CLI** + JS **web**, from a single codebase |
| Live editor | **CodeMirror 6, vanilla** | Interactive island with **no React**, per the framework decision |
| Live eval | **Web Worker** | Runs example JS off the main thread, no DOM access |

## The tool: `tool/` (Kotlin Multiplatform)

The transpiler is deliberately an **orchestrator**, not a documentation engine. It
does not parse MyST (mystmd does) and does not render generic Markdown (MDX/Astro
does). It owns exactly the parts that encode *your policy*:

```
tool/src/
├── commonMain/…/blueprint/      ← the shared core, compiled to BOTH targets
│   ├── ast/MystNode.kt          resolved-AST model (tolerant JSON wrapper)
│   ├── escape/MdxEscaper.kt     the FOUR-channel escaper (prose / code / attr / JSX-HTML)
│   ├── emit/NodeMapping.kt      native-vs-fallback set + admonition collapse table
│   ├── emit/MdxEmitter.kt       native-first emitter (→ <Aside>, Expressive Code, links)
│   └── Transpiler.kt            frontmatter + imports + provenance banner
├── jvmMain/…/Cli.kt             JVM target → the CLI used in CI
├── jsMain/…/WebApi.kt           JS target → the browser playground (@JsExport)
└── commonTest/…/blueprint/      unit tests for the shared core, incl. a fixtures/
                                 package with a real mystmd AST capture — run via
                                 `gradle jvmTest`, wired into CI before `jvmJar`
```

The same `Transpiler.transpile(...)` runs in CI (as a JVM jar walking JSON files)
and in the browser (as a JS function behind a live playground). That is the
"shared code for CLI and web" requirement, satisfied by construction rather than
by copy-paste.

### Escaping is retired, not risked

MDX generation breaks when stray `{`, `<`, or backticks hit the compiler. The
emitter routes every character through one of four channels (`MdxEscaper`): prose
text is neutralised, code fences pass through verbatim with an auto-sized fence,
component attributes are entity-escaped, and the raw-HTML fallback emits
**JSX-valid** markup (self-closed voids, quoted attrs). The build then runs the
real MDX compiler (`astro build`) as a gate, so correctness is *verified*, not
trusted.

### Native where possible, fallback where not

`NodeMapping.NATIVE` lists the node types with a true Starlight/MDX equivalent;
everything else lands in the styled HTML fallback. This is what keeps the
permanent CSS surface (`site/src/styles/myst-shim.css`) tiny — admonitions and
code never reach it because they are rewritten to native `<Aside>` and Expressive
Code.

## The signature: live JS evaluation

`site/myst/index.md` contains a `js-eval` code block. The transpiler maps it to
`<CodeMirrorEval>` — a vanilla CodeMirror 6 island whose Run button posts the
current editor contents to a sandboxed Web Worker and streams the console output
back. No React, no main-thread eval.

## Use it as a template

1. Click **Use this template** on GitHub (or clone).
2. In `site/astro.config.mjs`, set `SITE`/`BASE` to your Pages URL and repo.
3. Write MyST in `site/myst/`, list pages in `site/myst/myst.yml`'s `toc`.
4. Push to `main`. The workflow builds the tool, resolves MyST, transpiles, builds
   Starlight, and deploys to Pages.

Locally: `make pipeline` runs the whole chain (needs JDK 21, Node 22, `mystmd`).
With Nix, `nix develop` drops you into a shell with JDK 21, Node 22, and Gradle
already on `PATH` (matching CI); it still nudges you to `npm install -g mystmd`.

## Honest caveats

- **mystmd AST path.** `myst build --site` writes resolved AST under
  `_build/site/content/`; confirm the exact path for your mystmd version and
  adjust `--in` if needed.
- **Web Worker is isolation, not a security sandbox.** Fine for trusted docs
  examples; do not run untrusted third-party code through it.
- **Static live-eval only.** MyST's executable/notebook features are intentionally
  out of scope here — this path renders static output by design.
- **Starlight inner aside markup.** The emitter targets `<Aside>`; if you ever
  bypass it and hand-write aside classes, match Starlight's rendered structure.

## Layout

```
.
├── README.md                     ← this blueprint
├── CLAUDE.md                     ← guidance for Claude Code when working in this repo
├── Makefile                      ← `make pipeline`
├── package.json                  ← npm-script equivalents
├── .github/workflows/deploy.yml  ← self-render → GitHub Pages (+ MDX compile gate)
├── tool/                         ← Kotlin Multiplatform transpiler (CLI + web + tests)
└── site/                         ← Astro Starlight + MyST source + live-eval island
    ├── myst/                     ← MyST source of truth
    ├── src/components/           ← CodeMirrorEval.astro + eval-worker.ts
    ├── src/styles/myst-shim.css  ← orphan-construct shim (Starlight tokens)
    └── astro.config.mjs
```
