/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.text

import kotlin.test.*

class RegexJsTest {
    @Test
    fun replace() {
        // js capturing group name can contain Unicode letters, $, _, and digits (0-9), but may not start with a digit.
        // jvm capturing group name can contain a-z, A-Z, and 0-9, but may not start with a digit.
        // make sure reference to capturing group name in K/JS Regex.replace(input, replacement) obeys K/JVM rules
        val input = "123-456"
        val pattern = Regex("(?<first_part>\\d+)-(?<second_part>\\d+)")

        assertEquals("123/456", pattern.replace(input, "$1/$2"))
        assertEquals("123/456", pattern.replaceFirst(input, "$1/$2"))

        assertFailsWith<IllegalArgumentException> { pattern.replace(input, "\${first}/\${second}") }
        assertFailsWith<IllegalArgumentException> { pattern.replace(input, "\${first_part}/\${second_part}") }
        assertFailsWith<IllegalArgumentException> { pattern.replaceFirst(input, "\${first_part}/\${second_part}") }
    }
}