// IGNORE_BACKEND: JS
// !LANGUAGE: +JsAllowInvalidCharsIdentifiersEscaping

package foo

interface IA {
    val `#invalid@char value`: Int
    val __invalid_char_value: Int

    var `--invalud char@var`: String
}

@JsExport()
class A : IA {
    override val `#invalid@char value`: Int = 41
    override val __invalid_char_value: Int = 23

    override var `--invalud char@var`: String = "A: before"
}

class B : IA {
    override val `#invalid@char value`: Int = 42
    override val __invalid_char_value: Int = 24

    override var `--invalud char@var`: String = "B: before"
}

fun box(): String {
    val a: IA = A()
    val b: IA = B()

    assertEquals(23, a.__invalid_char_value)
    assertEquals(24, b.__invalid_char_value)

    assertEquals(41, a.`#invalid@char value`)
    assertEquals(42, b.`#invalid@char value`)

    assertEquals("A: before", a.`--invalud char@var`)
    assertEquals("B: before", b.`--invalud char@var`)

    a.`--invalud char@var` = "A: after"
    b.`--invalud char@var` = "B: after"

    assertEquals("A: after", a.`--invalud char@var`)
    assertEquals("B: after", b.`--invalud char@var`)

    assertEquals(41, js("a['#invalid@char value']"))
    assertEquals(js("undefined"), js("b['#invalid@char value']"))

    assertEquals("A: after", js("a['--invalud char@var']"))
    assertEquals(js("undefined"), js("b['--invalud char@var']"))

    return "OK"
}
