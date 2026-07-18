package blueprint

import blueprint.ast.MystNode
import blueprint.emit.MdxEmitter

/**
 * Orchestrates one page: resolved MyST AST (JSON) -> a full MDX document with
 * frontmatter, component imports, and a provenance banner. The MDX is a BUILD
 * ARTIFACT — regenerated every build, never hand-edited — which is why baking
 * resolved xrefs and numbers straight into it is safe.
 */
object Transpiler {

    fun transpile(astJson: String, sourcePath: String): String {
        val doc = MystNode.parse(astJson)
        val mdast = doc.child("mdast")
            ?: error("Malformed resolved AST for $sourcePath: missing \"mdast\" root")
        val emitter = MdxEmitter()
        val body = emitter.emit(mdast)

        val title = doc.frontmatterStr("title")
            ?: doc.frontmatterStr("label")
            ?: sourcePath.substringAfterLast('/').substringBeforeLast('.')

        val fm = buildString {
            append("---\n")
            append("title: ").append(yaml(title)).append('\n')
            doc.frontmatterStr("description")?.let {
                append("description: ").append(yaml(it)).append('\n')
            }
            append("---\n")
        }

        val provenance = "{/* GENERATED from $sourcePath by myst-mdx-transpiler — do not edit */}\n"
        val importBlock = if (emitter.imports.isEmpty()) ""
                          else emitter.imports.joinToString("\n") + "\n\n"

        return fm + "\n" + provenance + "\n" + importBlock + body.trimEnd() + "\n"
    }

    private fun yaml(s: String): String =
        if (s.any { it in ":#\"'{}[]" }) "\"" + s.replace("\"", "\\\"") + "\"" else s
}
