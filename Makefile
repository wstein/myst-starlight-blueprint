# One command runs the whole self-rendering pipeline locally.
.PHONY: pipeline tool myst transpile dokka api pdf site clean

pipeline: tool myst transpile dokka api pdf site   ## MyST -> AST -> MDX -> KDoc -> PDF -> Starlight site

tool:            ## Test + build the KMP transpiler (JVM CLI jar). Run `cd tool && gradle wrapper` once for ./gradlew
	cd tool && gradle jvmTest jvmJar

myst:            ## Resolve MyST source to AST JSON (xrefs + numbering baked in)
	cd site/myst && myst build --site

transpile:       ## AST JSON -> generated MDX in Starlight
	java -jar tool/build/libs/*-jvm.jar transpile \
		--in site/myst/_build/site/content \
		--out site/src/content/docs \
		--base /myst-starlight-blueprint

dokka:           ## Generate Dokka HTML from the tool's own KDoc comments
	cd tool && gradle dokkaGenerate

api:             ## Copy Dokka's own generated HTML site verbatim into site/public/api/ (served as-is: its own
                 ## theme, search, and nav — not lossy-converted into Starlight content; see astro.config.mjs
                 ## sidebar and site/myst/tool.md's link for how it's reached from the rest of the site)
	rm -rf site/public/api
	mkdir -p site/public/api
	cp -r tool/build/dokka/html/. site/public/api/

pdf:             ## Typst-export each MyST page to its own PDF in site/public/
	# One file per invocation, not `myst build --typst index.md tool.md`: a
	# same-run first-time template download races across files and can fail
	# with "invalid template.yml" on a clean checkout (always the case in CI).
	cd site/myst && myst build --typst index.md --force
	cd site/myst && myst build --typst tool.md --force
	mkdir -p site/public
	cp site/myst/exports/blueprint.pdf site/public/blueprint.pdf
	cp site/myst/exports/tool.pdf site/public/tool.pdf

site:            ## Build the Starlight site (this is also the MDX compile gate)
	cd site && npm ci && npm run build

clean:
	rm -rf site/src/content/docs/*.mdx site/myst/_build site/myst/exports site/public site/dist tool/build
