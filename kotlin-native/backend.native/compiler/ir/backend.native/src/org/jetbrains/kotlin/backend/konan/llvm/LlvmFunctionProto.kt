/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.llvm

import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import llvm.*
import org.jetbrains.kotlin.backend.konan.RuntimeNames
import org.jetbrains.kotlin.backend.konan.ir.llvmSymbolOrigin
import org.jetbrains.kotlin.descriptors.konan.CompiledKlibModuleOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.hasAnnotation

interface LlvmFunctionPrototype {
    val llvmReturnType: LLVMTypeRef

    val llvmFunctionType: LLVMTypeRef
}

interface LlvmCallSiteAttributeProvider : LlvmFunctionPrototype {
    // TODO: Do we need function attributes here?
    fun addCallSiteAttributes(callSite: LLVMValueRef)

    companion object {
        fun makeEmpty(functionType: LLVMTypeRef) = object : LlvmCallSiteAttributeProvider {
            override fun addCallSiteAttributes(callSite: LLVMValueRef) {}

            override val llvmReturnType: LLVMTypeRef =
                    functionType

            override val llvmFunctionType: LLVMTypeRef =
                    LLVMGetReturnType(functionType)!!
        }
    }
}

interface LlvmFunctionDeclarationAttributeProvider : LlvmFunctionPrototype {
    fun addFunctionAttributes(function: LLVMValueRef)
}

class LlvmFunctionAttributesCopier(private val externalFunction: LLVMValueRef) :
        LlvmCallSiteAttributeProvider, LlvmFunctionDeclarationAttributeProvider {

    override val llvmFunctionType: LLVMTypeRef by lazy {
        getFunctionType(externalFunction)
    }

    override val llvmReturnType: LLVMTypeRef by lazy {
        LLVMGetReturnType(llvmFunctionType)!!
    }

    override fun addCallSiteAttributes(callSite: LLVMValueRef) {
        for (index in LLVMAttributeReturnIndex until LLVMCountParams(externalFunction)) {
            copyAttributesAtIndex(index) { attributeRef ->
                LLVMAddCallSiteAttribute(callSite, index, attributeRef)
            }
        }
    }

    private inline fun copyAttributesAtIndex(index: Int, applyAttribute: (LLVMAttributeRef) -> Unit) {
        val count = LLVMGetAttributeCountAtIndex(externalFunction, index)
        memScoped {
            val attributes = allocArray<LLVMAttributeRefVar>(count)
            LLVMGetAttributesAtIndex(externalFunction, index, attributes)
            (0 until count).forEach {
                applyAttribute(attributes[it]!!)
            }
        }
    }

    override fun addFunctionAttributes(function: LLVMValueRef) {
        for (index in LLVMAttributeFunctionIndex until LLVMCountParams(externalFunction)) {
            copyAttributesAtIndex(index) { attributeRef ->
                LLVMAddAttributeAtIndex(function, index, attributeRef)
            }
        }
    }
}

private fun addCallSiteAttributesAtIndex(context: LLVMContextRef, callSite: LLVMValueRef, index: Int, attributes: List<LlvmAttribute>) {
    attributes.forEach { attribute ->
        val llvmAttributeRef = createLlvmEnumAttribute(context, attribute.asAttributeKindId())
        LLVMAddCallSiteAttribute(callSite, index, llvmAttributeRef)
    }
}

private fun addDeclarationAttributesAtIndex(context: LLVMContextRef, function: LLVMValueRef, index: Int, attributes: List<LlvmAttribute>) {
    attributes.forEach { attribute ->
        val llvmAttributeRef = createLlvmEnumAttribute(context, attribute.asAttributeKindId())
        LLVMAddAttributeAtIndex(function, index, llvmAttributeRef)
    }
}

class VirtualFunctionProto(
        private val returnType: AttributedLlvmType,
        private val parameterTypes: List<AttributedLlvmType> = emptyList(),
        private val isVararg: Boolean
) : LlvmCallSiteAttributeProvider {

    override val llvmFunctionType by lazy {
        functionType(returnType.llvmType, isVararg, parameterTypes.map { it.llvmType })
    }

    override val llvmReturnType: LLVMTypeRef =
            returnType.llvmType

    override fun addCallSiteAttributes(callSite: LLVMValueRef) {
        val caller = LLVMGetBasicBlockParent(LLVMGetInstructionParent(callSite))
        val llvmContext = LLVMGetModuleContext(LLVMGetGlobalParent(caller))!!
        addCallSiteAttributesAtIndex(llvmContext, callSite, LLVMAttributeReturnIndex, returnType.attributes)
        repeat(parameterTypes.count()) {
            addCallSiteAttributesAtIndex(llvmContext, callSite, it + 1, parameterTypes[it].attributes)
        }
    }
}

class LlvmFunctionProto(
        val name: String,
        val returnType: AttributedLlvmType,
        val parameterTypes: List<AttributedLlvmType> = emptyList(),
        val functionAttributes: List<LlvmAttribute> = emptyList(),
        val origin: CompiledKlibModuleOrigin,
        val isVararg: Boolean = false,
        val independent: Boolean = false,
) : LlvmCallSiteAttributeProvider, LlvmFunctionDeclarationAttributeProvider {

    override val llvmFunctionType by lazy {
        functionType(returnType.llvmType, isVararg, parameterTypes.map { it.llvmType })
    }

    override val llvmReturnType: LLVMTypeRef =
            returnType.llvmType

    override fun addCallSiteAttributes(callSite: LLVMValueRef) {
        val caller = LLVMGetBasicBlockParent(LLVMGetInstructionParent(callSite))
        val llvmContext = LLVMGetModuleContext(LLVMGetGlobalParent(caller))!!
        addCallSiteAttributesAtIndex(llvmContext, callSite, LLVMAttributeReturnIndex, returnType.attributes)
        repeat(parameterTypes.count()) {
            addCallSiteAttributesAtIndex(llvmContext, callSite, it + 1, parameterTypes[it].attributes)
        }
    }

    override fun addFunctionAttributes(function: LLVMValueRef) {
        val llvmContext = LLVMGetModuleContext(LLVMGetGlobalParent(function))!!
        addDeclarationAttributesAtIndex(llvmContext, function, LLVMAttributeFunctionIndex, functionAttributes)
        addDeclarationAttributesAtIndex(llvmContext, function, LLVMAttributeReturnIndex, returnType.attributes)
        repeat(parameterTypes.count()) {
            addDeclarationAttributesAtIndex(llvmContext, function, it + 1, parameterTypes[it].attributes)
        }
    }
}

fun RuntimeAware.LlvmFunctionProto(irFunction: IrFunction, symbolName: String): LlvmFunctionProto {
    return LlvmFunctionProto(
            name = symbolName,
            returnType = getLlvmFunctionReturnType(irFunction),
            parameterTypes = getLlvmFunctionParameterTypes(irFunction),
            origin = irFunction.llvmSymbolOrigin,
            independent = irFunction.hasAnnotation(RuntimeNames.independent)
    )
}

fun RuntimeAware.VirtualFunctionProto(irFunction: IrFunction): LlvmCallSiteAttributeProvider {
    return VirtualFunctionProto(
            returnType = getLlvmFunctionReturnType(irFunction),
            parameterTypes = getLlvmFunctionParameterTypes(irFunction),
            isVararg = false,
    )
}