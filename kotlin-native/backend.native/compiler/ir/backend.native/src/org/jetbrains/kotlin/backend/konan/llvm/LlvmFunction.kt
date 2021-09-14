/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.llvm

import llvm.*
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

    fun addCallSiteAttributes(callSite: LLVMValueRef, llvmContext: LLVMContextRef) {
        // TODO: Do we need function attributes here?
        addCallSiteAttributesAtIndex(llvmContext, callSite, LLVMAttributeReturnIndex, returnType.attributes)
        repeat(parameterTypes.count()) {
            addCallSiteAttributesAtIndex(llvmContext, callSite, it + 1, parameterTypes[it].attributes)
        }
    }

    private fun addCallSiteAttributesAtIndex(context: LLVMContextRef, callSite: LLVMValueRef, index: Int, attributes: List<LlvmAttribute>) {
        attributes.forEach { attribute ->
            val llvmAttributeRef = createLlvmEnumAttribute(context, attribute.asAttributeKindId())
            LLVMAddCallSiteAttribute(callSite, index, llvmAttributeRef)
        }
    }

    fun addFunctionAttributes(function: LLVMValueRef, llvmContext: LLVMContextRef) {
        addDeclarationAttributesAtIndex(llvmContext, function, LLVMAttributeFunctionIndex, functionAttributes)
        addDeclarationAttributesAtIndex(llvmContext, function, LLVMAttributeReturnIndex, returnType.attributes)
        repeat(parameterTypes.count()) {
            addDeclarationAttributesAtIndex(llvmContext, function, it + 1, parameterTypes[it].attributes)
        }
    }


    private fun addDeclarationAttributesAtIndex(context: LLVMContextRef, function: LLVMValueRef, index: Int, attributes: List<LlvmAttribute>) {
        attributes.forEach { attribute ->
            val llvmAttributeRef = createLlvmEnumAttribute(context, attribute.asAttributeKindId())
            LLVMAddAttributeAtIndex(function, index, llvmAttributeRef)
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