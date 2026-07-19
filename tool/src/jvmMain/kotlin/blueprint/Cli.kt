package blueprint

import blueprint.dokka.Html2Mdx
import blueprint.dokka.Html2Typst
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.jsoup.Jsoup
import java.io.File

/**
 * `myst2mdx` is one CLI with subcommands, not three separate binaries: they
 * share this file, this jar, and (transpile / dokka2mdx) the same MdxEscaper
 * escaping policy. `transpile` is the original, CI-invoked command. The two
 * `dokka2*` subcommands are CLI-reachable and tested but not called from
 * CI/Makefile: the live site instead copies Dokka's own generated HTML
 * verbatim into `site/public/api/` (see Makefile's `api` target) so it keeps
 * its own theme/search/nav rather than being lossy-converted into MDX/Typst
 * — `dokka2mdx`/`dokka2typst` stay available for whatever needs an actual
 * MDX/Typst rendering of KDoc content (e.g. a future merged PDF).
 */
class Myst2Mdx : NoOpCliktCommand(name = "myst2mdx")

/** MyST resolved-AST JSON -> Starlight MDX. The original, sole command before subcommands existed. */
class Transpile : CliktCommand(name = "transpile") {
    val input by option("--in", help = "Dir of mystmd resolved-AST JSON").required()
    val output by option("--out", help = "Starlight src/content/docs dir").required()
    val base by option(
        "--base",
        help = "Astro `base` config (e.g. /my-repo) — prefixed onto mystmd's " +
            "root-relative internal links/images/xrefs so they still resolve " +
            "once the site is served under a subpath. Must match astro.config.mjs."
    ).default("")

    override fun run() {
        val inDir = File(input)
        val outDir = File(output).apply { mkdirs() }
        var count = 0
        inDir.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { json ->
            val rel = json.relativeTo(inDir).path.removeSuffix(".json")
            val mdx = Transpiler.transpile(json.readText(), sourcePath = "myst/$rel.md", basePath = base)
            File(outDir, "$rel.mdx").apply { parentFile.mkdirs() }.writeText(mdx)
            count++
        }
        echo("Transpiled $count page(s) -> $output")
    }
}

/** Dokka-generated HTML -> Typst (see blueprint.dokka.Html2Typst). */
class Dokka2Typst : CliktCommand(name = "dokka2typst") {
    val input by option("--in", help = "Dir of Dokka-generated HTML (e.g. tool/build/dokka/html)").required()
    val output by option("--out", help = "Output dir for generated .typ files").required()

    override fun run() {
        val inDir = File(input)
        val outDir = File(output).apply { mkdirs() }
        var count = 0
        inDir.walkTopDown().filter { it.isFile && it.extension == "html" }.forEach { htmlFile ->
            val rel = htmlFile.relativeTo(inDir).path.removeSuffix(".html")
            File(outDir, "$rel.typ").apply { parentFile.mkdirs() }.writeText(Html2Typst.convert(htmlFile.readText()))
            count++
        }
        echo("Converted $count Dokka page(s) -> $output (Typst)")
    }
}

/**
 * Dokka-generated HTML -> MDX (see blueprint.dokka.Html2Mdx), plus the
 * Starlight frontmatter Html2Mdx deliberately doesn't add itself (it's a
 * body-only converter — see its class KDoc). `--in` should point at the
 * generated site's per-symbol subtree (e.g. tool/build/dokka/html/myst2mdx),
 * not the whole `dokka/html` output — navigation.html/search-worker.json
 * alongside it are pure site chrome. One exception: `--in`'s own *sibling*
 * `index.html` (one level up — the module's real "Packages" overview, not
 * chrome) is also converted, into `output/index.mdx` — see [run].
 */
class Dokka2Mdx : CliktCommand(name = "dokka2mdx") {
    val input by option("--in", help = "Dir of Dokka-generated HTML (e.g. tool/build/dokka/html/myst2mdx)").required()
    val output by option("--out", help = "Output dir for generated .mdx files").required()

    override fun run() {
        val inDir = File(input)
        val outDir = File(output).apply { mkdirs() }
        var count = 0
        inDir.walkTopDown().filter { it.isFile && it.extension == "html" }.forEach { htmlFile ->
            val rel = htmlFile.relativeTo(inDir).path.removeSuffix(".html")
            val fallbackTitle = rel.substringAfterLast('/')
            // Dokka names top-level package dirs after the FQN ("blueprint.ast"),
            // but Astro/Starlight's route slugifier silently drops '.' from path
            // segments (it treats the tail as a file extension, same rule that
            // strips ".mdx" itself) — "blueprint.ast" and "blueprint.dokka" both
            // collapsed onto unrelated flat slugs ("blueprintast", "blueprintdokka")
            // with no build warning. Only found by diffing generated dist/ dir
            // names against the source tree. Hyphenate instead so the slug
            // survives intact.
            val slug = rel.replace('.', '-')
            File(outDir, "$slug.mdx").apply { parentFile.mkdirs() }
                .writeText(renderDokkaMdxPage(htmlFile.readText(), fallbackTitle))
            count++
        }

        // Dokka's module-root "Packages" overview (a real landing page: every
        // package name + its common/js/jvm platform availability) lives one
        // level ABOVE --in, since --in is scoped to the per-symbol subtree to
        // exclude navigation.html/search chrome that sits alongside it at the
        // same root. Without this, api/ had no index of its own — a reader
        // landing on /api/ (or the sidebar group heading, once it links to an
        // index page) got nothing. Only found by diffing this pipeline's
        // output against a real `dokkaGenerate` run's full file tree.
        val moduleIndex = File(inDir.parentFile, "index.html")
        if (moduleIndex.isFile) {
            val page = renderDokkaMdxPage(moduleIndex.readText(), "API reference")
            File(outDir, "index.mdx").writeText(rewriteModuleIndexLinks(page, inDir.name))
            count++
        }

        echo("Converted $count Dokka page(s) -> $output (MDX)")
    }
}

/**
 * The module overview page links into the per-symbol subtree with paths
 * prefixed by that subtree's own directory name (e.g.
 * "myst2mdx/blueprint.ast/index.html") and un-hyphenated package dots —
 * neither matches routes this pipeline actually generates (flat under
 * `output/`, dots hyphenated per [Dokka2Mdx.run]) — so strip the prefix and
 * hyphenate the first path segment the same way.
 */
internal fun rewriteModuleIndexLinks(mdx: String, subtreeDirName: String): String {
    val linkPattern = Regex("""]\(${Regex.escape("$subtreeDirName/")}([^)]+)\)""")
    return linkPattern.replace(mdx) { match ->
        val path = match.groupValues[1]
        val slash = path.indexOf('/')
        val rewritten = if (slash >= 0) {
            path.take(slash).replace('.', '-') + path.substring(slash)
        } else {
            path.replace('.', '-')
        }
        "]($rewritten)"
    }
}

/**
 * `html` -> a full Starlight MDX file: frontmatter (title from Dokka's own
 * `<title>` tag, falling back to [fallbackTitle] if blank) + body. Extracted
 * from [Dokka2Mdx.run] so the frontmatter/duplicate-heading logic is testable
 * without file I/O.
 */
internal fun renderDokkaMdxPage(html: String, fallbackTitle: String): String {
    val title = Jsoup.parse(html).title().ifBlank { fallbackTitle }
    val frontmatter = "---\ntitle: ${yamlString(title)}\n---\n\n"
    // Starlight already renders frontmatter `title` as the page's H1 (same
    // bug class as the main site's index.md/tool.md, caught earlier — see
    // CLAUDE.md). Dokka's own "cover" <h1> becomes a duplicate leading
    // "# <title>" here; strip it if it's an exact match rather than trusting
    // every page not to have one.
    val body = Html2Mdx.convert(html).removePrefix("# $title\n\n")
    return frontmatter + body
}

/** Same minimal YAML-scalar quoting Transpiler.kt uses for frontmatter values. */
private fun yamlString(s: String): String =
    if (s.any { it in ":#\"'{}[]" }) "\"" + s.replace("\"", "\\\"") + "\"" else s

fun main(args: Array<String>) = Myst2Mdx()
    .subcommands(Transpile(), Dokka2Typst(), Dokka2Mdx())
    .main(args)
