/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diagnostics

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.AbstractKtSourceElement

abstract class AbstractSourceElementPositioningStrategy {
    abstract fun markDiagnostic(diagnostic: KtDiagnostic): List<TextRange>

    abstract fun isValid(element: AbstractKtSourceElement): Boolean

    companion object {

        private val defaultProxy = object : AbstractSourceElementPositioningStrategy() {
            lateinit var currentDefault: AbstractSourceElementPositioningStrategy

            override fun markDiagnostic(diagnostic: KtDiagnostic): List<TextRange> = currentDefault.markDiagnostic(diagnostic)
            override fun isValid(element: AbstractKtSourceElement): Boolean = currentDefault.isValid(element)
        }

        @JvmStatic
        fun setDefault(default: AbstractSourceElementPositioningStrategy) {
            defaultProxy.currentDefault = default
        }

        val DEFAULT: AbstractSourceElementPositioningStrategy = defaultProxy
    }
}