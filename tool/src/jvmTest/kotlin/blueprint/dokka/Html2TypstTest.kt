package blueprint.dokka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Html2TypstTest {

    private fun convert(html: String): String = Html2Typst.convert(html)

    @Test
    fun paragraphBecomesPlainText() {
        assertEquals("hello world", convert("<p>hello world</p>"))
    }

    @Test
    fun headingLevelsUseMatchingEqualsCount() {
        assertEquals("=== Title", convert("<h3>Title</h3>"))
    }

    @Test
    fun headingWithIdEmitsTypstLabel() {
        assertEquals("= Title <intro>", convert("""<h1 id="intro">Title</h1>"""))
    }

    @Test
    fun boldAndItalicUseTypstDelimiters() {
        assertEquals("*bold* and _em_", convert("<p><b>bold</b> and <i>em</i></p>"))
    }

    @Test
    fun underlineAndStrikeUseTypstFunctions() {
        assertEquals(
            "#underline[u] #strike[s]",
            convert("<p><u>u</u> <s>s</s></p>")
        )
    }

    @Test
    fun subAndSuperUseTypstFunctions() {
        assertEquals("H#sub[2]O and x#super[2]", convert("<p>H<sub>2</sub>O and x<sup>2</sup></p>"))
    }

    @Test
    fun unorderedListUsesDashMarkers() {
        assertEquals("- one\n- two", convert("<ul><li>one</li><li>two</li></ul>"))
    }

    @Test
    fun orderedListUsesPlusMarkers() {
        assertEquals("+ one\n+ two", convert("<ol><li>one</li><li>two</li></ol>"))
    }

    @Test
    fun linkBecomesTypstLinkFunction() {
        assertEquals(
            """#link("https://x.test")[go]""",
            convert("""<a href="https://x.test">go</a>""")
        )
    }

    @Test
    fun pdfLinkGetsAFootnoteSpellingOutTheUrl() {
        assertEquals(
            """#link("/index.pdf")[Download]#footnote[/index.pdf]""",
            convert("""<a href="/index.pdf">Download</a>""")
        )
    }

    @Test
    fun nonPdfLinkGetsNoFootnote() {
        assertEquals(
            """#link("/tool")[The tool]""",
            convert("""<a href="/tool">The tool</a>""")
        )
    }

    @Test
    fun pdfLinkWithQueryOrFragmentStillGetsAFootnote() {
        assertEquals(
            """#link("/index.pdf?x=1")[Download]#footnote[/index.pdf?x=1]""",
            convert("""<a href="/index.pdf?x=1">Download</a>""")
        )
    }

    @Test
    fun blockquoteUsesTypstQuoteFunction() {
        val out = convert("<blockquote>said something</blockquote>")
        assertTrue(out.contains("#quote(block: true)["))
        assertTrue(out.contains("said something"))
    }

    @Test
    fun standaloneInlineCodeUsesSingleBacktickSpan() {
        assertEquals("`val x = 1`", convert("<code>val x = 1</code>"))
    }

    @Test
    fun blockCodeWithLanguageClassUsesFencedRawBlock() {
        val out = convert("""<pre><code class="language-kotlin">fun foo() {}</code></pre>""")
        assertTrue(out.contains("```kotlin"))
        assertTrue(out.contains("fun foo() {}"))
    }

    @Test
    fun blockCodeFenceGrowsToAvoidBacktickCollision() {
        val out = convert("<pre><code>has ``` in it</code></pre>")
        assertTrue(out.startsWith("````"))
    }

    @Test
    fun tableHeaderAndBodyRowsBecomeTypstTable() {
        val out = convert(
            "<table><tr><th>Name</th><th>Type</th></tr><tr><td>x</td><td>Int</td></tr></table>"
        )
        assertTrue(out.contains("#table("))
        assertTrue(out.contains("columns: 2"))
        assertTrue(out.contains("[Name]"))
        assertTrue(out.contains("[Int]"))
    }

    @Test
    fun unrecognizedTagFallsBackToDescendantsInsteadOfCrashing() {
        // Upstream html2typst panics on <details>/<summary>; this port renders
        // through instead of failing the whole conversion for one tag.
        assertEquals("summary text", convert("<details><summary>summary text</summary></details>"))
    }

    @Test
    fun activeCharactersAreEscaped() {
        assertEquals("2 \\* 2 \\< 5", convert("<p>2 * 2 &lt; 5</p>"))
    }

    @Test
    fun leadingListMarkerCharacterIsEscapedInPlainText() {
        assertEquals("\\- not a list item", convert("<p>- not a list item</p>"))
    }

    @Test
    fun imageWithPlainSrcAndAltBecomesFigure() {
        val out = convert("""<img src="diagram.png" alt="A diagram">""")
        assertTrue(out.contains("#figure(caption: [A diagram]"))
        assertTrue(out.contains("\"diagram.png\""))
    }

    @Test
    fun imageWithBase64DataUriDecodesToBytesLiteral() {
        // "hi" base64-encoded
        val out = convert("""<img src="data:image/png;base64,aGk=" alt="tiny">""")
        assertTrue(out.contains("bytes((104, 105))"))
    }
}
