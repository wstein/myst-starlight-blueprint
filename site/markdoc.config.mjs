import { defineMarkdocConfig, component } from '@astrojs/markdoc/config';
import starlightMarkdoc from '@astrojs/starlight-markdoc';

export default defineMarkdocConfig({
  extends: [starlightMarkdoc()],
  tags: {
    codemirroreval: {
      render: component('./src/components/CodeMirrorEval.astro'),
      attributes: {
        code: { type: String },
      },
    },
  },
});
