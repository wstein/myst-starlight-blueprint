package blueprint.dokka

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * HTML -> Typst markup converter. Ported from adriandelgado/html2typst (Rust,
 * MIT, github.com/adriandelgado/html2typst/blob/main/src/lib.rs) and extended
 * with `<pre>`/`<code>`/`<table>` support, which upstream leaves as `todo!()` —
 * the two element types that make up the bulk of Dokka-generated API docs.
 *
 * One deliberate deviation from upstream: it `panic!()`s on any tag it doesn't
 * recognize (`hr`, `q`, `cite`, `details`, `summary`, `iframe`, and a catch-all
 * `_`). This port falls back to rendering the tag's descendants instead —
 * matching this repo's own rule (see `MdxEscaper.HtmlFallback`) that an
 * unrecognized construct degrades gracefully rather than breaking the build.
 *
 * Not ported (upstream doesn't implement them either): `colspan`/`rowspan` on
 * table cells are ignored — each cell renders once, in place, uncombined.
 *
 * Found only by running this against real `gradle dokkaGenerate` output (see
 * RealDokkaFixtureTest), not visible from hand-written test HTML: Dokka's
 * per-member "copy link" affordance renders as visible text ("Link copied to
 * clipboard") inside `class="anchor-wrapper"`, which would otherwise leak into
 * the converted prose as if it were documentation content. Skipped entirely.
 */
object Html2Typst {

    /** Dokka UI chrome with no documentation content — skipped entirely, not walked. */
    private val DOKKA_CHROME_CLASSES = setOf("anchor-wrapper", "breadcrumbs")

    fun convert(html: String): String {
        val doc = Jsoup.parse(html)
        // Dokka wraps every page's real content in id="content" (verified
        // across classlike and member page types against real dokkaGenerate
        // output) — everything outside it is site chrome: top nav, sidebar,
        // search modal. Fall back to <body> for non-Dokka HTML (tests, or any
        // other source), so this stays a general converter, not Dokka-only.
        val root = doc.getElementById("content") ?: doc.body()
        val ctx = Context()
        walkDescendants(root, ctx, root.tagName().lowercase())
        // Dokka's deeply nested chrome divs each contribute their own "\n\n",
        // compounding into runs of 4+ blank lines — harmless to a Typst reader
        // (any blank line is a paragraph break) but noisy in the source. Only
        // visible with real nested markup; hand-written test HTML is too flat
        // to produce it.
        val collapsed = ctx.output.toString().trim().replace(Regex("\n{3,}"), "\n\n")
        // Every "]" in our own output closes a #link/#footnote/#quote/etc.
        // content block. Typst treats a "]" immediately followed by "(" or
        // "[" as continuing the SAME code-mode call chain rather than
        // starting fresh markup — e.g. a Dokka function signature emits
        // `#link(...)[attr](value: String)`, which Typst parses as calling
        // the link's result with (value: String) as arguments, a hard parse
        // error. Only found by actually running a real `typst compile` on
        // dense signature content, not from the emitted string alone — a
        // single inserted space is enough to break the ambiguity and is
        // typographically harmless (`attr(value:` -> `attr (value:`).
        return collapsed.replace(Regex("\\](?=[(\\[])"), "] ")
    }

    private class Context(val footnotedUrls: MutableSet<String> = mutableSetOf()) {
        val tagStack = ArrayDeque<String>()
        val output = StringBuilder()
    }

    private fun currentListDepth(ctx: Context): Int =
        ctx.tagStack.count { it == "ol" || it == "ul" || it == "menu" }

    private fun insideListItem(ctx: Context): Boolean =
        ctx.tagStack.any { it == "li" }

    private fun listItemContinuationWidth(ctx: Context): Int? =
        if (insideListItem(ctx)) currentListDepth(ctx) * 2 else null

    private fun writeListItemContinuationIndent(ctx: Context) {
        listItemContinuationWidth(ctx)?.let { width -> ctx.output.append(" ".repeat(width)) }
    }

    private fun walk(node: Node, ctx: Context) {
        when (node) {
            is TextNode -> appendText(node.wholeText, ctx)
            is Element -> walkElement(node, ctx)
            else -> Unit // comments, doctype, etc. — nothing to emit
        }
    }

    /**
     * Upstream unconditionally `.trim()`s every text node — its own comment flags
     * "remove excess whitespace" as unresolved — which drops real inter-element
     * spacing (`<b>bold</b> and <i>em</i>` -> "boldand em"). Fixed here rather
     * than ported: a whitespace-only node containing a newline is pretty-printed
     * indentation between block tags and is dropped; a whitespace-only node
     * without one is a meaningful inline gap and collapses to a single space;
     * anything else keeps a single leading/trailing space if the original had one.
     */
    private fun appendText(raw: String, ctx: Context) {
        when {
            raw.isBlank() && '\n' in raw -> Unit
            raw.isBlank() -> ctx.output.append(' ')
            else -> {
                val collapsed = raw.trim().replace(Regex("\\s+"), " ")
                if (raw.first().isWhitespace()) ctx.output.append(' ')
                ctx.output.append(escapeTypstText(collapsed))
                if (raw.last().isWhitespace()) ctx.output.append(' ')
            }
        }
    }

    private fun walkElement(node: Element, ctx: Context) {
        if (node.classNames().any { it in DOKKA_CHROME_CLASSES }) return // see class doc

        // See Html2Mdx's matching case: KMP platform-availability badges are
        // real content, but each is its own <div> — without this they'd
        // stack one per line via the generic "div" case below instead of
        // reading as the compact inline group a badge is meant to be.
        if ("platform-tag" in node.classNames()) {
            val text = node.text().trim()
            val fence = rawFence(text, minLen = 1)
            ctx.output.append(fence).append(text).append(fence).append(' ')
            return
        }

        when (val tag = node.tagName().lowercase()) {
            "sub" -> inlineWrap(node, ctx, "#sub[", "]")
            "sup" -> inlineWrap(node, ctx, "#super[", "]")
            "s", "del" -> inlineWrap(node, ctx, "#strike[", "]")
            "b", "strong" -> inlineWrap(node, ctx, "*", "*")
            "i", "em" -> inlineWrap(node, ctx, "_", "_")
            "u", "ins" -> inlineWrap(node, ctx, "#underline[", "]")

            "div", "section", "header", "footer" -> {
                ctx.output.append("\n\n")
                walkDescendants(node, ctx, tag)
                ctx.output.append("\n\n")
            }

            "li" -> {
                val parentListTag = ctx.tagStack.lastOrNull { it == "ol" || it == "ul" || it == "menu" }
                val tagLevel = (listItemContinuationWidth(ctx) ?: 0).let { (it - 2).coerceAtLeast(0) }
                val marker = if (parentListTag == "ol") "+ " else "- "
                ctx.output.append(" ".repeat(tagLevel)).append(marker)
                walkDescendants(node, ctx, tag)
                if (!ctx.output.endsWith("\n\n")) ctx.output.append('\n')
            }

            "ol", "ul", "menu" -> {
                ctx.output.append('\n')
                if (currentListDepth(ctx) == 0) ctx.output.append('\n')
                walkDescendants(node, ctx, tag)
                if (insideListItem(ctx)) ctx.output.append('\n') else ctx.output.append("\n\n")
            }

            "blockquote" -> {
                ctx.output.append("\n\n#quote(block: true)[\n")
                walkDescendants(node, ctx, tag)
                ctx.output.append("\n]\n\n")
            }

            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = tag[1] - '0'
                ctx.output.append("=".repeat(level)).append(' ')
                walkDescendants(node, ctx, tag)
                node.attrOrNull("id")?.let { id -> ctx.output.append(" <").append(id).append(">\n") }
            }

            "html", "head", "body" -> walkDescendants(node, ctx, tag)

            "p" -> {
                if (listItemContinuationWidth(ctx) != null && ctx.output.endsWith("\n\n")) {
                    writeListItemContinuationIndent(ctx)
                }
                walkDescendants(node, ctx, tag)
                ctx.output.append("\n\n")
            }

            "br" -> {
                ctx.output.append("\\\n")
                writeListItemContinuationIndent(ctx)
            }

            "a" -> {
                val href = node.attrOrNull("href")
                if (href != null) {
                    ctx.output.append("#link(\"").append(href).append("\")[")
                    walkDescendants(node, ctx, tag)
                    ctx.output.append(']')
                    // A reader of the rendered PDF can't click through the way
                    // a web link works, so a link's destination is spelled out
                    // as a footnote the first time it's seen on the page, not
                    // just the link text — except a pure same-page anchor
                    // (`#section`), which has no meaningful destination to
                    // spell out beyond itself. Deduped per page (not per
                    // occurrence): found via the real Dokka fixture that a
                    // dense function signature can repeat the identical stdlib
                    // link 2-3 times in one line, which without dedup meant
                    // 2-3 near-duplicate footnotes for a single URL.
                    if (!href.startsWith("#") && ctx.footnotedUrls.add(href)) {
                        ctx.output.append("#footnote[").append(href).append(']')
                    }
                } else {
                    walkDescendants(node, ctx, tag)
                }
            }

            "img" -> emitImage(node, ctx)

            "pre" -> emitCodeBlock(node, ctx)
            "code" -> emitInlineCode(node, ctx) // only reached when NOT inside <pre> (see emitCodeBlock)

            "table" -> emitTable(node, ctx)

            // See Html2Mdx's matching case: Dokka's tab/platform-toggle
            // buttons are live UI controls, not content, in a static export —
            // their label text (e.g. a duplicate "Types") would otherwise
            // leak into the fallback branch below as unstyled noise.
            "button" -> Unit

            else -> {
                // Graceful fallback (see class doc): render descendants, don't crash.
                walkDescendants(node, ctx, tag)
            }
        }
    }

    private fun inlineWrap(node: Element, ctx: Context, open: String, close: String) {
        val (leadingWs, trailingWs) = inlineEdgeWhitespace(node)
        if (leadingWs) ctx.output.append(' ')
        ctx.output.append(open)
        walkDescendants(node, ctx, node.tagName().lowercase())
        ctx.output.append(close)
        if (trailingWs) ctx.output.append(' ')
    }

    private fun emitImage(node: Element, ctx: Context) {
        val alt = node.attrOrNull("alt")
        val srcAttr = node.attrOrNull("src") ?: return
        val src = imageSrcArg(srcAttr) ?: return
        if (alt != null) {
            ctx.output.append("#figure(caption: [").append(escapeTypstText(alt))
                .append("], image(alt: \"").append(escapeQuotes(alt)).append("\", ").append(src).append("))")
        } else {
            ctx.output.append("#figure(caption: none, image(").append(src).append("))")
        }
    }

    /** `data:image/...;base64,...` -> a Typst `bytes((..))` literal; anything else -> a quoted path. */
    private fun imageSrcArg(src: String): String? {
        val cleared = src.filterNot { it.isWhitespace() }
        if (!cleared.startsWith("data:")) return "\"${escapeQuotes(src)}\""
        val afterScheme = cleared.removePrefix("data:")
        val mediaAndData = afterScheme.split(';')
        if (!mediaAndData.first().startsWith("image/")) return null // not image data — skip gracefully
        val last = mediaAndData.last()
        val commaIdx = last.indexOf(',')
        if (commaIdx < 0) return null
        val encoding = last.substring(0, commaIdx)
        val data = last.substring(commaIdx + 1)
        return when (encoding) {
            "base64" -> runCatching {
                val bytes = java.util.Base64.getDecoder().decode(data)
                "bytes((${bytes.joinToString(", ") { (it.toInt() and 0xFF).toString() }}))"
            }.getOrNull()
            else -> null // e.g. inline `image/svg+xml` data URIs — not handled, skipped gracefully
        }
    }

    /** `<pre><code class="language-kotlin">...</code></pre>` -> a fenced Typst code block. */
    private fun emitCodeBlock(node: Element, ctx: Context) {
        val codeChild = node.children().firstOrNull { it.tagName().equals("code", ignoreCase = true) }
        val raw = (codeChild ?: node).wholeText().trimEnd('\n')
        val lang = codeChild?.classNames()?.firstOrNull { it.startsWith("language-") }?.removePrefix("language-").orEmpty()
        val fence = rawFence(raw, minLen = 3)
        ctx.output.append("\n\n").append(fence).append(lang).append('\n')
            .append(raw).append('\n').append(fence).append("\n\n")
    }

    /** Standalone inline `<code>` (not wrapped in `<pre>`) -> an inline Typst raw span. */
    private fun emitInlineCode(node: Element, ctx: Context) {
        val raw = node.wholeText()
        val fence = rawFence(raw, minLen = 1)
        ctx.output.append(fence).append(raw).append(fence)
    }

    /**
     * `<table>` -> Typst's `#table(columns: N, ...)`. Column count is the widest
     * row's cell count; `colspan`/`rowspan` are ignored (see class doc) rather
     * than merged, so a spanned layout won't line up perfectly — a known,
     * accepted limitation rather than a crash.
     */
    private fun emitTable(node: Element, ctx: Context) {
        val rows = node.select("tr")
        val cellRows = rows.map { row -> row.children().filter { it.tagName().let { t -> t == "th" || t == "td" } } }
        val columns = cellRows.maxOfOrNull { it.size } ?: 0
        if (columns == 0) return
        ctx.output.append("\n\n#table(\n  columns: ").append(columns).append(",\n")
        for (cells in cellRows) {
            for (cell in cells) {
                val cellCtx = Context(ctx.footnotedUrls) // shared, so URL dedup spans the whole page
                walkDescendants(cell, cellCtx, cell.tagName().lowercase())
                ctx.output.append("  [").append(cellCtx.output.toString().trim()).append("],\n")
            }
        }
        ctx.output.append(")\n\n")
    }

    private fun textContent(node: Node, out: StringBuilder) {
        if (node is TextNode) {
            out.append(node.wholeText)
        } else {
            for (child in node.childNodes()) textContent(child, out)
        }
    }

    private fun inlineEdgeWhitespace(node: Node): Pair<Boolean, Boolean> {
        val text = StringBuilder()
        textContent(node, text)
        return (text.isNotEmpty() && text.first().isWhitespace()) to
            (text.isNotEmpty() && text.last().isWhitespace())
    }

    private fun walkDescendants(node: Node, ctx: Context, tagName: String?) {
        if (tagName != null) ctx.tagStack.addLast(tagName)
        for (child in node.childNodes()) walk(child, ctx)
        if (tagName != null) ctx.tagStack.removeLast()
    }

    private fun Element.attrOrNull(name: String): String? = if (hasAttr(name)) attr(name) else null

    /** Minimum backtick run that won't collide with a backtick run already in `text`. */
    private fun rawFence(text: String, minLen: Int): String {
        var longest = 0
        var run = 0
        for (c in text) {
            if (c == '`') { run++; longest = maxOf(longest, run) } else run = 0
        }
        return "`".repeat(maxOf(minLen, longest + 1))
    }

    private fun escapeQuotes(text: String): String =
        if ('"' !in text) text else text.replace("\"", "\\\"")

    /** Neutralise Typst-active characters in prose text: markup delimiters and leading list/heading markers. */
    private fun escapeTypstText(text: String): String {
        val startsWithMarker = text.firstOrNull()?.let { it in "=-+" } ?: false
        if (!startsWithMarker && text.none { it in "*_<>" }) return text
        val out = StringBuilder()
        if (startsWithMarker) out.append('\\')
        for (c in text) {
            if (c in "*_<>") out.append('\\')
            out.append(c)
        }
        return out.toString()
    }
}
