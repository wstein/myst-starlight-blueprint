package blueprint.emit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeMappingTest {

    @Test
    fun isNativeIsTrueForMappedTypes() {
        assertTrue(NodeMapping.isNative("paragraph"))
        assertTrue(NodeMapping.isNative("block"))
        assertTrue(NodeMapping.isNative("admonition"))
    }

    @Test
    fun isNativeIsFalseForUnmappedTypes() {
        assertFalse(NodeMapping.isNative("mystDirective"))
        assertFalse(NodeMapping.isNative("table"))
    }

    @Test
    fun asideTypeCollapsesKnownKindsToTheFourStarlightTypes() {
        assertEquals("note", NodeMapping.asideType("note"))
        assertEquals("note", NodeMapping.asideType("seealso"))
        assertEquals("tip", NodeMapping.asideType("tip"))
        assertEquals("caution", NodeMapping.asideType("warning"))
        assertEquals("danger", NodeMapping.asideType("error"))
    }

    @Test
    fun asideTypeDefaultsToNoteForUnknownOrMissingKind() {
        assertEquals("note", NodeMapping.asideType("something-unmapped"))
        assertEquals("note", NodeMapping.asideType(null))
    }
}
