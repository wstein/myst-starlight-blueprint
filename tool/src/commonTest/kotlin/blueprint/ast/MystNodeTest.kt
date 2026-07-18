package blueprint.ast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MystNodeTest {

    @Test
    fun parsesTypeValueAndChildren() {
        val node = MystNode.parse(
            """{"type":"paragraph","children":[{"type":"text","value":"hi"}]}"""
        )
        assertEquals("paragraph", node.type)
        assertNull(node.value)
        assertEquals(1, node.children.size)
        assertEquals("text", node.children[0].type)
        assertEquals("hi", node.children[0].value)
    }

    @Test
    fun defaultsTypeToUnknownWhenMissing() {
        assertEquals("unknown", MystNode.parse("{}").type)
    }

    @Test
    fun childrenIsEmptyWhenAbsent() {
        assertEquals(emptyList(), MystNode.parse("""{"type":"text","value":"hi"}""").children)
    }

    @Test
    fun strReadsArbitraryStringFields() {
        val node = MystNode.parse("""{"type":"link","url":"https://example.com"}""")
        assertEquals("https://example.com", node.str("url"))
        assertNull(node.str("missing"))
    }

    @Test
    fun childReadsNestedObjectField() {
        val node = MystNode.parse("""{"type":"page","mdast":{"type":"root","children":[]}}""")
        assertEquals("root", node.child("mdast")?.type)
        assertNull(node.child("missing"))
    }

    @Test
    fun frontmatterStrReadsFrontmatterFields() {
        val node = MystNode.parse(
            """{"type":"page","frontmatter":{"title":"Hello","description":"A page"}}"""
        )
        assertEquals("Hello", node.frontmatterStr("title"))
        assertEquals("A page", node.frontmatterStr("description"))
        assertNull(node.frontmatterStr("missing"))
    }

    @Test
    fun frontmatterStrIsNullWithoutFrontmatter() {
        assertNull(MystNode.parse("""{"type":"page"}""").frontmatterStr("title"))
    }

    @Test
    fun sourceFallsBackFromKeyToPositionLine() {
        val withKey = MystNode.parse("""{"type":"text","key":"abc123"}""")
        assertEquals("abc123", withKey.source)

        val withPosition = MystNode.parse(
            """{"type":"text","position":{"start":{"line":7}}}"""
        )
        assertEquals("line 7", withPosition.source)

        assertNull(MystNode.parse("""{"type":"text"}""").source)
    }
}
