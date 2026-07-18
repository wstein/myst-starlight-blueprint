package blueprint

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import java.io.File

/**
 * CLI target. Same shared core, wrapped with file IO + arg parsing.
 * Walks mystmd's resolved-AST JSON tree and mirrors it as .mdx into Starlight.
 */
class Transpile : CliktCommand(name = "myst-transpile") {
    val input by option("--in", help = "Dir of mystmd resolved-AST JSON").required()
    val output by option("--out", help = "Starlight src/content/docs dir").required()

    override fun run() {
        val inDir = File(input)
        val outDir = File(output).apply { mkdirs() }
        var count = 0
        inDir.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { json ->
            val rel = json.relativeTo(inDir).path.removeSuffix(".json")
            val mdx = Transpiler.transpile(json.readText(), sourcePath = "myst/$rel.md")
            File(outDir, "$rel.mdx").apply { parentFile.mkdirs() }.writeText(mdx)
            count++
        }
        echo("Transpiled $count page(s) -> $output")
    }
}

fun main(args: Array<String>) = Transpile().main(args)
