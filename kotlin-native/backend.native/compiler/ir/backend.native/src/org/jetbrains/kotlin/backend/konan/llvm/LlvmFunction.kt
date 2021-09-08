/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.llvm

import llvm.LLVMAddAttributeAtIndex
import llvm.LLVMGetTypeContext
import llvm.LLVMValueRef
import org.jetbrains.kotlin.backend.konan.RuntimeNames
import org.jetbrains.kotlin.backend.konan.ir.llvmSymbolOrigin
import org.jetbrains.kotlin.descriptors.konan.CompiledKlibModuleOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.hasAnnotation

class LlvmFunction(
        val name: String,
        val returnType: AttributedLlvmType,
        val parameterTypes: List<AttributedLlvmType> = emptyList(),
        val functionAttributes: List<LlvmAttribute> = emptyList(),
        val origin: CompiledKlibModuleOrigin,
        val isVararg: Boolean = false,
        val independent: Boolean = false,
) {

    val llvmFunctionType by lazy {
        functionType(returnType.llvmType, isVararg, parameterTypes.map { it.llvmType })
    }

    fun addAttributes(function: LLVMValueRef) {
        addAttributesToFunction(function)
        addAttributesToReturnType(function)
        repeat(parameterTypes.count()) {
            addAttributesToParameter(function, it)
        }
    }

    private fun addAttributesToFunction(function: LLVMValueRef) {
        functionAttributes.forEach {
            addLlvmFunctionEnumAttribute(function, it.asAttributeKindId())
        }
    }

    private fun addAttributesToReturnType(function: LLVMValueRef) {
        val llvmAttributes = returnType.attributes.map { it.asAttributeKindId() }
        addLlvmAttributesToParameter(function, 0, llvmAttributes)
    }

    private fun addAttributesToParameter(function: LLVMValueRef, index: Int) {
        val llvmAttributes = parameterTypes[index].attributes.map { it.asAttributeKindId() }
        addLlvmAttributesToParameter(function, index + 1, llvmAttributes)
    }

    private fun addLlvmAttributesToParameter(function: LLVMValueRef, index: Int, llvmAttributes: List<LLVMAttributeKindId>) {
        llvmAttributes.forEach {
            val attribute = createLlvmEnumAttribute(LLVMGetTypeContext(function.type)!!, it)
            LLVMAddAttributeAtIndex(function, index, attribute)
        }
    }
}

fun RuntimeAware.LlvmFunction(irFunction: IrFunction): LlvmFunction {
    return LlvmFunction(
            name = irFunction.computeSymbolName(),
            returnType = getLlvmFunctionReturnType(irFunction),
            parameterTypes = getLlvmFunctionParameterTypes(irFunction),
            origin = irFunction.llvmSymbolOrigin,
            independent = irFunction.hasAnnotation(RuntimeNames.independent)
    )
}