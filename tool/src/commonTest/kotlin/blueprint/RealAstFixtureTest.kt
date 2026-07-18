package blueprint

import blueprint.fixtures.REAL_INDEX_PAGE_AST
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test against a real mystmd output shape (see Fixtures.kt), not a
 * hand-guessed one — this is what would have caught the missing "mdast" root
 * wrapper before it ever reached CI.
 */
class RealAstFixtureTest {

    private val mdx = Transpiler.transpile(REAL_INDEX_PAGE_AST, sourcePath = "myst/index.md")

    @Test
    fun rendersFrontmatterFromRealDocument() {
        assertTrue(mdx.startsWith("---\ntitle: The Blueprint\n"))
        assertTrue(mdx.contains("description: This page is written in MyST and rendered by the pipeline it documents."))
    }

    @Test
    fun rendersHeadingsAtTheirSourceDepth() {
        assertTrue(mdx.contains("## The blueprint renders itself"))
        assertTrue(mdx.contains("### Live evaluation"))
        assertTrue(mdx.contains("### Ordinary code stays native"))
    }

    @Test
    fun rendersStrongInlineFormatting() {
        assertTrue(mdx.contains("**MyST Markdown**"))
    }

    @Test
    fun collapsesBothAdmonitionKindsWithDefaultTitlesOmitted() {
        // "note" carries mystmd's default admonitionTitle text ("Note"), and
        // "danger" collapses from mystmd's own "danger" kind — both should omit
        // an explicit title= since neither overrides the default.
        assertTrue(mdx.contains("<Aside type=\"note\">"))
        assertTrue(mdx.contains("<Aside type=\"danger\">"))
        assertEquals(1, Regex("import \\{ Aside \\}").findAll(mdx).count())
    }

    @Test
    fun mapsJsEvalCodeToTheCodeMirrorIslandWithOneImport() {
        assertTrue(mdx.contains("<CodeMirrorEval code={`const fib"))
        assertEquals(1, Regex("import CodeMirrorEval").findAll(mdx).count())
    }

    @Test
    fun leavesOrdinaryCodeAsAFencedBlock() {
        assertTrue(mdx.contains("```js\n"))
        assertTrue(mdx.contains("export const answer = 42;"))
    }
}
