# One command runs the whole self-rendering pipeline locally.
.PHONY: pipeline tool myst transpile pdf site clean

pipeline: tool myst transpile pdf site   ## MyST -> AST -> MDX -> PDF -> Starlight site

tool:            ## Test + build the KMP transpiler (JVM CLI jar). Run `cd tool && gradle wrapper` once for ./gradlew
	cd tool && gradle jvmTest jvmJar

myst:            ## Resolve MyST source to AST JSON (xrefs + numbering baked in)
	cd site/myst && myst build --site

transpile:       ## AST JSON -> generated MDX in Starlight
	java -jar tool/build/libs/*-jvm.jar transpile \
		--in site/myst/_build/site/content \
		--out site/src/content/docs \
		--base /myst-starlight-blueprint

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
