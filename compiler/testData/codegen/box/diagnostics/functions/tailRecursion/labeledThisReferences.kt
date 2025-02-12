// DONT_TARGET_EXACT_BACKEND: WASM
// WASM_MUTE_REASON: IGNORED_IN_JS
// !DIAGNOSTICS: -UNUSED_PARAMETER
// IGNORE_BACKEND: JS_IR
// IGNORE_BACKEND: JS_IR_ES6

// TODO: muted automatically, investigate should it be ran for JS or not
// DONT_RUN_GENERATED_CODE: JS
// IGNORE_BACKEND: JS

class B {
    inner class C {
        tailrec fun h(counter : Int) {
            if (counter > 0) {
                this@C.h(counter - 1)
            }
        }

        <!NO_TAIL_CALLS_FOUND!>tailrec<!> fun h2(x : Any) {
            this@B.h2("no recursion") // keep vigilance
        }

    }

    fun makeC() : C = C()

    fun h2(x : Any) {
    }
}

fun box() : String {
    B().makeC().h(1000000)
    B().makeC().h2(0)
    return "OK"
}
