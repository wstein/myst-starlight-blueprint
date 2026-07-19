package blueprint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderDokkaMdxPageTest {

    @Test
    fun frontmatterTitleComesFromHtmlTitleTag() {
        val out = renderDokkaMdxPage("<html><head><title>MdxEscaper</title></head><body></body></html>", "fallback")
        assertTrue(out.startsWith("---\ntitle: MdxEscaper\n---\n\n"))
    }

    @Test
    fun blankHtmlTitleFallsBackToProvidedTitle() {
        val out = renderDokkaMdxPage("<html><head><title></title></head><body></body></html>", "MdxEscaper")
        assertTrue(out.startsWith("---\ntitle: MdxEscaper\n---\n\n"))
    }

    @Test
    fun titleNeedingYamlQuotingIsQuoted() {
        val out = renderDokkaMdxPage("<html><head><title>Foo: Bar</title></head><body></body></html>", "fallback")
        assertTrue(out.startsWith("---\ntitle: \"Foo: Bar\"\n---\n\n"))
    }

    @Test
    fun duplicateLeadingHeadingMatchingTitleIsStripped() {
        val html = """
            <html><head><title>MdxEscaper</title></head>
            <body><div id="content"><h1>MdxEscaper</h1><p>Escapes MDX-sensitive characters.</p></div></body></html>
        """.trimIndent()
        val out = renderDokkaMdxPage(html, "fallback")
        assertEquals("---\ntitle: MdxEscaper\n---\n\nEscapes MDX-sensitive characters.", out)
    }

    @Test
    fun nonMatchingLeadingHeadingIsKept() {
        val html = """
            <html><head><title>MdxEscaper</title></head>
            <body><div id="content"><h1>Overview</h1><p>Escapes MDX-sensitive characters.</p></div></body></html>
        """.trimIndent()
        val out = renderDokkaMdxPage(html, "fallback")
        assertEquals("---\ntitle: MdxEscaper\n---\n\n# Overview\n\nEscapes MDX-sensitive characters.", out)
    }
}

class RewriteModuleIndexLinksTest {

    @Test
    fun subtreePrefixIsStrippedAndFirstSegmentDotsAreHyphenated() {
        val out = rewriteModuleIndexLinks("[blueprint.ast](myst2mdx/blueprint.ast/index.html)", "myst2mdx")
        assertEquals("[blueprint.ast](blueprint-ast/index.html)", out)
    }

    @Test
    fun rootPackageLinkWithNoDotsIsUnaffectedBeyondPrefixStrip() {
        val out = rewriteModuleIndexLinks("[blueprint](myst2mdx/blueprint/index.html)", "myst2mdx")
        assertEquals("[blueprint](blueprint/index.html)", out)
    }

    @Test
    fun linksOutsideTheSubtreeAreLeftAlone() {
        val out = rewriteModuleIndexLinks("[stdlib](https://kotlinlang.org/api/core/kotlin-stdlib/index.html)", "myst2mdx")
        assertEquals("[stdlib](https://kotlinlang.org/api/core/kotlin-stdlib/index.html)", out)
    }
}
