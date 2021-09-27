/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diagnostics

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.AbstractKtSourceElement

open class OffsetsOnlyPositioningStrategy {
    open fun markKtDiagnostic(element: AbstractKtSourceElement, diagnostic: KtDiagnostic): List<TextRange> {
        return mark(element.startOffset, element.endOffset)
    }

    open fun mark(
        startOffset: Int,
        endOffset: Int,
    ): List<TextRange> {
        return markElement(startOffset, endOffset)
    }
}

fun markElement(
    startOffset: Int,
    endOffset: Int,
): List<TextRange> = markRange(startOffset, endOffset)

fun markRange(
    startOffset: Int,
    endOffset: Int,
): List<TextRange> {
    return listOf(markSingleElement(startOffset, endOffset))
}

fun markSingleElement(
    startOffset: Int,
    endOffset: Int,
): TextRange {
    return TextRange(startOffset, endOffset)
}
