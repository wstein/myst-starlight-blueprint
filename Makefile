# One command runs the whole self-rendering pipeline locally.
.PHONY: pipeline tool myst transpile site clean

pipeline: tool myst transpile site   ## MyST -> AST -> MDX -> Starlight site

tool:            ## Build the KMP transpiler (JVM CLI jar). Run `cd tool && gradle wrapper` once for ./gradlew
	cd tool && gradle jvmJar

myst:            ## Resolve MyST source to AST JSON (xrefs + numbering baked in)
	cd site/myst && myst build --site

transpile:       ## AST JSON -> generated MDX in Starlight
	java -jar tool/build/libs/*-jvm.jar \
		--in site/myst/_build/site/content \
		--out site/src/content/docs

site:            ## Build the Starlight site (this is also the MDX compile gate)
	cd site && npm ci && npm run build

clean:
	rm -rf site/src/content/docs/*.mdx site/myst/_build site/dist tool/build
