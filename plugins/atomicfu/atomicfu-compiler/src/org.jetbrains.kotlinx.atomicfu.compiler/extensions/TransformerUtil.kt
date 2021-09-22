/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.atomicfu.compiler.extensions

import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

internal fun IrFactory.buildFunction(
    parent: IrDeclarationParent,
    origin: IrDeclarationOrigin,
    name: Name,
    visibility: DescriptorVisibility,
    isInline: Boolean,
    returnType: IrType
): IrSimpleFunction =
    buildFun {
        startOffset = UNDEFINED_OFFSET
        endOffset = UNDEFINED_OFFSET
        this.origin = origin
        this.name = name
        this.visibility = visibility
        this.isInline = isInline
        this.returnType = returnType
    }.apply {
        this.parent = parent
    }

internal fun buildCall(
    startOffset: Int = UNDEFINED_OFFSET,
    endOffset: Int = UNDEFINED_OFFSET,
    target: IrSimpleFunctionSymbol,
    type: IrType? = null,
    origin: IrStatementOrigin? = null,
    typeArguments: List<IrType> = emptyList(),
    valueArguments: List<IrExpression?> = emptyList()
): IrCall =
    IrCallImpl(
        startOffset,
        endOffset,
        type ?: target.owner.returnType,
        target,
        typeArguments.size,
        valueArguments.size,
        origin
    ).apply {
        typeArguments.let {
            it.withIndex().forEach { (i, t) -> putTypeArgument(i, t) }
        }
        valueArguments.let {
            it.withIndex().forEach { (i, arg) -> putValueArgument(i, arg) }
        }
    }

internal fun IrFactory.buildBlockBody(statements: List<IrStatement>) =
    createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, statements)

internal fun buildSetField(
    symbol: IrFieldSymbol,
    receiver: IrExpression?,
    value: IrExpression,
    superQualifierSymbol: IrClassSymbol? = null
) =
    IrSetFieldImpl(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        symbol,
        receiver,
        value,
        value.type,
        IrStatementOrigin.GET_PROPERTY,
        superQualifierSymbol
    )

internal fun buildGetField(symbol: IrFieldSymbol, receiver: IrExpression?, superQualifierSymbol: IrClassSymbol? = null, type: IrType? = null) =
    IrGetFieldImpl(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        symbol,
        type ?: symbol.owner.type,
        receiver,
        IrStatementOrigin.GET_PROPERTY,
        superQualifierSymbol
    )

internal fun buildFunctionSimpleType(symbol: IrClassifierSymbol, typeParameters: List<IrType>): IrSimpleType =
    IrSimpleTypeImpl(
        classifier = symbol,
        hasQuestionMark = false,
        arguments = typeParameters.map { makeTypeProjection(it, Variance.INVARIANT) },
        annotations = emptyList()
    )

internal fun buildGetValue(startOffset: Int = UNDEFINED_OFFSET, endOffset: Int = UNDEFINED_OFFSET, symbol: IrValueSymbol) =
    IrGetValueImpl(
        startOffset,
        endOffset,
        symbol.owner.type,
        symbol
    )

internal fun getterName(name: String) = "<get-$name>"
internal fun setterName(name: String) = "<set-$name>"
internal fun Name.getFieldName() = "<get-(\\w+)>".toRegex().find(asString())?.groupValues?.get(1)
    ?: error("Getter name ${this.asString()} does not match special name pattern <get-fieldName>")

internal fun IrFunctionAccessExpression.getValueArguments() = (0 until valueArgumentsCount).map { i ->
    getValueArgument(i)
}

internal fun IrValueParameter.capture() = buildGetValue(UNDEFINED_OFFSET, UNDEFINED_OFFSET, symbol)