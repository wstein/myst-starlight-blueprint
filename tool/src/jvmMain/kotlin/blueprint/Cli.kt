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
import java.io.File

/**
 * `myst2mdx` is one CLI with subcommands, not three separate binaries: they
 * share this file, this jar, and (transpile / dokka2mdx) the same MdxEscaper
 * escaping policy. `transpile` is the original, CI-invoked command; the two
 * `dokka2*` subcommands are new and not yet called from anywhere (see
 * CLAUDE.md's rough edges) — they exist so Html2Typst/Html2Mdx are reachable
 * outside a test suite, which is the actual prerequisite for wiring either
 * one into a real pipeline.
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

/** Dokka-generated HTML -> MDX (see blueprint.dokka.Html2Mdx). */
class Dokka2Mdx : CliktCommand(name = "dokka2mdx") {
    val input by option("--in", help = "Dir of Dokka-generated HTML (e.g. tool/build/dokka/html)").required()
    val output by option("--out", help = "Output dir for generated .mdx files").required()

    override fun run() {
        val inDir = File(input)
        val outDir = File(output).apply { mkdirs() }
        var count = 0
        inDir.walkTopDown().filter { it.isFile && it.extension == "html" }.forEach { htmlFile ->
            val rel = htmlFile.relativeTo(inDir).path.removeSuffix(".html")
            File(outDir, "$rel.mdx").apply { parentFile.mkdirs() }.writeText(Html2Mdx.convert(htmlFile.readText()))
            count++
        }
        echo("Converted $count Dokka page(s) -> $output (MDX)")
    }
}

fun main(args: Array<String>) = Myst2Mdx()
    .subcommands(Transpile(), Dokka2Typst(), Dokka2Mdx())
    .main(args)
