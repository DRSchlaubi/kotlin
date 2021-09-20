/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.llvm

interface LlvmAttribute {
    fun asAttributeKindId(): LLVMAttributeKindId
}

enum class LlvmParameterAttribute(private val llvmAttributeName: String) : LlvmAttribute {
    SignExt("signext"),
    ZeroExt("zeroext");

    override fun asAttributeKindId(): LLVMAttributeKindId = llvmAttributeKindIdCache.getOrPut(this) {
        getLlvmAttributeKindId(llvmAttributeName)
    }

    companion object {
        private val llvmAttributeKindIdCache = mutableMapOf<LlvmParameterAttribute, LLVMAttributeKindId>()
    }
}

enum class LlvmFunctionAttribute(private val llvmAttributeName: String) : LlvmAttribute {
    NoUnwind("nounwind"),
    NoReturn("noreturn"),
    NoInline("noinline");

    override fun asAttributeKindId(): LLVMAttributeKindId = llvmAttributeKindIdCache.getOrPut(this) {
        getLlvmAttributeKindId(llvmAttributeName)
    }

    companion object {
        private val llvmAttributeKindIdCache = mutableMapOf<LlvmFunctionAttribute, LLVMAttributeKindId>()
    }
}