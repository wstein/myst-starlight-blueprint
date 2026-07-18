package blueprint.emit

import blueprint.ast.MystNode
import blueprint.escape.MdxEscaper
import blueprint.escape.MdxEscaper.HtmlFallback

/**
 * Native-where-possible, HTML-fallback-where-not. The result reads like
 * hand-written docs for the 90% that maps, and only the rare orphan constructs
 * land in the (styled-by-shim) fallback branch.
 */
class MdxEmitter {

    /** Set when we emit an <Aside> or <CodeMirrorEval>, so we can add imports. */
    val imports = linkedSetOf<String>()

    fun emit(node: MystNode): String = when (node.type) {
        "root"          -> block(node)
        "paragraph"     -> inline(node) + "\n\n"
        "heading"       -> "#".repeat((node.str("depth")?.toIntOrNull() ?: 1)) +
                           " " + inline(node) + "\n\n"
        "list"          -> node.children.joinToString("") { emit(it) } + "\n"
        "listItem"      -> "- " + inline(node).trim() + "\n"
        "code"          -> code(node)
        "admonition"    -> aside(node)
        "thematicBreak" -> "---\n\n"
        else            -> inline(node) + "\n\n"
    }

    private fun block(node: MystNode) = node.children.joinToString("") { emit(it) }

    /** Inline-level rendering (text spans). */
    private fun inline(node: MystNode): String = node.children.joinToString("") { child ->
        when (child.type) {
            "text"          -> MdxEscaper.prose(child.value ?: "")
            "strong"        -> "**" + inline(child) + "**"
            "emphasis"      -> "*" + inline(child) + "*"
            "inlineCode"    -> { val f = MdxEscaper.fence(child.value ?: ""); f + (child.value ?: "") + f }
            "break"         -> "  \n"
            "link"          -> "[" + inline(child) + "](" + (child.str("url") ?: "#") + ")"
            "crossReference"-> xref(child)
            "image"         -> "![" + MdxEscaper.attr(child.str("alt") ?: "") + "](" +
                               (child.str("url") ?: "") + ")"
            "paragraph"     -> inline(child)
            else            -> if (NodeMapping.isNative(child.type)) inline(child)
                               else HtmlFallback.render(child)
        }
    }

    /** Cross-references are baked from the RESOLVED AST -> plain links, no runtime resolver. */
    private fun xref(node: MystNode): String {
        val text = if (node.children.isNotEmpty()) inline(node) else (node.value ?: "ref")
        val url = node.str("url") ?: node.str("html_id")?.let { "#$it" }
        return if (url != null) "[$text]($url)" else HtmlFallback.render(node)
    }

    /** js-eval code blocks become the live CodeMirror island; everything else is Expressive Code. */
    private fun code(node: MystNode): String {
        val lang = node.str("lang") ?: ""
        val body = node.value ?: ""
        if (lang == "js-eval") {
            imports.add("import CodeMirrorEval from '../../components/CodeMirrorEval.astro';")
            val safe = body.replace("\\", "\\\\").replace("`", "\\`").replace("\${", "\\\${")
            return "<CodeMirrorEval code={`$safe`} />\n\n"
        }
        val f = MdxEscaper.fence(body)
        return "$f$lang\n$body\n$f\n\n"
    }

    private fun aside(node: MystNode): String {
        imports.add("import { Aside } from '@astrojs/starlight/components';")
        val type = NodeMapping.asideType(node.str("kind"))
        val title = node.str("title")
        val open = if (title != null) "<Aside type=\"$type\" title=\"${MdxEscaper.attr(title)}\">"
                   else "<Aside type=\"$type\">"
        return "$open\n${block(node).trim()}\n</Aside>\n\n"
    }
}
