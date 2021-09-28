// IGNORE_BACKEND: JS
// RUN_PLAIN_BOX_FUNCTION
// INFER_MAIN_MODULE
// !LANGUAGE: +JsAllowInvalidCharsIdentifiersEscaping

// MODULE: export-invalid-name-function
// FILE: lib.kt

@JsExport()
fun `@do something like-this`(): Int = 42

// FILE: test.js
function box() {
    var value = this["export-invalid-name-function"]["@do something like-this"]()

    if (value !== 42)
        return "false: expect exproted function '@do something like-this' to return 42 but it equals " + value

    return "OK"
}
