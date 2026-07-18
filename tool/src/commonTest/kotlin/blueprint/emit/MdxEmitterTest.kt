package blueprint.emit

import blueprint.ast.MystNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MdxEmitterTest {

    private fun emit(json: String): String = MdxEmitter().emit(MystNode.parse(json))

    @Test
    fun rootAndBlockBothPassThroughToChildren() {
        val body = """{"type":"paragraph","children":[{"type":"text","value":"hi"}]}"""
        assertEquals(
            emit("""{"type":"root","children":[$body]}"""),
            emit("""{"type":"block","children":[$body]}""")
        )
    }

    @Test
    fun headingUsesDepthForHashCount() {
        assertEquals(
            "### Title\n\n",
            emit("""{"type":"heading","depth":3,"children":[{"type":"text","value":"Title"}]}""")
        )
    }

    @Test
    fun headingDefaultsToDepthOneWhenMissing() {
        assertEquals(
            "# Title\n\n",
            emit("""{"type":"heading","children":[{"type":"text","value":"Title"}]}""")
        )
    }

    @Test
    fun listRendersItemsAsMarkdownBullets() {
        val json = """{"type":"list","children":[
            {"type":"listItem","children":[{"type":"text","value":"one"}]},
            {"type":"listItem","children":[{"type":"text","value":"two"}]}
        ]}"""
        assertEquals("- one\n- two\n\n", emit(json))
    }

    @Test
    fun inlineFormattingNestsStrongEmphasisAndLinks() {
        val json = """{"type":"paragraph","children":[
            {"type":"strong","children":[{"type":"text","value":"bold"}]},
            {"type":"emphasis","children":[{"type":"text","value":"em"}]},
            {"type":"link","url":"https://x.test","children":[{"type":"text","value":"x"}]}
        ]}"""
        assertEquals("**bold***em*[x](https://x.test)\n\n", emit(json))
    }

    @Test
    fun codeBlockUsesFenceWithLanguage() {
        assertEquals(
            "```kotlin\nval x = 1\n```\n\n",
            emit("""{"type":"code","lang":"kotlin","value":"val x = 1"}""")
        )
    }

    @Test
    fun jsEvalCodeBecomesCodeMirrorIslandAndRegistersImport() {
        val emitter = MdxEmitter()
        val node = MystNode.parse("""{"type":"code","lang":"js-eval","value":"console.log(1)"}""")
        val out = emitter.emit(node)
        assertTrue(out.startsWith("<CodeMirrorEval code={`console.log(1)`} />"))
        assertTrue(emitter.imports.any { it.contains("CodeMirrorEval") })
    }

    @Test
    fun admonitionWithDefaultTitleOmitsExplicitTitleAttr() {
        val emitter = MdxEmitter()
        val node = MystNode.parse(
            """{"type":"admonition","kind":"note","children":[
                {"type":"admonitionTitle","children":[{"type":"text","value":"Note"}]},
                {"type":"paragraph","children":[{"type":"text","value":"body"}]}
            ]}"""
        )
        val out = emitter.emit(node)
        assertEquals("<Aside type=\"note\">\nbody\n</Aside>\n\n", out)
        assertTrue(emitter.imports.any { it.contains("Aside") })
    }

    @Test
    fun admonitionWithCustomTitleAddsExplicitTitleAttr() {
        val node = MystNode.parse(
            """{"type":"admonition","kind":"warning","children":[
                {"type":"admonitionTitle","children":[{"type":"text","value":"Heads up"}]},
                {"type":"paragraph","children":[{"type":"text","value":"body"}]}
            ]}"""
        )
        val out = MdxEmitter().emit(node)
        assertEquals("<Aside type=\"caution\" title=\"Heads up\">\nbody\n</Aside>\n\n", out)
    }

    @Test
    fun unknownNativeTypeFallsBackToInline() {
        // "strong" is in NodeMapping.NATIVE but has no dedicated block-level `emit`
        // branch, so it must fall through to the generic native inline() rendering.
        assertEquals(
            "hi\n\n",
            emit("""{"type":"strong","children":[{"type":"text","value":"hi"}]}""")
        )
    }

    @Test
    fun unknownNonNativeTypeFallsBackToHtml() {
        val json = """{"type":"mystDirective","children":[{"type":"text","value":"hi"}]}"""
        assertEquals("<div>hi</div>\n\n", emit(json))
    }

    @Test
    fun thematicBreakRendersAsMarkdownRule() {
        assertEquals("---\n\n", emit("""{"type":"thematicBreak"}"""))
    }
}
