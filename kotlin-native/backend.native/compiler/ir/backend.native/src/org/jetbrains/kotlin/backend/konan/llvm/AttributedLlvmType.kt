/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.llvm

import llvm.LLVMTypeRef
import org.jetbrains.kotlin.backend.common.ir.allParameters
import org.jetbrains.kotlin.backend.konan.*
import org.jetbrains.kotlin.backend.konan.unwrapToPrimitiveOrReference
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.util.isSuspend

class AttributedLlvmType(val llvmType: LLVMTypeRef, val attributes: List<LlvmAttribute> = emptyList()) {
    // TODO: Add some asserts here (e.g. check that types with bit width of <= 16 are attributed).
}

internal fun RuntimeAware.getLlvmFunctionParameterTypes(function: IrFunction): List<AttributedLlvmType> {
    val returnType = getLlvmFunctionReturnType(function).llvmType
    val paramTypes = ArrayList(function.allParameters.map { AttributedLlvmType(getLLVMType(it.type), defaultAttributesForIrType(it.type)) })
    if (function.isSuspend)
        paramTypes.add(AttributedLlvmType(kObjHeaderPtr))                       // Suspend functions have implicit parameter of type Continuation<>.
    if (isObjectType(returnType))
        paramTypes.add(AttributedLlvmType(kObjHeaderPtrPtr))

    return paramTypes
}

internal fun RuntimeAware.getLlvmFunctionReturnType(function: IrFunction): AttributedLlvmType {
    val returnType = when {
        function is IrConstructor -> AttributedLlvmType(voidType)
        function.isSuspend -> AttributedLlvmType(kObjHeaderPtr)                // Suspend functions return Any?.
        else -> AttributedLlvmType(getLLVMReturnType(function.returnType), defaultAttributesForIrType(function.returnType))
    }
    return returnType
}

private fun defaultAttributesForIrType(irType: IrType): List<LlvmAttribute> {
    return irType.unwrapToPrimitiveOrReference(
            eachInlinedClass = { _, _ -> },
            ifPrimitive = { primitiveType, _ ->
                when (primitiveType) {
                    KonanPrimitiveType.BOOLEAN -> listOf(LlvmAttribute.ZeroExt)
                    KonanPrimitiveType.CHAR -> listOf(LlvmAttribute.ZeroExt)
                    KonanPrimitiveType.BYTE -> listOf(LlvmAttribute.SignExt)
                    KonanPrimitiveType.SHORT -> listOf(LlvmAttribute.SignExt)
                    KonanPrimitiveType.INT -> emptyList()
                    KonanPrimitiveType.LONG -> emptyList()
                    KonanPrimitiveType.FLOAT -> emptyList()
                    KonanPrimitiveType.DOUBLE -> emptyList()
                    KonanPrimitiveType.NON_NULL_NATIVE_PTR -> emptyList()
                    KonanPrimitiveType.VECTOR128 -> emptyList()
                }
            },
            ifReference = {
                return listOf()
            },
    )
}
