package blueprint.emit

/**
 * The policy layer: which MyST nodes have a native Starlight/MDX equivalent, and
 * how MyST's ~10 admonition kinds collapse onto Starlight's 4 aside types.
 * Everything here is a deliberate choice, which is exactly why it lives in code
 * you own rather than in a generic library.
 */
object NodeMapping {

    /** Node types the emitter renders natively. Anything else -> HTML fallback. */
    val NATIVE = setOf(
        "root", "paragraph", "text", "strong", "emphasis", "inlineCode",
        "code", "heading", "link", "list", "listItem", "admonition",
        "crossReference", "image", "break", "thematicBreak"
    )

    /** MyST admonition kind -> Starlight <Aside> type. */
    val ASIDE = mapOf(
        "note" to "note", "important" to "note", "hint" to "note",
        "seealso" to "note", "admonition" to "note",
        "tip" to "tip",
        "warning" to "caution", "caution" to "caution", "attention" to "caution",
        "danger" to "danger", "error" to "danger"
    )

    fun asideType(kind: String?): String = ASIDE[kind] ?: "note"
    fun isNative(type: String): Boolean = type in NATIVE
}
