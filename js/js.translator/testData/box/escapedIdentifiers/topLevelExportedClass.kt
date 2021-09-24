// IGNORE_BACKEND: JS
// RUN_PLAIN_BOX_FUNCTION
// INFER_MAIN_MODULE

// MODULE: export-invalid-name-class
// FILE: lib.kt

@JsExport()
class `invalid@class-name `() {
    fun foo(): Int = 42
}

// FILE: test.js
function box() {
    var InvalidClass = this["export-invalid-name-class"]["invalid@class-name "]
    var instance = new InvalidClass()
    var value = instance.foo()

    if (value !== 42)
        return "false: expect exproted class function to return 42 but it equals " + value

    return "OK"
}
