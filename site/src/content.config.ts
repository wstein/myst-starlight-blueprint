import { defineCollection } from 'astro:content';
import { docsLoader } from '@astrojs/starlight/loaders';
import { docsSchema } from '@astrojs/starlight/schema';

// The transpiler writes generated .mdx into src/content/docs/**; Starlight
// treats them as native pages -> shared theme, topbar, Pagefind search, TOC.
export const collections = {
  docs: defineCollection({ loader: docsLoader(), schema: docsSchema() }),
};
