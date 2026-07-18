package blueprint.escape

import blueprint.ast.MystNode
import kotlin.test.Test
import kotlin.test.assertEquals

class MdxEscaperTest {

    @Test
    fun proseNeutralisesMdxActiveCharacters() {
        // Only { } < ` are MDX-active; '>' needs no escaping in prose position.
        assertEquals("a \\{b\\} \\<c> \\`d\\`", MdxEscaper.prose("a {b} <c> `d`"))
    }

    @Test
    fun prosePassesThroughPlainText() {
        assertEquals("hello world", MdxEscaper.prose("hello world"))
    }

    @Test
    fun fenceDefaultsToThreeBackticks() {
        assertEquals("```", MdxEscaper.fence("no backticks here"))
    }

    @Test
    fun fenceGrowsPastTheLongestBacktickRun() {
        assertEquals("````", MdxEscaper.fence("has ``` in it"))
        assertEquals("`````", MdxEscaper.fence("has ```` in it"))
    }

    @Test
    fun attrEscapesQuotesOpenBraceOpenAngleAndAmpersand() {
        // Only " { < & are escaped; '}' and '>' pass through unescaped in attr position.
        assertEquals("&quot;a&quot; &#123;b} &lt;c> &amp;d", MdxEscaper.attr("\"a\" {b} <c> &d"))
    }

    @Test
    fun htmlFallbackSelfClosesVoidTags() {
        val img = MystNode.parse("""{"type":"image"}""")
        assertEquals("<img />", MdxEscaper.HtmlFallback.render(img))
    }

    @Test
    fun htmlFallbackEscapesLeafText() {
        val text = MystNode.parse("""{"type":"text","value":"<a & b>"}""")
        assertEquals("&lt;a &amp; b&gt;", MdxEscaper.HtmlFallback.render(text))
    }

    @Test
    fun htmlFallbackWrapsUnknownTypesInDiv() {
        val node = MystNode.parse("""{"type":"mystDirective","children":[{"type":"text","value":"hi"}]}""")
        assertEquals("<div>hi</div>", MdxEscaper.HtmlFallback.render(node))
    }
}
