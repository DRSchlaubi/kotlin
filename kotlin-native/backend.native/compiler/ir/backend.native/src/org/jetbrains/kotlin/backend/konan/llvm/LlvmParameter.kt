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
import org.jetbrains.kotlin.ir.util.isSuspend

class LlvmParameter(val llvmType: LLVMTypeRef, val attributes: List<LlvmParameterAttribute> = emptyList()) {
    // TODO: Add some asserts here (e.g. check that types with bit width of <= 16 are attributed).
}

internal fun RuntimeAware.getLlvmFunctionParameterTypes(function: IrFunction): List<LlvmParameter> {
    val returnType = getLlvmFunctionReturnType(function).llvmType
    val paramTypes = ArrayList(function.allParameters.map { LlvmParameter(getLLVMType(it.type), defaultParameterAttributesForIrType(it.type)) })
    if (function.isSuspend)
        paramTypes.add(LlvmParameter(kObjHeaderPtr))                       // Suspend functions have implicit parameter of type Continuation<>.
    if (isObjectType(returnType))
        paramTypes.add(LlvmParameter(kObjHeaderPtrPtr))

    return paramTypes
}

internal fun RuntimeAware.getLlvmFunctionReturnType(function: IrFunction): LlvmParameter {
    val returnType = when {
        function is IrConstructor -> LlvmParameter(voidType)
        function.isSuspend -> LlvmParameter(kObjHeaderPtr)                // Suspend functions return Any?.
        else -> LlvmParameter(getLLVMReturnType(function.returnType), defaultParameterAttributesForIrType(function.returnType))
    }
    return returnType
}

private fun defaultParameterAttributesForIrType(irType: IrType): List<LlvmParameterAttribute> {
    return irType.unwrapToPrimitiveOrReference(
            eachInlinedClass = { _, _ -> },
            ifPrimitive = { primitiveType, _ ->
                when (primitiveType) {
                    KonanPrimitiveType.BOOLEAN -> listOf(LlvmParameterAttribute.ZeroExt)
                    KonanPrimitiveType.CHAR -> listOf(LlvmParameterAttribute.ZeroExt)
                    KonanPrimitiveType.BYTE -> listOf(LlvmParameterAttribute.SignExt)
                    KonanPrimitiveType.SHORT -> listOf(LlvmParameterAttribute.SignExt)
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
