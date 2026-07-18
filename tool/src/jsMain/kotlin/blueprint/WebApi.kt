package blueprint

import kotlin.js.JsExport

/**
 * JS target. The IDENTICAL core, exported for the browser playground so the docs
 * site can show "paste MyST AST -> get MDX" live — proving the shared-code claim
 * rather than just asserting it.
 */
@JsExport
fun transpileToMdx(astJson: String, sourcePath: String = "playground.md"): String =
    Transpiler.transpile(astJson, sourcePath)
