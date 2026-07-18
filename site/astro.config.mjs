import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// GitHub Pages: set these to your repo. The pipeline deploys site/dist here.
const SITE = 'https://YOUR-USER.github.io';
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
      social: { github: 'https://github.com/YOUR-USER/myst-starlight-blueprint' },
      sidebar: [
        // Generated from myst.yml TOC by the pipeline (see scripts/sidebar).
        { label: 'Start here', autogenerate: { directory: '.' } },
      ],
    }),
  ],
});
