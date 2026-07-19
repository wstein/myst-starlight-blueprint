package blueprint.dokka

import blueprint.escape.MdxEscaper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * HTML -> MDX converter: the Starlight-side counterpart to [Html2Typst]. Same
 * DOM-walk shape (see that file's KDoc for the upstream html2typst lineage and
 * the deviations already made there — unrecognized tags fall back instead of
 * crashing, inter-element whitespace is preserved), but this one was written
 * fresh against MDX/GFM syntax rather than ported from Rust, and reuses this
 * project's own [MdxEscaper] (prose + fence channels) so Dokka-derived content
 * escapes exactly the same way myst2mdx's own MyST->MDX output does — one
 * escaping policy, not two.
 *
 * Output is body content only, meant for files under `site/src/content/docs/api/`
 * with Starlight frontmatter (at minimum `title`) prepended separately — this
 * doesn't go through mystmd/myst2mdx at all, since Dokka's HTML has no MyST
 * constructs for that pipeline to resolve.
 *
 * See [Html2Typst]'s KDoc for the `anchor-wrapper`/"Link copied to clipboard"
 * finding — same fix applies here, same reason (only found via real
 * `dokkaGenerate` output, see RealDokkaFixtureTest).
 */
object Html2Mdx {

    /** Dokka UI chrome with no documentation content — skipped entirely, not walked. */
    private val DOKKA_CHROME_CLASSES = setOf("anchor-wrapper", "breadcrumbs")

    fun convert(html: String): String {
        val doc = Jsoup.parse(html)
        // See Html2Typst.convert's matching comment: Dokka wraps real content
        // in id="content" across every page type; everything else is chrome.
        val root = doc.getElementById("content") ?: doc.body()
        val ctx = Context()
        walkDescendants(root, ctx, root.tagName().lowercase())
        // See Html2Typst.convert's matching comment: collapses blank-line runs
        // from Dokka's deeply nested chrome divs; harmless (Markdown/MDX also
        // treat any blank line as a paragraph break), just noisy uncollapsed.
        return ctx.output.toString().trim().replace(Regex("\n{3,}"), "\n\n")
    }

    private class Context {
        val tagStack = ArrayDeque<String>()
        val output = StringBuilder()
    }

    private fun insideListItem(ctx: Context): Boolean = ctx.tagStack.any { it == "li" }

    private fun listNestingDepth(ctx: Context): Int =
        ctx.tagStack.count { it == "ol" || it == "ul" || it == "menu" }

    private fun walk(node: Node, ctx: Context) {
        when (node) {
            is TextNode -> appendText(node.wholeText, ctx)
            is Element -> walkElement(node, ctx)
            else -> Unit
        }
    }

    /** See [Html2Typst]'s matching function — same fix, same rationale, independent implementation. */
    private fun appendText(raw: String, ctx: Context) {
        when {
            raw.isBlank() && '\n' in raw -> Unit
            raw.isBlank() -> ctx.output.append(' ')
            else -> {
                val collapsed = raw.trim().replace(Regex("\\s+"), " ")
                if (raw.first().isWhitespace()) ctx.output.append(' ')
                ctx.output.append(MdxEscaper.prose(collapsed))
                if (raw.last().isWhitespace()) ctx.output.append(' ')
            }
        }
    }

    private fun walkElement(node: Element, ctx: Context) {
        if (node.classNames().any { it in DOKKA_CHROME_CLASSES }) return // see class doc

        when (val tag = node.tagName().lowercase()) {
            "b", "strong" -> wrap(node, ctx, "**", "**")
            "i", "em" -> wrap(node, ctx, "*", "*")
            "s", "del" -> wrap(node, ctx, "~~", "~~")
            "sub" -> wrap(node, ctx, "<sub>", "</sub>")
            "sup" -> wrap(node, ctx, "<sup>", "</sup>")
            "u", "ins" -> wrap(node, ctx, "<u>", "</u>")

            "div", "section", "header", "footer" -> {
                ctx.output.append("\n\n")
                walkDescendants(node, ctx, tag)
                ctx.output.append("\n\n")
            }

            "li" -> {
                val parentListTag = ctx.tagStack.lastOrNull { it == "ol" || it == "ul" || it == "menu" }
                val indent = "  ".repeat((listNestingDepth(ctx) - 1).coerceAtLeast(0))
                val marker = if (parentListTag == "ol") "1. " else "- "
                ctx.output.append(indent).append(marker)
                walkDescendants(node, ctx, tag)
                if (!ctx.output.endsWith("\n\n")) ctx.output.append('\n')
            }

            "ol", "ul", "menu" -> {
                ctx.output.append('\n')
                if (listNestingDepth(ctx) == 0) ctx.output.append('\n')
                walkDescendants(node, ctx, tag)
                if (insideListItem(ctx)) ctx.output.append('\n') else ctx.output.append("\n\n")
            }

            "blockquote" -> {
                val innerCtx = Context()
                walkDescendants(node, innerCtx, tag)
                val quoted = innerCtx.output.toString().trim().lineSequence().joinToString("\n") { "> $it" }
                ctx.output.append("\n\n").append(quoted).append("\n\n")
            }

            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = tag[1] - '0'
                ctx.output.append("\n\n").append("#".repeat(level)).append(' ')
                walkDescendants(node, ctx, tag)
                ctx.output.append("\n\n")
            }

            "html", "head", "body" -> walkDescendants(node, ctx, tag)

            "p" -> {
                walkDescendants(node, ctx, tag)
                ctx.output.append("\n\n")
            }

            "br" -> ctx.output.append("  \n")

            "a" -> {
                val href = node.attrOrNull("href")
                if (href != null) {
                    ctx.output.append('[')
                    walkDescendants(node, ctx, tag)
                    ctx.output.append("](").append(href).append(')')
                } else {
                    walkDescendants(node, ctx, tag)
                }
            }

            "img" -> {
                val src = node.attrOrNull("src")
                if (src != null) {
                    val alt = node.attrOrNull("alt").orEmpty()
                    ctx.output.append("![").append(MdxEscaper.prose(alt)).append("](").append(src).append(')')
                }
            }

            "pre" -> emitCodeBlock(node, ctx)
            "code" -> emitInlineCode(node, ctx) // only reached when NOT inside <pre>

            "table" -> emitTable(node, ctx)

            // Dokka's own tab/platform toggles (e.g. the "Types"/"Functions"
            // section-tab buttons, the per-symbol "jvm" platform-bookmark
            // button) are live UI controls with no JS behind them in a static
            // export — the graceful fallback below would otherwise leak their
            // label text into prose as unstyled noise, duplicating the real
            // "## Types"/"## Functions" headings that follow. Found by reading
            // the real Dokka HTML (data-togglable/data-toggle attributes), not
            // guessed from the rendered symptom alone.
            "button" -> Unit

            else -> walkDescendants(node, ctx, tag) // graceful fallback — see class doc
        }
    }

    private fun wrap(node: Element, ctx: Context, open: String, close: String) {
        ctx.output.append(open)
        walkDescendants(node, ctx, node.tagName().lowercase())
        ctx.output.append(close)
    }

    private fun emitCodeBlock(node: Element, ctx: Context) {
        val codeChild = node.children().firstOrNull { it.tagName().equals("code", ignoreCase = true) }
        val raw = (codeChild ?: node).wholeText().trimEnd('\n')
        val lang = codeChild?.classNames()?.firstOrNull { it.startsWith("language-") }?.removePrefix("language-").orEmpty()
        val fence = MdxEscaper.fence(raw)
        ctx.output.append("\n\n").append(fence).append(lang).append('\n')
            .append(raw).append('\n').append(fence).append("\n\n")
    }

    private fun emitInlineCode(node: Element, ctx: Context) {
        val raw = node.wholeText()
        val fence = inlineRawFence(raw)
        ctx.output.append(fence).append(raw).append(fence)
    }

    /** Minimal (not block-minimum-3) backtick run — inline code doesn't need MdxEscaper.fence's 3-backtick floor. */
    private fun inlineRawFence(text: String): String {
        var longest = 0
        var run = 0
        for (c in text) {
            if (c == '`') { run++; longest = maxOf(longest, run) } else run = 0
        }
        return "`".repeat(maxOf(1, longest + 1))
    }

    /** GFM table — same colspan/rowspan limitation as [Html2Typst.emitTable]: ignored, not merged. */
    private fun emitTable(node: Element, ctx: Context) {
        val rows = node.select("tr")
        val cellRows = rows.map { row -> row.children().filter { it.tagName().let { t -> t == "th" || t == "td" } } }
        val columns = cellRows.maxOfOrNull { it.size } ?: 0
        if (columns == 0) return

        fun renderRow(cells: List<Element>): String = cells.joinToString(" | ", prefix = "| ", postfix = " |") { cell ->
            val cellCtx = Context()
            walkDescendants(cell, cellCtx, cell.tagName().lowercase())
            cellCtx.output.toString().trim().replace("\n", " ").replace("|", "\\|")
        }

        ctx.output.append("\n\n")
        ctx.output.append(renderRow(cellRows.firstOrNull().orEmpty()))
        ctx.output.append('\n').append("| ").append(List(columns) { "---" }.joinToString(" | ")).append(" |\n")
        for (cells in cellRows.drop(1)) {
            ctx.output.append(renderRow(cells)).append('\n')
        }
        ctx.output.append('\n')
    }

    private fun walkDescendants(node: Node, ctx: Context, tagName: String?) {
        if (tagName != null) ctx.tagStack.addLast(tagName)
        for (child in node.childNodes()) walk(child, ctx)
        if (tagName != null) ctx.tagStack.removeLast()
    }

    private fun Element.attrOrNull(name: String): String? = if (hasAttr(name)) attr(name) else null
}
