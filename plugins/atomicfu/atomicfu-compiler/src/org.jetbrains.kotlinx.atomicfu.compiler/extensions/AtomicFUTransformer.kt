/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.atomicfu.compiler.extensions

import org.jetbrains.kotlin.backend.common.deepCopyWithVariables
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder.buildValueParameter
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator.*

private const val KOTLIN = "kotlin"
private const val AFU_PKG = "kotlinx.atomicfu"
private const val LOCKS = "locks"
private const val ATOMIC_CONSTRUCTOR = "atomic"
private const val ATOMICFU_VALUE_TYPE = """Atomic(Int|Long|Boolean|Ref)"""
private const val ATOMIC_ARRAY_TYPE = """Atomic(Int|Long|Boolean|)Array"""
private const val ATOMIC_ARRAY_FACTORY_FUNCTION = "atomicArrayOfNulls"
private const val ATOMICFU_RUNTIME_FUNCTION_PREDICATE = "atomicfu_"
private const val REENTRANT_LOCK_TYPE = "ReentrantLock"
private const val GETTER = "atomicfu\$getter"
private const val SETTER = "atomicfu\$setter"
private const val GET = "get"
private const val SET = "set"
private const val ATOMICFU_INLINE_FUNCTION = """atomicfu_(loop|update|getAndUpdate|updateAndGet)"""

private val ATOMICFU_VALUE_TYPE_REGEX = ATOMICFU_VALUE_TYPE.toRegex()
private val ATOMIC_ARRAY_TYPE_REGEX = ATOMIC_ARRAY_TYPE.toRegex()
private val REENTRANT_LOCK_TYPE_REGEX = REENTRANT_LOCK_TYPE.toRegex()

class AtomicFUTransformer(val context: IrPluginContext) : IrElementTransformerVoid() {

    private val irBuiltIns = context.irBuiltIns

    private val AFU_CLASSES: Map<String, IrType> = mapOf(
        "AtomicInt" to irBuiltIns.intType,
        "AtomicLong" to irBuiltIns.longType,
        "AtomicRef" to irBuiltIns.anyNType,
        "AtomicBoolean" to irBuiltIns.booleanType
    )

    private val AFU_ARRAY_CLASSES: Map<String, String> = mapOf(
        "AtomicIntArray" to "IntArray",
        "AtomicLongArray" to "LongArray",
        "AtomicBooleanArray" to "BooleanArray",
        "AtomicArray" to "Array"
    )

    override fun visitFile(declaration: IrFile): IrFile {
        val transformedDeclarations = mutableListOf<IrDeclaration>()
        declaration.declarations.forEach { irDeclaration ->
            irDeclaration.transformInlineAtomicExtension()?.let { transformedDeclarations.add(it) }
        }
        declaration.declarations.addAll(transformedDeclarations)
        return super.visitFile(declaration)
    }

    override fun visitClass(declaration: IrClass): IrStatement {
        val transformedDeclarations = mutableListOf<IrDeclaration>()
        declaration.declarations.forEach { irDeclaration ->
            irDeclaration.transformInlineAtomicExtension()?.let { transformedDeclarations.add(it) }
        }
        declaration.declarations.addAll(transformedDeclarations)
        return super.visitClass(declaration)
    }

    override fun visitProperty(declaration: IrProperty): IrStatement {
        declaration.backingField?.let { backingField ->
            backingField.initializer?.let { initializer ->
                initializer.expression.eraseAtomicConstructor(backingField)?.let { newInitializer ->
                    declaration.backingField!!.initializer = context.irFactory.createExpressionBody(newInitializer)
                }
            }
        }
        return super.visitProperty(declaration)
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        transformDeclarationBody(declaration.body, declaration)
        return super.visitFunction(declaration)
    }

    override fun visitAnonymousInitializer(declaration: IrAnonymousInitializer): IrStatement {
        transformDeclarationBody(declaration.body, declaration)
        return super.visitAnonymousInitializer(declaration)
    }

    private fun transformDeclarationBody(body: IrBody?, parent: IrDeclaration, lambda: IrFunction? = null) {
        if (body is IrBlockBody) {
            body.statements.forEachIndexed { i, stmt ->
                val transformedStmt = stmt.transformStatement(parent, lambda)
                body.statements[i] = transformedStmt
            }
        }
    }

    private fun IrExpression.eraseAtomicConstructor(parentDeclaration: IrDeclaration, lambda: IrFunction? = null) =
        when {
            type.isAtomicValueType() -> getPureTypeValue().transformAtomicFunctionCall(parentDeclaration, lambda)
            type.isAtomicArrayType() -> buildPureTypeArrayConstructor()
            type.isReentrantLockType() -> buildConstNull()
            else -> null
        }

    private fun IrDeclaration.transformInlineAtomicExtension(): IrDeclaration? {
        // transform the signature of the inline Atomic* extension declaration
        // inline fun AtomicRef<T>.foo(args) { ... } -> inline fun foo(args, getter: () -> T, setter: (T) -> Unit)
        if (this is IrFunction &&
            isInline &&
            extensionReceiverParameter != null &&
            extensionReceiverParameter!!.type.isAtomicValueType()
        ) {
            val newDeclaration = this.deepCopyWithSymbols(parent)
            val oldValueParameters = valueParameters.map { it.deepCopyWithSymbols(newDeclaration) }
            val valueParametersCount = valueParameters.size
            val receiverValueType = extensionReceiverParameter!!.type.atomicToValueType() // T
            val getterType = buildGetterType(receiverValueType)
            val setterType = buildSetterType(receiverValueType)
            newDeclaration.valueParameters = oldValueParameters + listOf(
                buildValueParameter(newDeclaration, GETTER, valueParametersCount, getterType),
                buildValueParameter(newDeclaration, SETTER, valueParametersCount + 1, setterType)
            )
            newDeclaration.extensionReceiverParameter = null
            return newDeclaration
        }
        return null
    }

    private fun IrExpression.getPureTypeValue(): IrExpression {
        require(this is IrCall && isAtomicFactory()) { "Illegal initializer for the atomic property ${this.render()}" }
        return getValueArgument(0)!!
    }

    private fun IrExpression.buildPureTypeArrayConstructor() =
        when (this) {
            is IrConstructorCall -> {
                require(isAtomicArrayConstructor()) { "Unsupported atomic array constructor ${this.render()}" }
                val arrayConstructorSymbol = type.getArrayConstructorSymbol { it.owner.valueParameters.size == 1 }
                val size = getValueArgument(0)
                IrConstructorCallImpl(
                    startOffset, endOffset,
                    arrayConstructorSymbol.owner.returnType, arrayConstructorSymbol,
                    arrayConstructorSymbol.owner.typeParameters.size, 0, 1
                ).apply {
                    putValueArgument(0, size)
                }
            }
            is IrCall -> {
                require(isAtomicArrayFactory()) { "Unsupported atomic array factory function ${this.render()}" }
                val arrayFactorySymbol = referencePackageFunction("kotlin", "arrayOfNulls")
                val arrayElementType = getTypeArgument(0)!!
                val size = getValueArgument(0)
                buildCall(
                    startOffset, endOffset,
                    target = arrayFactorySymbol,
                    type = type,
                    typeArguments = listOf(arrayElementType),
                    valueArguments = listOf(size)
                )
            }
            else -> error("Illegal type of atomic array initializer ${this.render()}")
        }

    private fun IrCall.inlineAtomicFunction(atomicType: IrType, accessors: List<IrExpression>): IrCall {
        val valueArguments = getValueArguments()
        val functionName = getAtomicFunctionName()
        val runtimeFunction = getRuntimeFunctionSymbol(functionName, atomicType)
        return buildCall(
            target = runtimeFunction,
            type = type,
            origin = IrStatementOrigin.INVOKE,
            typeArguments = if (runtimeFunction.owner.typeParameters.size == 1) listOf(atomicType) else emptyList(),
            valueArguments = valueArguments + accessors
        )
    }

    private fun IrStatement.transformStatement(parentDeclaration: IrDeclaration, lambda: IrFunction? = null) =
        when (this) {
            is IrExpression -> transformAtomicFunctionCall(parentDeclaration, lambda)
            is IrVariable -> {
                apply { initializer = initializer?.transformAtomicFunctionCall(parentDeclaration, lambda) }
            }
            else -> this
        }

    private fun IrExpression.transformAtomicFunctionCall(parentDeclaration: IrDeclaration, lambda: IrFunction? = null): IrExpression {
        // erase unchecked cast to the Atomic* type
        if (this is IrTypeOperatorCall && (operator == CAST || operator == IMPLICIT_CAST) && typeOperand.isAtomicValueType()) {
            return argument
        }
        if (isAtomicValueInitializer()) {
            return eraseAtomicConstructor(parentDeclaration, lambda)!!
        }
        when (this) {
            is IrTypeOperatorCall -> {
                return apply { argument = argument.transformAtomicFunctionCall(parentDeclaration, lambda) }
            }
            is IrStringConcatenationImpl -> {
                return apply {
                    arguments.forEachIndexed { i, arg ->
                        arguments[i] = arg.transformAtomicFunctionCall(parentDeclaration, lambda)
                    }
                }
            }
            is IrReturn -> {
                if (parentDeclaration.isTransformedAtomicExtensionFunction() && (parentDeclaration as IrFunction).isInline &&
                    returnTargetSymbol !== parentDeclaration.symbol) {
                    return IrReturnImpl(
                        startOffset,
                        endOffset,
                        type,
                        parentDeclaration.symbol,
                        value.transformAtomicFunctionCall(parentDeclaration, lambda)
                    )
                }
                return apply { value = value.transformAtomicFunctionCall(parentDeclaration, lambda) }
            }
            is IrSetValue -> {
                return apply { value = value.transformAtomicFunctionCall(parentDeclaration, lambda) }
            }
            is IrSetField -> {
                return apply { value = value.transformAtomicFunctionCall(parentDeclaration, lambda) }
            }
            is IrWhen -> {
                return apply {
                    branches.forEachIndexed { i, branch ->
                        branches[i] = branch.apply {
                            condition = condition.transformAtomicFunctionCall(parentDeclaration, lambda)
                            result = result.transformAtomicFunctionCall(parentDeclaration, lambda)
                        }
                    }
                }
            }
            is IrTry -> {
                return apply {
                    tryResult = tryResult.transformAtomicFunctionCall(parentDeclaration, lambda)
                    catches.forEach {
                        it.result = it.result.transformAtomicFunctionCall(parentDeclaration, lambda)
                    }
                    finallyExpression = finallyExpression?.transformAtomicFunctionCall(parentDeclaration, lambda)
                }
            }
            is IrBlock -> {
                return apply {
                    statements.forEachIndexed { i, stmt ->
                        statements[i] = stmt.transformStatement(parentDeclaration, lambda)
                    }
                }
            }
            is IrGetValue -> {
                if (lambda != null && symbol.owner.parent == lambda) return this
                if (symbol is IrValueParameterSymbol && parentDeclaration.isTransformedAtomicExtensionFunction()) {
                    // replace use site of the value parameter with it's copy from the transformed declaration
                    val index = (symbol.owner as IrValueParameter).index
                    if (index >= 0) { // index == -1 for `this` parameter
                        val transformedValueParameter = (parentDeclaration as IrFunction).valueParameters[index]
                        return buildGetValue(startOffset, endOffset, transformedValueParameter.symbol)
                    }
                }
            }
            is IrCall -> {
                dispatchReceiver?.let { dispatchReceiver = it.transformAtomicFunctionCall(parentDeclaration, lambda) }
                extensionReceiver?.let { extensionReceiver = it.transformAtomicFunctionCall(parentDeclaration, lambda) }
                getValueArguments().forEachIndexed { i, arg ->
                    putValueArgument(i, arg?.transformAtomicFunctionCall(parentDeclaration, lambda))
                }
                val isInline = symbol.owner.isInline
                val receiver = extensionReceiver ?: dispatchReceiver ?: return this
                // Invocation of atomic functions on atomic receivers
                // are substituted with the corresponding inline declarations from `kotlinx-atomicfu-runtime`
                if (symbol.isKotlinxAtomicfuPackage() && receiver.type.isAtomicValueType()) {
                    // 1. Receiver is the atomic field:
                    // a.incrementAndGet() -> atomicfu_incrementAndGet(get_a {..}, set_a {..})
                    if (receiver is IrCall) { // property accessor <get-field>
                        val accessors = receiver.getAccessors(lambda ?: parentDeclaration)
                        return inlineAtomicFunction(receiver.type.atomicToValueType(), accessors).apply {
                            transformAtomicfuLoopBody(parentDeclaration)
                        }
                    }
                    // 2. Atomic extension receiver `this`, the receiver of the parent function.
                    // Parent function is the inline Atomic* extension function already transformed with `transformInlineAtomicExtension`
                    // inline fun foo(getter: () -> T, setter: (T) -> Unit) { incrementAndGet() } ->
                    // inline fun foo(getter: () -> T, setter: (T) -> Unit) { atomicfu_incrementAndGet(getter, setter) }
                    if (receiver is IrGetValue && parentDeclaration.isTransformedAtomicExtensionFunction()) {
                        val accessorParameters = (parentDeclaration as IrFunction).valueParameters.takeLast(2).map { it.capture() }
                        return inlineAtomicFunction(receiver.type.atomicToValueType(), accessorParameters).apply {
                            transformAtomicfuLoopBody(parentDeclaration)
                        }
                    }
                } else {
                    // 3. transform invocation of an inline Atomic* extension function call: a.foo()
                    if (isInline && receiver is IrCall && receiver.type.isAtomicValueType()) {
                        val accessors = receiver.getAccessors(lambda ?: parentDeclaration)
                        val dispatch = dispatchReceiver
                        val args = getValueArguments()
                        val transformedTarget = symbol.owner.getDeclarationWithAccessorParameters()
                        return buildCall(
                            target = transformedTarget.symbol,
                            type = type,
                            origin = IrStatementOrigin.INVOKE,
                            valueArguments = args + accessors
                        ).apply {
                            dispatchReceiver = dispatch
                        }
                    }
                }
            }
            is IrConstructorCall -> {
                getValueArguments().forEachIndexed { i, arg ->
                    putValueArgument(i, arg?.transformAtomicFunctionCall(parentDeclaration, lambda))
                }
            }
        }
        return this
    }

    private fun IrCall.transformAtomicfuLoopBody(parent: IrDeclaration) {
        if (symbol.owner.name.asString().matches(ATOMICFU_INLINE_FUNCTION.toRegex())) {
            val lambdaLoop = (getValueArgument(0) as IrFunctionExpression).function
            transformDeclarationBody(lambdaLoop.body, parent, lambdaLoop)
        }
    }

    private fun IrFunction.hasReceiverAccessorParameters(): Boolean {
        if (valueParameters.size < 2) return false
        val params = valueParameters.takeLast(2)
        return params[0].name.asString() == GETTER && params[1].name.asString() == SETTER
    }

    private fun IrDeclaration.isTransformedAtomicExtensionFunction(): Boolean =
        this is IrFunction && this.hasReceiverAccessorParameters()

    private fun IrFunction.getDeclarationWithAccessorParameters(): IrSimpleFunction {
        val parent = parent as IrDeclarationContainer
        val params = valueParameters.map { it.type }
        val extensionType = extensionReceiverParameter?.type?.atomicToValueType()
        return try {
            parent.declarations.single {
                it is IrSimpleFunction &&
                it.name == symbol.owner.name &&
                it.valueParameters.size == params.size + 2 &&
                it.valueParameters.dropLast(2).withIndex().all { p -> p.value.type.classifierOrNull?.owner == params[p.index].classifierOrNull?.owner } &&
                it.getGetterReturnType()?.classifierOrNull?.owner == extensionType?.classifierOrNull?.owner
            } as IrSimpleFunction
        } catch (e: RuntimeException) {
            error("Exception while looking for the declaration ${this.render()} with accessor parameters: ${e.message}")
        }
    }

    private fun IrExpression.isAtomicValueInitializer() =
        (this is IrCall && (this.isAtomicFactory() || this.isAtomicArrayFactory())) ||
                (this is IrConstructorCall && this.isAtomicArrayConstructor()) ||
                type.isReentrantLockType()

    private fun IrCall.isArrayElementGetter() =
        dispatchReceiver != null &&
                dispatchReceiver!!.type.isAtomicArrayType() &&
                symbol.owner.name.asString() == GET

    private fun IrCall.getBackingField(): IrField {
        val correspondingPropertySymbol = symbol.owner.correspondingPropertySymbol!!
        return correspondingPropertySymbol.owner.backingField!!
    }

    private fun IrCall.buildAccessorLambda(
        isSetter: Boolean,
        parentDeclaration: IrDeclaration
    ): IrFunctionExpression {
        val isArrayElement = isArrayElementGetter()
        val getterCall = if (isArrayElement) dispatchReceiver as IrCall else this
        val valueType = type.atomicToValueType()
        val type = if (isSetter) buildSetterType(valueType) else buildGetterType(valueType)
        val name = if (isSetter) setterName(getterCall.symbol.owner.name.getFieldName())
            else getterName(getterCall.symbol.owner.name.getFieldName())
        val returnType = if (isSetter) context.irBuiltIns.unitType else valueType
        val accessorFunction = context.irFactory.buildFunction(
            parent = parentDeclaration as IrDeclarationParent,
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR,
            name = Name.identifier(name),
            visibility = DescriptorVisibilities.LOCAL,
            isInline = true,
            returnType = returnType
        ).apply {
            val valueParameter = buildValueParameter(this, name, 0, valueType)
            this.valueParameters = if (isSetter) listOf(valueParameter) else emptyList()
            val body = if (isSetter) {
                if (isArrayElement) {
                    val setSymbol = referenceFunction(getterCall.type.referenceClass(), SET)
                    val elementIndex = getValueArgument(0)!!.deepCopyWithVariables()
                    buildCall(
                        target = setSymbol,
                        type = context.irBuiltIns.unitType,
                        origin = IrStatementOrigin.LAMBDA,
                        valueArguments = listOf(elementIndex, valueParameter.capture())
                    ).apply {
                        dispatchReceiver = getterCall
                    }
                } else {
                    buildSetField(getterCall.getBackingField(), getterCall.dispatchReceiver, valueParameter.capture())
                }
            } else {
                val getField = buildGetField(getterCall.getBackingField(), getterCall.dispatchReceiver)
                if (isArrayElement) {
                    val getSymbol = referenceFunction(getterCall.type.referenceClass(), GET)
                    val elementIndex = getValueArgument(0)!!.deepCopyWithVariables()
                    buildCall(
                        target = getSymbol,
                        type = valueType,
                        origin = IrStatementOrigin.LAMBDA,
                        valueArguments = listOf(elementIndex)
                    ).apply {
                        dispatchReceiver = getField.deepCopyWithVariables()
                    }
                } else {
                    getField.deepCopyWithVariables()
                }
            }
            this.body = context.irFactory.buildBlockBody(listOf(body))
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        return IrFunctionExpressionImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            type,
            accessorFunction,
            IrStatementOrigin.LAMBDA
        )
    }

    private fun buildSetField(backingField: IrField, ownerClass: IrExpression?, value: IrGetValue): IrSetField {
        val receiver = if (ownerClass is IrTypeOperatorCall) ownerClass.argument as IrGetValue else ownerClass
        val fieldSymbol = backingField.symbol
        return buildSetField(
            symbol = fieldSymbol,
            receiver = receiver,
            value = value
        )
    }

    private fun buildGetField(backingField: IrField, ownerClass: IrExpression?): IrGetField {
        val receiver = if (ownerClass is IrTypeOperatorCall) ownerClass.argument as IrGetValue else ownerClass
        return buildGetField(
            symbol = backingField.symbol,
            receiver = receiver
        )
    }

    private fun buildGetterType(valueType: IrType): IrSimpleType =
        buildFunctionSimpleType(
            context.irBuiltIns.functionN(0).symbol,
            listOf(valueType)
        )

    private fun buildSetterType(valueType: IrType): IrSimpleType =
        buildFunctionSimpleType(
            context.irBuiltIns.functionN(1).symbol,
            listOf(valueType, context.irBuiltIns.unitType)
        )

    private fun IrCall.getAccessors(parentDeclaration: IrDeclaration): List<IrExpression> =
        listOf(
            buildAccessorLambda(false, parentDeclaration),
            buildAccessorLambda(true, parentDeclaration)
        )

    private fun getRuntimeFunctionSymbol(name: String, type: IrType): IrSimpleFunctionSymbol {
        val functionName = when (name) {
            "value.<get-value>" -> "getValue"
            "value.<set-value>" -> "setValue"
            else -> name
        }
        return referencePackageFunction("kotlinx.atomicfu", "$ATOMICFU_RUNTIME_FUNCTION_PREDICATE$functionName") {
            val typeArg = it.owner.getGetterReturnType()
            !(typeArg as IrType).isPrimitiveType() || typeArg == type
        }
    }

    private fun IrFunction.getGetterReturnType() = (valueParameters[valueParameters.lastIndex - 1].type as IrSimpleType).arguments.first().typeOrNull

    private fun IrCall.isAtomicFactory(): Boolean {
        val name = symbol.owner.name
        return name.asString() == ATOMIC_CONSTRUCTOR
    }

    private fun IrCall.isAtomicArrayFactory(): Boolean {
        val name = symbol.owner.name
        return name.asString() == ATOMIC_ARRAY_FACTORY_FUNCTION
    }

    private fun IrConstructorCall.isAtomicArrayConstructor() = (type as IrSimpleType).isAtomicArrayType()

    private fun IrSymbol.isKotlinxAtomicfuPackage() =
        this.isPublicApi && signature?.packageFqName()?.asString() == AFU_PKG

    private fun IrType.isAtomicValueType() = belongsTo(ATOMICFU_VALUE_TYPE_REGEX)
    private fun IrType.isAtomicArrayType() = belongsTo(ATOMIC_ARRAY_TYPE_REGEX)
    private fun IrType.isReentrantLockType() = belongsTo("$AFU_PKG.$LOCKS", REENTRANT_LOCK_TYPE_REGEX)

    private fun IrType.belongsTo(typeName: Regex) = belongsTo(AFU_PKG, typeName)

    private fun IrType.belongsTo(packageName: String, typeNameReg: Regex): Boolean {
        return classOrNull?.let {
            it.signature?.asPublic()?.let { sig ->
                sig.packageFqName == packageName && sig.declarationFqName.matches(typeNameReg)
            }
        } ?: false
    }

    private fun IrCall.getAtomicFunctionName(): String {
        val signature = symbol.signature!!
        val classFqName = if (signature is IdSignature.AccessorSignature) {
            signature.accessorSignature.declarationFqName
        } else (signature.asPublic()!!).declarationFqName
        val pattern = "$ATOMICFU_VALUE_TYPE\\.(.*)".toRegex()
        return pattern.findAll(classFqName).firstOrNull()?.let { it.groupValues[2] } ?: classFqName
    }

    private fun IrType.atomicToValueType(): IrType {
        require(isAtomicValueType())
        // todo check correctness of this way to get type name
        val classId = ((this as IrSimpleType).classifier.signature!!.asPublic())!!.declarationFqName
        if (classId == "AtomicRef") {
            return arguments.first().typeOrNull ?: error("$AFU_PKG.AtomicRef type parameter is not IrTypeProjection")
        }
        return AFU_CLASSES[classId] ?: error("IrType ${this.getClass()} does not match any of atomicfu types")
    }

    private fun buildConstNull() = IrConstImpl.constNull(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.anyNType)

    private fun IrType.getArrayConstructorSymbol(predicate: (IrConstructorSymbol) -> Boolean = { true }): IrConstructorSymbol {
        // todo check correctness of this way to get type name
        val afuClassId = ((this as IrSimpleType).classifier.signature!!.asPublic())!!.declarationFqName
        val classId = FqName("$KOTLIN.${AFU_ARRAY_CLASSES[afuClassId]!!}")
        return context.referenceConstructors(classId).single(predicate)
    }

    private fun IrType.referenceClass(): IrClassSymbol {
        val afuClassId = ((this as IrSimpleType).classifier.signature!!.asPublic())!!.declarationFqName
        val classId = FqName("$KOTLIN.${AFU_ARRAY_CLASSES[afuClassId]!!}")
        return context.referenceClass(classId)!!
    }

    private fun referencePackageFunction(
        packageName: String,
        name: String,
        predicate: (IrFunctionSymbol) -> Boolean = { true }
    ) = try {
            context.referenceFunctions(FqName("$packageName.$name")).single(predicate)
        } catch (e: RuntimeException) {
            error("Exception while looking for the function `$name` in package `$packageName`: ${e.message}")
        }

    private fun referenceFunction(classSymbol: IrClassSymbol, functionName: String): IrSimpleFunctionSymbol {
        val functionId = FqName("$KOTLIN.${classSymbol.owner.name}.$functionName")
        return try {
            context.referenceFunctions(functionId).single()
        } catch (e: RuntimeException) {
            error("Exception while looking for the function `$functionId`: ${e.message}")
        }
    }

    private class CheckIrTypeVisitor : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitExpression(expression: IrExpression) {
            checkType(expression.type, expression)
            super.visitExpression(expression)
        }

        private fun checkType(type: IrType, element: IrElement) {
            when (type) {
                is IrSimpleType -> {
                    if (!type.classifier.isBound) {
                        error("Type: ${type.render()} has unbound classifier, element.render() = ${element.render()}")
                    }
                }
            }
        }
    }

    companion object {
        fun transform(irFile: IrFile, context: IrPluginContext) {
            irFile.transform(AtomicFUTransformer(context), null)
            // this check is to ensure that all type classifier are bound after Atomicfu transformation
            // todo to be removed
            irFile.acceptChildrenVoid(CheckIrTypeVisitor())
        }
    }
}
