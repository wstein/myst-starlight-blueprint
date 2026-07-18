import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// GitHub Pages: set these to your repo. The pipeline deploys site/dist here.
const SITE = 'https://wstein.github.io';
const BASE = '/myst-starlight-blueprint';

export default defineConfig({
  site: SITE,
  base: BASE,
  integrations: [
    starlight({
      title: 'MyST → Starlight Blueprint',
      description: 'A self-rendering blueprint: MyST source, KMP transpiler, Starlight output.',
      customCss: ['./src/styles/myst-shim.css'],
      // @astrojs/starlight ^0.30 takes `social` as an object keyed by platform
      // name (the array-of-{icon,label,href} shape isn't until Starlight 0.33).
      social: { github: 'https://github.com/wstein/myst-starlight-blueprint' },
      sidebar: [
        // Mirrors site/myst/myst.yml's `toc` order. `autogenerate` was tried
        // first but Starlight's directory-match against root-level pages
        // (`directory: '.'` or `''`) matches nothing, silently rendering an
        // empty sidebar group — explicit entries sidestep that entirely.
        //
        // The PDF link lives here (not just as a paragraph in index.md) so
        // it's reachable from every page, not only the homepage — Starlight
        // sidebar `link` values are base-prefixed automatically, unlike a raw
        // href written directly in MyST/MDX content.
        {
          label: 'Start here',
          items: ['index', 'tool', { label: 'Download as PDF', link: '/blueprint.pdf' }],
        },
      ],
    }),
  ],
});
