package blueprint.escape

import blueprint.ast.MystNode

/**
 * MDX "escaping-hell" is really FOUR channels with different rules. Routing each
 * correctly is what retires the problem instead of hoping it away.
 */
object MdxEscaper {

    /** Channel 1 — prose text nodes. Neutralise the MDX-active characters. */
    fun prose(text: String): String = buildString {
        for (c in text) when (c) {
            '{'  -> append("\\{")
            '}'  -> append("\\}")
            '<'  -> append("\\<")
            '`'  -> append("\\`")
            else -> append(c)
        }
    }

    /**
     * Channel 2 — inline code / fenced code. Content is NOT parsed as MDX, so it
     * is emitted verbatim between delimiters. Nothing to escape; we only pick a
     * fence long enough to contain any backtick runs already in the source.
     */
    fun fence(code: String): String {
        var longest = 0; var run = 0
        for (c in code) { if (c == '`') { run++; longest = maxOf(longest, run) } else run = 0 }
        return "`".repeat(maxOf(3, longest + 1))
    }

    /** Channel 4 — component attribute values (rendered inside double quotes). */
    fun attr(value: String): String = buildString {
        for (c in value) when (c) {
            '"'  -> append("&quot;")
            '{'  -> append("&#123;")
            '<'  -> append("&lt;")
            '&'  -> append("&amp;")
            else -> append(c)
        }
    }

    /**
     * Channel 3 — the raw-HTML fallback branch, the real sharp edge. Raw HTML in
     * MDX is JSX: void tags must self-close, attributes must be quoted, and bare
     * `<`, `&`, `{` in text break compilation. This renders a subtree as
     * JSX-VALID markup so the fallback can never crash the build.
     */
    object HtmlFallback {
        private val VOID = setOf(
            "img","br","hr","input","meta","link","source","area","base",
            "col","embed","param","track","wbr"
        )

        fun render(node: MystNode): String {
            // Leaf text
            node.value?.let { return escapeText(it) }
            val tag = htmlTag(node.type)
            val inner = node.children.joinToString("") { render(it) }
            return if (tag in VOID) "<$tag />"
                   else "<$tag>$inner</$tag>"
        }

        private fun htmlTag(nodeType: String): String = when (nodeType) {
            "paragraph" -> "p"; "strong" -> "strong"; "emphasis" -> "em"
            "listItem" -> "li"; "list" -> "ul"; "image" -> "img"
            else -> "div"
        }

        fun escapeText(t: String): String = buildString {
            for (c in t) when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '{' -> append("&#123;")
                '}' -> append("&#125;")
                else -> append(c)
            }
        }
    }
}
