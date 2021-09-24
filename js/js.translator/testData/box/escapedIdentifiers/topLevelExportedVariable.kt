// IGNORE_BACKEND: JS
// RUN_PLAIN_BOX_FUNCTION
// INFER_MAIN_MODULE

// MODULE: export-invalid-name-variable
// FILE: lib.kt

@JsExport()
val `my@invalid variable` = 42

// FILE: test.js
function box() {
    var variableValue = this["export-invalid-name-variable"]["my@invalid variable"]

    if (variableValue !== 42)
        return "false: expect exproted 'my@invalid variable' to be 42 but it equals " + variableValue

    return "OK"
}
