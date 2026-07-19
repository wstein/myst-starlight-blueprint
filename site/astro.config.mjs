import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import markdoc from '@astrojs/markdoc';

// GitHub Pages: set these to your repo. The pipeline deploys site/dist here.
const SITE = 'https://wstein.github.io';
const BASE = '/myst-starlight-blueprint';

export default defineConfig({
  site: SITE,
  base: BASE,
  integrations: [
    markdoc(),
    starlight({
      title: 'MyST → Starlight Blueprint',
      description: 'A self-rendering blueprint: MyST source, KMP transpiler, Starlight output.',
      customCss: ['./src/styles/myst-shim.css'],
      // Starlight >=0.33 takes `social` as an array of {icon,label,href} —
      // the object-keyed-by-platform shape (^0.30) was retired; this pin
      // moved past 0.33 for the official Markdoc preset (needs Starlight
      // >=0.41), so the array shape is required now, not optional.
      social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/wstein/myst-starlight-blueprint' }],
      // Per-page PDF download link, not a single sidebar entry: each page has
      // its own PDF (index.pdf, tool.pdf, ...), so the link has to change with
      // the page. See src/components/HeaderWithPdf.astro.
      components: { Header: './src/components/HeaderWithPdf.astro' },
      sidebar: [
        // Mirrors site/myst/myst.yml's `toc` order. `autogenerate` was tried
        // first but Starlight's directory-match against root-level pages
        // (`directory: '.'` or `''`) matches nothing, silently rendering an
        // empty sidebar group — explicit entries sidestep that entirely.
        { label: 'Start here', items: ['index', 'tool'] },
        // api/ isn't a Starlight content collection: it's Dokka's own
        // generated HTML site, copied verbatim into site/public/api/ (see
        // Makefile's `api` target) so it keeps its own theme, search, and
        // nav rather than being lossy-converted into MDX — so this is a
        // plain link, not `autogenerate` (there's no content collection
        // entry here for Starlight to walk).
        { label: 'API reference', link: '/api/' },
      ],
    }),
  ],
});
