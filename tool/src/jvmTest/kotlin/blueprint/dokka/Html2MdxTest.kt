package blueprint.dokka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Html2MdxTest {

    private fun convert(html: String): String = Html2Mdx.convert(html)

    @Test
    fun paragraphBecomesPlainText() {
        assertEquals("hello world", convert("<p>hello world</p>"))
    }

    @Test
    fun headingLevelsUseMatchingHashCount() {
        assertEquals("### Title", convert("<h3>Title</h3>"))
    }

    @Test
    fun boldAndItalicUseMarkdownDelimiters() {
        assertEquals("**bold** and *em*", convert("<p><b>bold</b> and <i>em</i></p>"))
    }

    @Test
    fun strikethroughUsesGfmDelimiters() {
        assertEquals("~~gone~~", convert("<s>gone</s>"))
    }

    @Test
    fun underlineAndSubSupFallBackToRawHtml() {
        assertEquals("<u>u</u> H<sub>2</sub>O", convert("<p><u>u</u> H<sub>2</sub>O</p>"))
    }

    @Test
    fun unorderedListUsesDashMarkers() {
        assertEquals("- one\n- two", convert("<ul><li>one</li><li>two</li></ul>"))
    }

    @Test
    fun orderedListUsesNumberedMarkers() {
        assertEquals("1. one\n1. two", convert("<ol><li>one</li><li>two</li></ol>"))
    }

    @Test
    fun linkBecomesMarkdownLink() {
        assertEquals("[go](https://x.test)", convert("""<a href="https://x.test">go</a>"""))
    }

    @Test
    fun imageBecomesMarkdownImage() {
        assertEquals("![a diagram](diagram.png)", convert("""<img src="diagram.png" alt="a diagram">"""))
    }

    @Test
    fun blockquoteUsesAngleBracketPrefix() {
        assertEquals("> said something", convert("<blockquote>said something</blockquote>"))
    }

    @Test
    fun standaloneInlineCodeUsesSingleBacktickSpan() {
        assertEquals("`val x = 1`", convert("<code>val x = 1</code>"))
    }

    @Test
    fun blockCodeWithLanguageClassUsesFencedBlock() {
        val out = convert("""<pre><code class="language-kotlin">fun foo() {}</code></pre>""")
        assertTrue(out.contains("```kotlin"))
        assertTrue(out.contains("fun foo() {}"))
    }

    @Test
    fun blockCodeFenceMatchesMdxEscaperMinimumOfThree() {
        // No backticks in content -> MdxEscaper.fence's own 3-backtick floor, same as myst2mdx's own code blocks.
        val out = convert("<pre><code>plain</code></pre>")
        assertTrue(out.contains("```\nplain\n```"))
    }

    @Test
    fun tableHeaderAndBodyRowsBecomeGfmTable() {
        val out = convert(
            "<table><tr><th>Name</th><th>Type</th></tr><tr><td>x</td><td>Int</td></tr></table>"
        )
        assertEquals(
            "| Name | Type |\n| --- | --- |\n| x | Int |",
            out
        )
    }

    @Test
    fun unrecognizedTagFallsBackToDescendantsInsteadOfCrashing() {
        assertEquals("summary text", convert("<details><summary>summary text</summary></details>"))
    }

    @Test
    fun proseEscapingReusesMdxEscaperSoOutputMatchesMyst2mdxConventions() {
        // MdxEscaper.prose neutralises `{`, `}`, `<`, backtick — same channel myst2mdx's own emitter uses.
        assertEquals("a \\{b\\} c", convert("<p>a {b} c</p>"))
    }

    @Test
    fun tabToggleButtonIsSkippedNotLeakedAsProse() {
        // Dokka's own section-tab control (a live UI toggle in the real site,
        // dead weight in a static export) — its label text used to leak in
        // and duplicate the real "## Types" heading that follows.
        val out = convert(
            """<div class="tabs-section"><button class="section-tab" data-togglable="TYPE">Types</button></div>
               <h2>Types</h2>"""
        )
        assertEquals("## Types", out)
    }

    @Test
    fun platformBookmarkButtonIsSkippedNotLeakedAsProse() {
        val out = convert(
            """<p>before</p><button class="platform-bookmark" data-toggle=":/jvmMain">jvm</button><p>after</p>"""
        )
        assertEquals("before\n\nafter", out)
    }
}
