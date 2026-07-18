package blueprint.dokka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Grounds Html2Typst/Html2Mdx in a real `gradle dokkaGenerate` capture (this
 * project's own MdxEscaper class page) rather than only hand-written HTML —
 * same reasoning as RealAstFixtureTest for the MyST side: hand-guessed
 * structure misses what real tool output actually looks like. Caught three
 * real gaps this way (none visible from hand-written test HTML): Dokka's
 * "Link copied to clipboard" copy-button chrome leaking into prose, page
 * content needing scoping to id="content" rather than the whole <body> (which
 * includes global site nav), and blank-line runs from deeply nested chrome
 * divs. All three are fixed in the converters themselves; these assertions
 * are the regression net.
 */
class RealDokkaFixtureTest {

    private val html = checkNotNull(javaClass.classLoader.getResourceAsStream("dokka-html/mdx-escaper.html")) {
        "missing test resource dokka-html/mdx-escaper.html"
    }.bufferedReader().readText()

    @Test
    fun typstConversionIncludesRealContentWithoutChrome() {
        val out = Html2Typst.convert(html)
        assertTrue(out.contains("= MdxEscaper"))
        assertTrue(out.contains("MDX \"escaping-hell\" is really FOUR channels"))
        assertTrue(out.contains("Channel 1 — prose text nodes"))
        assertTrue(out.contains("Channel 4 — component attribute values"))
        assertFalse(out.contains("Link copied to clipboard"))
        assertFalse(out.contains("breadcrumbs"))
    }

    @Test
    fun typstConversionHasNoExcessiveBlankLines() {
        val out = Html2Typst.convert(html)
        assertFalse(out.contains("\n\n\n"))
    }

    @Test
    fun mdxConversionIncludesRealContentWithoutChrome() {
        val out = Html2Mdx.convert(html)
        assertTrue(out.contains("# MdxEscaper"))
        assertTrue(out.contains("MDX \"escaping-hell\" is really FOUR channels"))
        assertTrue(out.contains("Channel 2 — inline code / fenced code"))
        assertFalse(out.contains("Link copied to clipboard"))
    }

    @Test
    fun mdxConversionPreservesFunctionSignatureLinks() {
        val out = Html2Mdx.convert(html)
        assertTrue(out.contains("[prose](prose.html)"))
        assertTrue(out.contains("[fence](fence.html)"))
    }

    @Test
    fun typstConversionDedupsRepeatedStdlibLinkFootnotes() {
        // This page's function signatures link to the same kotlin-stdlib
        // String page many times over (every `value: String` / `: String`
        // return type). Without dedup this was one footnote per occurrence —
        // found by actually reading the converted output, not guessed.
        val out = Html2Typst.convert(html)
        val stdlibUrl = "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html"
        val linkCount = Regex(Regex.escape("#link(\"$stdlibUrl\")")).findAll(out).count()
        val footnoteCount = Regex(Regex.escape("#footnote[$stdlibUrl]")).findAll(out).count()
        assertTrue(linkCount > 1, "expected the stdlib String link to repeat on this page, got $linkCount")
        assertEquals(1, footnoteCount)
    }
}
