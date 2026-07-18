package blueprint.ast

import kotlinx.serialization.json.*

/**
 * A lightweight wrapper over MyST's mdast-based AST.
 *
 * MyST nodes are polymorphic (dozens of optional fields), so instead of a rigid
 * data class per node type we keep the raw JsonObject and expose typed accessors.
 * This is what lets the SAME core parse whatever `mystmd build` emits without
 * breaking every time a new field appears.
 */
class MystNode(private val obj: JsonObject) {
    val type: String get() = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: "unknown"
    val value: String? get() = (obj["value"] as? JsonPrimitive)?.contentOrNull

    val children: List<MystNode>
        get() = (obj["children"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let(::MystNode) }
            ?: emptyList()

    /** Arbitrary string field, e.g. kind, url, lang, identifier, alt. */
    fun str(key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull

    /** Nested JSON object field as a MystNode, e.g. a page document's `mdast` tree. */
    fun child(key: String): MystNode? = (obj[key] as? JsonObject)?.let(::MystNode)

    /** Source pointer for provenance comments, if mystmd attached one. */
    val source: String?
        get() = str("key") ?: str("source")
            ?: ((obj["position"] as? JsonObject)?.get("start") as? JsonObject)
                ?.get("line")?.let { "line ${(it as JsonPrimitive).content}" }

    /** Page frontmatter (present on the root node of a resolved page). */
    val frontmatter: JsonObject? get() = obj["frontmatter"] as? JsonObject
    fun frontmatterStr(key: String): String? =
        (frontmatter?.get(key) as? JsonPrimitive)?.contentOrNull

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(astJson: String): MystNode =
            MystNode(json.parseToJsonElement(astJson).jsonObject)
    }
}
