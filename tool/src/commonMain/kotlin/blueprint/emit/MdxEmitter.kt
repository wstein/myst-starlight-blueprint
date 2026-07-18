package blueprint.emit

import blueprint.ast.MystNode
import blueprint.escape.MdxEscaper
import blueprint.escape.MdxEscaper.HtmlFallback

/**
 * Native-where-possible, HTML-fallback-where-not. The result reads like
 * hand-written docs for the 90% that maps, and only the rare orphan constructs
 * land in the (styled-by-shim) fallback branch.
 */
class MdxEmitter(private val basePath: String = "") {

    /** Set when we emit an <Aside> or <CodeMirrorEval>, so we can add imports. */
    val imports = linkedSetOf<String>()

    /**
     * mystmd resolves internal links/images/xrefs to root-relative paths (e.g.
     * "/tool") with no knowledge of Astro's `base` config. Prefix root-relative
     * URLs with `basePath` so they still resolve once the site is served under a
     * subpath; external URLs and same-page anchors (`#id`) pass through as-is.
     */
    private fun resolveHref(url: String?): String? {
        if (url == null || basePath.isEmpty()) return url
        if (!url.startsWith("/") || url.startsWith("//")) return url
        return basePath.trimEnd('/') + url
    }

    fun emit(node: MystNode): String = when (node.type) {
        // mystmd wraps each top-level flow section in a "block" node; it carries
        // no rendering of its own, so it passes through exactly like "root".
        "root", "block" -> block(node)
        "paragraph"     -> inline(node) + "\n\n"
        "heading"       -> "#".repeat((node.str("depth")?.toIntOrNull() ?: 1)) +
                           " " + inline(node) + "\n\n"
        "list"          -> node.children.joinToString("") { emit(it) } + "\n"
        "listItem"      -> "- " + inline(node).trim() + "\n"
        "code"          -> code(node)
        "admonition"    -> aside(node)
        "thematicBreak" -> "---\n\n"
        else            -> if (NodeMapping.isNative(node.type)) inline(node) + "\n\n"
                           else HtmlFallback.render(node) + "\n\n"
    }

    private fun block(node: MystNode) = emitChildren(node.children)

    private fun emitChildren(children: List<MystNode>) = children.joinToString("") { emit(it) }

    /** Inline-level rendering (text spans). */
    private fun inline(node: MystNode): String = node.children.joinToString("") { child ->
        when (child.type) {
            "text"          -> MdxEscaper.prose(child.value ?: "")
            "strong"        -> "**" + inline(child) + "**"
            "emphasis"      -> "*" + inline(child) + "*"
            "inlineCode"    -> { val f = MdxEscaper.fence(child.value ?: ""); f + (child.value ?: "") + f }
            "break"         -> "  \n"
            "link"          -> "[" + inline(child) + "](" + (resolveHref(child.str("url")) ?: "#") + ")"
            "crossReference"-> xref(child)
            "image"         -> "![" + MdxEscaper.attr(child.str("alt") ?: "") + "](" +
                               (resolveHref(child.str("url")) ?: "") + ")"
            "paragraph"     -> inline(child)
            else            -> if (NodeMapping.isNative(child.type)) inline(child)
                               else HtmlFallback.render(child)
        }
    }

    /** Cross-references are baked from the RESOLVED AST -> plain links, no runtime resolver. */
    private fun xref(node: MystNode): String {
        val text = if (node.children.isNotEmpty()) inline(node) else (node.value ?: "ref")
        val url = resolveHref(node.str("url")) ?: node.str("html_id")?.let { "#$it" }
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

    /**
     * mystmd always emits a leading `admonitionTitle` child (defaulted from
     * `kind`, e.g. "Note"), not a `title` string attribute. Only forward it as an
     * explicit <Aside title> when the author overrode the default text —
     * otherwise Starlight's own default title would be duplicated in the body.
     */
    private fun aside(node: MystNode): String {
        imports.add("import { Aside } from '@astrojs/starlight/components';")
        val kind = node.str("kind")
        val type = NodeMapping.asideType(kind)
        val titleNode = node.children.firstOrNull { it.type == "admonitionTitle" }
        val titleText = titleNode?.let(::inline)?.trim()
        val defaultTitle = kind?.replaceFirstChar(Char::uppercaseChar)
        val bodyChildren = node.children.filterNot { it.type == "admonitionTitle" }
        val open = if (titleText != null && titleText != defaultTitle)
            "<Aside type=\"$type\" title=\"${MdxEscaper.attr(titleText)}\">"
        else "<Aside type=\"$type\">"
        return "$open\n${emitChildren(bodyChildren).trim()}\n</Aside>\n\n"
    }
}
