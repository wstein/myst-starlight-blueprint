# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A documentation stack that is its own worked example. Docs are authored in MyST
Markdown, transpiled to MDX by a Kotlin Multiplatform tool, and built by Astro
Starlight into a static site deployed to GitHub Pages.

```
site/myst/*.md                  MyST source — the single source of truth (edit this)
      │  mystmd (myst build --site)         parse + resolve xrefs, numbering, TOC
      ▼
_build/site/content/*.json      resolved MyST AST (mdast-based)
      │  KMP transpiler (shared core → JVM CLI)
      ▼
site/src/content/docs/*.mdx     generated MDX — a BUILD ARTIFACT, never edited
      │  astro build (Starlight + MDX)      ← this step is the MDX compile gate
      ▼
site/dist/                      static site → GitHub Pages
```

(Mirrors the Mermaid diagram in `README.md`'s "The idea in one picture" — keep
both in sync if the pipeline's steps or artifact paths change.)

Everything downstream of the MyST source is regenerated on every build. Generated
`.mdx` under `site/src/content/docs/**` is gitignored on purpose — never hand-edit
it or add content there expecting it to persist.

## Commands

Local pipeline requires JDK 21, Node 22, and `mystmd` (`npm install -g mystmd`).
`flake.nix` provides a Nix devShell with JDK 21, Node 22, and Gradle pre-wired —
`nix develop` (or `direnv allow` via the checked-in `.envrc`); `mystmd` still
needs its own `npm install -g mystmd`.

```
make pipeline     # full chain: tool -> myst -> transpile -> site
make tool         # cd tool && gradle jvmTest jvmJar   (tests, then builds the fat JVM CLI jar)
make myst         # cd site/myst && myst build --site   (resolve MyST -> AST JSON)
make transpile    # java -jar tool/build/libs/*-jvm.jar --in <ast-json-dir> --out site/src/content/docs --base <astro-base>
make site         # cd site && npm ci && npm run build   (also the MDX compile gate)
make clean        # rm generated .mdx, _build, dist, tool/build
```

`npm run pipeline` in the repo root does the same thing via `package.json` scripts
(`myst:build`, `transpile`, `site:build`).

Kotlin tool, run from `tool/`:
- `gradle jvmJar` — build the CLI fat jar (`tool/build/libs/*-jvm.jar`, bundles the
  runtime classpath, main class `blueprint.CliKt`)
- `gradle jvmTest` — run `commonTest` + `jvmMain`-specific tests on the JVM target
  (this is what CI and `make tool` run before `jvmJar`)
- `gradle jsBrowserProductionWebpack` (or equivalent JS target task) — build the
  browser-playground bundle from `jsMain`
- `gradle jsNodeTest` / `gradle jsBrowserTest` — run `commonTest` on the JS target
  (`jsBrowserTest` needs a local Chrome install via Karma; not run in CI)
- `gradle wrapper` — generate `./gradlew` (not currently checked in; the README
  assumes a system `gradle` is available)

Test sources live under `tool/src/commonTest/kotlin/blueprint/` (unit tests for
`MystNode`, `MdxEscaper`, `NodeMapping`, `MdxEmitter`, `Transpiler`) plus a
`fixtures/` package holding a real `mystmd build --site` capture used to ground
tests in the actual resolved-AST shape rather than a hand-guessed one. Add tests
there when adding logic to `commonMain`.

Astro site, run from `site/`:
- `npm run dev` — local dev server
- `npm run build` — production build (fails if generated MDX doesn't compile —
  this is the correctness gate for the whole pipeline)
- `npm run check` — `astro check` (type/diagnostics check)

CI (`.github/workflows/deploy.yml`) runs the exact same four steps on push to
`main` (build tool jar → resolve MyST AST → transpile → build+deploy site), so it
is the authoritative sequence if `make pipeline` and CI ever diverge.

## Architecture

### `tool/` — Kotlin Multiplatform transpiler

Deliberately an *orchestrator*, not a documentation engine: it does not parse MyST
(mystmd does that upstream) and does not render generic Markdown (MDX/Astro does
that downstream). It owns only the policy of how resolved MyST AST nodes map onto
Starlight/MDX.

```
tool/src/
├── commonMain/…/blueprint/      shared core, compiled to BOTH targets
│   ├── ast/MystNode.kt          tolerant wrapper over mystmd's mdast JSON (no rigid
│   │                            per-node-type data classes — new mystmd fields don't
│   │                            break parsing)
│   ├── escape/MdxEscaper.kt     four escaping channels: prose text, code fences,
│   │                            component attributes, raw-HTML/JSX fallback
│   ├── emit/NodeMapping.kt      NATIVE node-type set + admonition-kind → <Aside> table
│   ├── emit/MdxEmitter.kt       native-first emitter (block/paragraph/heading/list/
│   │                            code/admonition/xref → MDX or Starlight components)
│   └── Transpiler.kt            per-page orchestration: frontmatter + imports +
│                                 provenance banner + body
├── jvmMain/…/Cli.kt             JVM target: clikt CLI, walks a dir of AST JSON files,
│                                 writes .mdx (this is what CI/make invoke)
└── jsMain/…/WebApi.kt           JS target: @JsExport'd transpileToMdx(), the exact
                                  same Transpiler core, used by the browser playground
```

`Transpiler.transpile(astJson, sourcePath)` is the single entry point both targets
call — that's what makes "shared core for CLI and web" true by construction rather
than by convention.

Key design points to preserve when touching this code:
- **Native where possible, HTML fallback where not.** `NodeMapping.NATIVE` is the
  contract for what the emitter renders as real Starlight/MDX constructs (e.g.
  `<Aside>`, native code fences). Anything not in that set falls through to
  `MdxEscaper.HtmlFallback`, which renders JSX-valid markup (self-closing void
  tags, entity-escaped text) so the fallback path can never break the MDX compile.
  Adding native support for a node type means adding it to `NATIVE` *and* handling
  it explicitly in `MdxEmitter.emit`/`inline`.
- **Four escaping channels, not one.** `MdxEscaper` has separate functions for
  prose text (`prose`), code fences (`fence`), component attribute values
  (`attr`), and the raw-HTML fallback (`HtmlFallback`). Don't reuse one channel's
  escaping for another context — they have different rules (e.g. code fence
  content is verbatim; prose neutralises `{`, `}`, `<`, backtick).
  MDX (`astro build`) is treated as the actual correctness gate — the escaper's job
  is to make that gate pass, not to be trusted blindly.
- **Cross-references are baked in at transpile time.** The MyST AST is already
  *resolved* (xrefs, numbering) by `myst build --site` before the tool ever sees
  it, so `MdxEmitter.xref` just emits a plain link — there's no runtime resolver
  in the generated site.
- **`js-eval` code blocks are special-cased.** They become a `<CodeMirrorEval>`
  island (see `site/src/components/CodeMirrorEval.astro` +
  `eval-worker.ts`) instead of a static code fence — a vanilla CodeMirror 6 editor
  whose Run button evaluates in a sandboxed Web Worker, off the main thread, no
  React.
- Admonitions: MyST has ~10 admonition kinds; `NodeMapping.ASIDE` collapses them
  onto Starlight's 4 `<Aside>` types. `MdxEmitter.aside` only forwards an explicit
  `title` when the author's title differs from the kind's default, to avoid
  duplicating Starlight's own default title.

### `site/` — Astro Starlight

- `site/myst/` is the actual authored content (`index.md`, `tool.md`, `myst.yml`
  TOC). This is what you edit when changing docs content.
- `site/src/content/docs/**/*.mdx` is generated output (gitignored) — only exists
  after running the transpile step; don't expect it to be present in a fresh
  checkout or to persist edits.
- `site/src/content.config.ts` wires the `docs` collection to Starlight's loader —
  generated MDX pages become native Starlight pages (shared theme, topbar,
  Pagefind search, sidebar) for free.
- `site/src/styles/myst-shim.css` is the *permanent* CSS surface for whatever
  still lands in the HTML fallback branch. Keeping `NodeMapping.NATIVE` complete
  is what keeps this file small — prefer extending native mapping over adding
  shim CSS for a new construct.
- `site/astro.config.mjs`'s `SITE`/`BASE`/`social` values are set for this repo's
  own deployment (`wstein.github.io/myst-starlight-blueprint`) — anyone forking
  this as a template must repoint all three to their own GitHub Pages URL/repo.
- Sidebar is currently `autogenerate` from the docs directory; the intent (per
  README) is eventually to generate it from `myst.yml`'s `toc`, not yet wired up.

## Known rough edges (per README's own caveats)

- The exact resolved-AST output path (`site/myst/_build/site/content/`) depends on
  the installed `mystmd` version; adjust `--in` in `make transpile` /
  `npm run transpile` if it changes.
- `site/myst/myst.yml`'s `site.template` must name a real template (e.g.
  `book-theme`) — `myst build --site` always resolves and downloads one over the
  network even though we only consume the resolved AST JSON and never render its
  output; a sentinel like `none` 404s instead of skipping template resolution.
  `myst templates list --site` lists the valid options.
- Pinned `site/package.json` versions are load-bearing: `@astrojs/starlight`
  changed the shape of the `social` config option between 0.30 and 0.33 (object
  keyed by platform name vs. an array of `{icon,label,href}`), and only
  `astro build` catches a mismatch — `astro check` and the Kotlin test suite
  don't touch `astro.config.mjs`. Re-run the full pipeline after bumping
  anything under `site/`.
- `site/package-lock.json` is committed on purpose — CI's `site` step runs
  `npm ci`, which requires one; don't gitignore it.
- `make transpile`'s `--base` must equal `site/astro.config.mjs`'s `BASE` —
  mystmd resolves internal links to root-relative paths with no knowledge of
  Astro's subpath deploy, so `MdxEmitter`/`Cli.kt` re-prefix them at transpile
  time from this flag. It's a second copy of the same value, not derived from
  `astro.config.mjs`; keep `Makefile`, `package.json`, and
  `.github/workflows/deploy.yml` in sync with it.
- The Web Worker in `eval-worker.ts` is isolation, not a security sandbox — fine
  for trusted docs examples, not for untrusted third-party code.
- Live eval is static-output-only; MyST's executable/notebook features are
  intentionally out of scope.
