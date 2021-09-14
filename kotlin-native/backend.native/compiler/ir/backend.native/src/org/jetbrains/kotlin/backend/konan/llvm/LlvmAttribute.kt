/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.llvm

// TODO: Split into several classes (parameter attribute, function attribute)
enum class LlvmAttribute(private val llvmAttributeName: String) {
    SignExt("signext"),
    ZeroExt("zeroext"),
    NoUnwind("nounwind"),
    NoReturn("noreturn"),
    NoInline("noinline");

    fun asAttributeKindId(): LLVMAttributeKindId = llvmAttributeKindIdCache.getOrPut(this) {
        getLlvmAttributeKindId(llvmAttributeName)
    }

    companion object {
        private val llvmAttributeKindIdCache = mutableMapOf<LlvmAttribute, LLVMAttributeKindId>()
    }
}