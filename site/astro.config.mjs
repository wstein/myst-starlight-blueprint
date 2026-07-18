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
      social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/YOUR-USER/myst-starlight-blueprint' }],
      sidebar: [
        // Generated from myst.yml TOC by the pipeline (see scripts/sidebar).
        { label: 'Start here', autogenerate: { directory: '.' } },
      ],
    }),
  ],
});
