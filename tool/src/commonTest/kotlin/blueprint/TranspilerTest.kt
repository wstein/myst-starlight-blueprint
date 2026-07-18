package blueprint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TranspilerTest {

    @Test
    fun transpilesFrontmatterHeadingAndParagraph() {
        val ast = """
            {
              "frontmatter": { "title": "Hello", "description": "A page" },
              "mdast": {
                "type": "root",
                "children": [
                  { "type": "block", "children": [
                    { "type": "heading", "depth": 1, "children": [{ "type": "text", "value": "Hello" }] },
                    { "type": "paragraph", "children": [{ "type": "text", "value": "hello world" }] }
                  ]}
                ]
              }
            }
        """.trimIndent()

        val mdx = Transpiler.transpile(ast, sourcePath = "myst/hello.md")

        assertTrue(mdx.startsWith("---\ntitle: Hello\ndescription: A page\n---\n"))
        assertTrue(mdx.contains("GENERATED from myst/hello.md by myst-mdx-transpiler"))
        assertTrue(mdx.contains("# Hello"))
        assertTrue(mdx.contains("hello world"))
    }

    @Test
    fun fallsBackToFileNameWhenFrontmatterHasNoTitle() {
        val ast = """{"mdast":{"type":"root","children":[]}}"""
        val mdx = Transpiler.transpile(ast, sourcePath = "myst/nested/my-page.md")
        assertTrue(mdx.contains("title: my-page"))
    }

    @Test
    fun quotesTitlesContainingYamlSpecialCharacters() {
        val ast = """{"frontmatter":{"title":"A: B"},"mdast":{"type":"root","children":[]}}"""
        val mdx = Transpiler.transpile(ast, sourcePath = "myst/x.md")
        assertTrue(mdx.contains("title: \"A: B\""))
    }

    @Test
    fun addsImportsCollectedFromEmittedComponents() {
        val ast = """
            {
              "mdast": { "type": "root", "children": [
                { "type": "admonition", "kind": "tip", "children": [
                  { "type": "paragraph", "children": [{ "type": "text", "value": "tip body" }] }
                ]}
              ]}
            }
        """.trimIndent()
        val mdx = Transpiler.transpile(ast, sourcePath = "myst/x.md")
        assertTrue(mdx.contains("import { Aside } from '@astrojs/starlight/components';"))
        assertTrue(mdx.contains("<Aside type=\"tip\">"))
    }

    @Test
    fun throwsOnMissingMdastRoot() {
        val err = assertFailsWith<IllegalStateException> {
            Transpiler.transpile("""{"frontmatter":{"title":"x"}}""", sourcePath = "myst/broken.md")
        }
        assertTrue(err.message!!.contains("myst/broken.md"))
        assertTrue(err.message!!.contains("mdast"))
    }

    @Test
    fun emptyDocumentStillProducesValidFrontmatterShell() {
        val mdx = Transpiler.transpile(
            """{"mdast":{"type":"root","children":[]}}""",
            sourcePath = "myst/empty.md"
        )
        assertEquals(
            "---\ntitle: empty\n---\n\n{/* GENERATED from myst/empty.md by myst-mdx-transpiler — do not edit */}\n\n\n",
            mdx
        )
    }
}
