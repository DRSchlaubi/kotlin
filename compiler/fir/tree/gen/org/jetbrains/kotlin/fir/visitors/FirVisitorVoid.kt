/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.visitors

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.references.FirReference
import org.jetbrains.kotlin.fir.FirLabel
import org.jetbrains.kotlin.fir.expressions.FirResolvable
import org.jetbrains.kotlin.fir.FirTargetElement
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirResolvedDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirControlFlowGraphOwner
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirAnnotatedDeclaration
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirTypedDeclaration
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRefsOwner
import org.jetbrains.kotlin.fir.declarations.FirTypeParametersOwner
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirField
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirContractDescriptionOwner
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirBackingField
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.FirPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.declarations.FirAnonymousObject
import org.jetbrains.kotlin.fir.expressions.FirAnonymousObjectExpression
import org.jetbrains.kotlin.fir.diagnostics.FirDiagnosticHolder
import org.jetbrains.kotlin.fir.declarations.FirImport
import org.jetbrains.kotlin.fir.declarations.FirResolvedImport
import org.jetbrains.kotlin.fir.declarations.FirErrorImport
import org.jetbrains.kotlin.fir.expressions.FirLoop
import org.jetbrains.kotlin.fir.expressions.FirErrorLoop
import org.jetbrains.kotlin.fir.expressions.FirDoWhileLoop
import org.jetbrains.kotlin.fir.expressions.FirWhileLoop
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirBinaryLogicExpression
import org.jetbrains.kotlin.fir.expressions.FirJump
import org.jetbrains.kotlin.fir.expressions.FirLoopJump
import org.jetbrains.kotlin.fir.expressions.FirBreakExpression
import org.jetbrains.kotlin.fir.expressions.FirContinueExpression
import org.jetbrains.kotlin.fir.expressions.FirCatch
import org.jetbrains.kotlin.fir.expressions.FirTryExpression
import org.jetbrains.kotlin.fir.expressions.FirConstExpression
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.FirStarProjection
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.expressions.FirArgumentList
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.FirComparisonExpression
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirAssignmentOperatorStatement
import org.jetbrains.kotlin.fir.expressions.FirEqualityOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.expressions.FirWhenBranch
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccess
import org.jetbrains.kotlin.fir.expressions.FirCheckNotNullCall
import org.jetbrains.kotlin.fir.expressions.FirElvisExpression
import org.jetbrains.kotlin.fir.expressions.FirArrayOfCall
import org.jetbrains.kotlin.fir.expressions.FirAugmentedArraySetCall
import org.jetbrains.kotlin.fir.expressions.FirClassReferenceExpression
import org.jetbrains.kotlin.fir.expressions.FirErrorExpression
import org.jetbrains.kotlin.fir.declarations.FirErrorFunction
import org.jetbrains.kotlin.fir.declarations.FirErrorProperty
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirImplicitInvokeCall
import org.jetbrains.kotlin.fir.expressions.FirDelegatedConstructorCall
import org.jetbrains.kotlin.fir.expressions.FirComponentCall
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirExpressionWithSmartcast
import org.jetbrains.kotlin.fir.expressions.FirExpressionWithSmartcastToNull
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.expressions.FirCheckedSafeCallSubject
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirWrappedExpression
import org.jetbrains.kotlin.fir.expressions.FirWrappedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirLambdaArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirErrorResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirResolvedReifiedParameterReference
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.expressions.FirThrowExpression
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.FirWhenSubjectExpression
import org.jetbrains.kotlin.fir.expressions.FirWrappedDelegateExpression
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.FirErrorNamedReference
import org.jetbrains.kotlin.fir.references.FirSuperReference
import org.jetbrains.kotlin.fir.references.FirThisReference
import org.jetbrains.kotlin.fir.references.FirControlFlowGraphReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.FirDelegateFieldReference
import org.jetbrains.kotlin.fir.references.FirBackingFieldReference
import org.jetbrains.kotlin.fir.references.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirErrorTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRefWithNullability
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.FirDynamicTypeRef
import org.jetbrains.kotlin.fir.types.FirFunctionTypeRef
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.contracts.FirEffectDeclaration
import org.jetbrains.kotlin.fir.contracts.FirContractDescription
import org.jetbrains.kotlin.fir.contracts.FirLegacyRawContractDescription
import org.jetbrains.kotlin.fir.contracts.FirRawContractDescription
import org.jetbrains.kotlin.fir.contracts.FirResolvedContractDescription

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

abstract class FirVisitorVoid : FirVisitor<Unit, Nothing?>() {
    abstract fun visitElement(element: FirElement)

    open fun visitAnnotationContainer(annotationContainer: FirAnnotationContainer) {
        visitElement(annotationContainer)
    }

    open fun visitTypeRef(typeRef: FirTypeRef) {
        visitAnnotationContainer(typeRef)
    }

    open fun visitReference(reference: FirReference) {
        visitElement(reference)
    }

    open fun visitLabel(label: FirLabel) {
        visitElement(label)
    }

    open fun visitResolvable(resolvable: FirResolvable) {
        visitElement(resolvable)
    }

    open fun visitTargetElement(targetElement: FirTargetElement) {
        visitElement(targetElement)
    }

    open fun visitDeclarationStatus(declarationStatus: FirDeclarationStatus) {
        visitElement(declarationStatus)
    }

    open fun visitResolvedDeclarationStatus(resolvedDeclarationStatus: FirResolvedDeclarationStatus) {
        visitDeclarationStatus(resolvedDeclarationStatus)
    }

    open fun visitControlFlowGraphOwner(controlFlowGraphOwner: FirControlFlowGraphOwner) {
        visitElement(controlFlowGraphOwner)
    }

    open fun visitStatement(statement: FirStatement) {
        visitAnnotationContainer(statement)
    }

    open fun visitExpression(expression: FirExpression) {
        visitStatement(expression)
    }

    open fun visitDeclaration(declaration: FirDeclaration) {
        visitElement(declaration)
    }

    open fun visitAnnotatedDeclaration(annotatedDeclaration: FirAnnotatedDeclaration) {
        visitDeclaration(annotatedDeclaration)
    }

    open fun visitAnonymousInitializer(anonymousInitializer: FirAnonymousInitializer) {
        visitDeclaration(anonymousInitializer)
    }

    open fun visitTypedDeclaration(typedDeclaration: FirTypedDeclaration) {
        visitAnnotatedDeclaration(typedDeclaration)
    }

    open fun visitTypeParameterRefsOwner(typeParameterRefsOwner: FirTypeParameterRefsOwner) {
        visitElement(typeParameterRefsOwner)
    }

    open fun visitTypeParametersOwner(typeParametersOwner: FirTypeParametersOwner) {
        visitTypeParameterRefsOwner(typeParametersOwner)
    }

    open fun visitMemberDeclaration(memberDeclaration: FirMemberDeclaration) {
        visitTypeParameterRefsOwner(memberDeclaration)
    }

    open fun visitCallableDeclaration(callableDeclaration: FirCallableDeclaration) {
        visitTypedDeclaration(callableDeclaration)
    }

    open fun visitTypeParameterRef(typeParameterRef: FirTypeParameterRef) {
        visitElement(typeParameterRef)
    }

    open fun visitTypeParameter(typeParameter: FirTypeParameter) {
        visitTypeParameterRef(typeParameter)
    }

    open fun visitVariable(variable: FirVariable) {
        visitCallableDeclaration(variable)
    }

    open fun visitValueParameter(valueParameter: FirValueParameter) {
        visitVariable(valueParameter)
    }

    open fun visitProperty(property: FirProperty) {
        visitVariable(property)
    }

    open fun visitField(field: FirField) {
        visitVariable(field)
    }

    open fun visitEnumEntry(enumEntry: FirEnumEntry) {
        visitVariable(enumEntry)
    }

    open fun visitClassLikeDeclaration(classLikeDeclaration: FirClassLikeDeclaration) {
        visitAnnotatedDeclaration(classLikeDeclaration)
    }

    open fun visitClass(klass: FirClass) {
        visitClassLikeDeclaration(klass)
    }

    open fun visitRegularClass(regularClass: FirRegularClass) {
        visitClass(regularClass)
    }

    open fun visitTypeAlias(typeAlias: FirTypeAlias) {
        visitClassLikeDeclaration(typeAlias)
    }

    open fun visitFunction(function: FirFunction) {
        visitCallableDeclaration(function)
    }

    open fun visitContractDescriptionOwner(contractDescriptionOwner: FirContractDescriptionOwner) {
        visitElement(contractDescriptionOwner)
    }

    open fun visitSimpleFunction(simpleFunction: FirSimpleFunction) {
        visitFunction(simpleFunction)
    }

    open fun visitPropertyAccessor(propertyAccessor: FirPropertyAccessor) {
        visitFunction(propertyAccessor)
    }

    open fun visitBackingField(backingField: FirBackingField) {
        visitVariable(backingField)
    }

    open fun visitConstructor(constructor: FirConstructor) {
        visitFunction(constructor)
    }

    open fun visitFile(file: FirFile) {
        visitAnnotatedDeclaration(file)
    }

    open fun visitPackageDirective(packageDirective: FirPackageDirective) {
        visitElement(packageDirective)
    }

    open fun visitAnonymousFunction(anonymousFunction: FirAnonymousFunction) {
        visitFunction(anonymousFunction)
    }

    open fun visitAnonymousFunctionExpression(anonymousFunctionExpression: FirAnonymousFunctionExpression) {
        visitExpression(anonymousFunctionExpression)
    }

    open fun visitAnonymousObject(anonymousObject: FirAnonymousObject) {
        visitClass(anonymousObject)
    }

    open fun visitAnonymousObjectExpression(anonymousObjectExpression: FirAnonymousObjectExpression) {
        visitExpression(anonymousObjectExpression)
    }

    open fun visitDiagnosticHolder(diagnosticHolder: FirDiagnosticHolder) {
        visitElement(diagnosticHolder)
    }

    open fun visitImport(import: FirImport) {
        visitElement(import)
    }

    open fun visitResolvedImport(resolvedImport: FirResolvedImport) {
        visitImport(resolvedImport)
    }

    open fun visitErrorImport(errorImport: FirErrorImport) {
        visitImport(errorImport)
    }

    open fun visitLoop(loop: FirLoop) {
        visitStatement(loop)
    }

    open fun visitErrorLoop(errorLoop: FirErrorLoop) {
        visitLoop(errorLoop)
    }

    open fun visitDoWhileLoop(doWhileLoop: FirDoWhileLoop) {
        visitLoop(doWhileLoop)
    }

    open fun visitWhileLoop(whileLoop: FirWhileLoop) {
        visitLoop(whileLoop)
    }

    open fun visitBlock(block: FirBlock) {
        visitExpression(block)
    }

    open fun visitBinaryLogicExpression(binaryLogicExpression: FirBinaryLogicExpression) {
        visitExpression(binaryLogicExpression)
    }

    open fun <E : FirTargetElement> visitJump(jump: FirJump<E>) {
        visitExpression(jump)
    }

    open fun visitLoopJump(loopJump: FirLoopJump) {
        visitJump(loopJump)
    }

    open fun visitBreakExpression(breakExpression: FirBreakExpression) {
        visitLoopJump(breakExpression)
    }

    open fun visitContinueExpression(continueExpression: FirContinueExpression) {
        visitLoopJump(continueExpression)
    }

    open fun visitCatch(catch: FirCatch) {
        visitElement(catch)
    }

    open fun visitTryExpression(tryExpression: FirTryExpression) {
        visitExpression(tryExpression)
    }

    open fun <T> visitConstExpression(constExpression: FirConstExpression<T>) {
        visitExpression(constExpression)
    }

    open fun visitTypeProjection(typeProjection: FirTypeProjection) {
        visitElement(typeProjection)
    }

    open fun visitStarProjection(starProjection: FirStarProjection) {
        visitTypeProjection(starProjection)
    }

    open fun visitTypeProjectionWithVariance(typeProjectionWithVariance: FirTypeProjectionWithVariance) {
        visitTypeProjection(typeProjectionWithVariance)
    }

    open fun visitArgumentList(argumentList: FirArgumentList) {
        visitElement(argumentList)
    }

    open fun visitCall(call: FirCall) {
        visitStatement(call)
    }

    open fun visitAnnotation(annotation: FirAnnotation) {
        visitExpression(annotation)
    }

    open fun visitAnnotationCall(annotationCall: FirAnnotationCall) {
        visitAnnotation(annotationCall)
    }

    open fun visitAnnotationArgumentMapping(annotationArgumentMapping: FirAnnotationArgumentMapping) {
        visitElement(annotationArgumentMapping)
    }

    open fun visitComparisonExpression(comparisonExpression: FirComparisonExpression) {
        visitExpression(comparisonExpression)
    }

    open fun visitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall) {
        visitExpression(typeOperatorCall)
    }

    open fun visitAssignmentOperatorStatement(assignmentOperatorStatement: FirAssignmentOperatorStatement) {
        visitStatement(assignmentOperatorStatement)
    }

    open fun visitEqualityOperatorCall(equalityOperatorCall: FirEqualityOperatorCall) {
        visitExpression(equalityOperatorCall)
    }

    open fun visitWhenExpression(whenExpression: FirWhenExpression) {
        visitExpression(whenExpression)
    }

    open fun visitWhenBranch(whenBranch: FirWhenBranch) {
        visitElement(whenBranch)
    }

    open fun visitQualifiedAccess(qualifiedAccess: FirQualifiedAccess) {
        visitResolvable(qualifiedAccess)
    }

    open fun visitCheckNotNullCall(checkNotNullCall: FirCheckNotNullCall) {
        visitExpression(checkNotNullCall)
    }

    open fun visitElvisExpression(elvisExpression: FirElvisExpression) {
        visitExpression(elvisExpression)
    }

    open fun visitArrayOfCall(arrayOfCall: FirArrayOfCall) {
        visitExpression(arrayOfCall)
    }

    open fun visitAugmentedArraySetCall(augmentedArraySetCall: FirAugmentedArraySetCall) {
        visitStatement(augmentedArraySetCall)
    }

    open fun visitClassReferenceExpression(classReferenceExpression: FirClassReferenceExpression) {
        visitExpression(classReferenceExpression)
    }

    open fun visitErrorExpression(errorExpression: FirErrorExpression) {
        visitExpression(errorExpression)
    }

    open fun visitErrorFunction(errorFunction: FirErrorFunction) {
        visitFunction(errorFunction)
    }

    open fun visitErrorProperty(errorProperty: FirErrorProperty) {
        visitVariable(errorProperty)
    }

    open fun visitQualifiedAccessExpression(qualifiedAccessExpression: FirQualifiedAccessExpression) {
        visitExpression(qualifiedAccessExpression)
    }

    open fun visitPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression) {
        visitQualifiedAccessExpression(propertyAccessExpression)
    }

    open fun visitFunctionCall(functionCall: FirFunctionCall) {
        visitQualifiedAccessExpression(functionCall)
    }

    open fun visitImplicitInvokeCall(implicitInvokeCall: FirImplicitInvokeCall) {
        visitFunctionCall(implicitInvokeCall)
    }

    open fun visitDelegatedConstructorCall(delegatedConstructorCall: FirDelegatedConstructorCall) {
        visitResolvable(delegatedConstructorCall)
    }

    open fun visitComponentCall(componentCall: FirComponentCall) {
        visitFunctionCall(componentCall)
    }

    open fun visitCallableReferenceAccess(callableReferenceAccess: FirCallableReferenceAccess) {
        visitQualifiedAccessExpression(callableReferenceAccess)
    }

    open fun visitThisReceiverExpression(thisReceiverExpression: FirThisReceiverExpression) {
        visitQualifiedAccessExpression(thisReceiverExpression)
    }

    open fun visitExpressionWithSmartcast(expressionWithSmartcast: FirExpressionWithSmartcast) {
        visitQualifiedAccessExpression(expressionWithSmartcast)
    }

    open fun visitExpressionWithSmartcastToNull(expressionWithSmartcastToNull: FirExpressionWithSmartcastToNull) {
        visitExpressionWithSmartcast(expressionWithSmartcastToNull)
    }

    open fun visitSafeCallExpression(safeCallExpression: FirSafeCallExpression) {
        visitExpression(safeCallExpression)
    }

    open fun visitCheckedSafeCallSubject(checkedSafeCallSubject: FirCheckedSafeCallSubject) {
        visitExpression(checkedSafeCallSubject)
    }

    open fun visitGetClassCall(getClassCall: FirGetClassCall) {
        visitExpression(getClassCall)
    }

    open fun visitWrappedExpression(wrappedExpression: FirWrappedExpression) {
        visitExpression(wrappedExpression)
    }

    open fun visitWrappedArgumentExpression(wrappedArgumentExpression: FirWrappedArgumentExpression) {
        visitWrappedExpression(wrappedArgumentExpression)
    }

    open fun visitLambdaArgumentExpression(lambdaArgumentExpression: FirLambdaArgumentExpression) {
        visitWrappedArgumentExpression(lambdaArgumentExpression)
    }

    open fun visitSpreadArgumentExpression(spreadArgumentExpression: FirSpreadArgumentExpression) {
        visitWrappedArgumentExpression(spreadArgumentExpression)
    }

    open fun visitNamedArgumentExpression(namedArgumentExpression: FirNamedArgumentExpression) {
        visitWrappedArgumentExpression(namedArgumentExpression)
    }

    open fun visitVarargArgumentsExpression(varargArgumentsExpression: FirVarargArgumentsExpression) {
        visitExpression(varargArgumentsExpression)
    }

    open fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier) {
        visitExpression(resolvedQualifier)
    }

    open fun visitErrorResolvedQualifier(errorResolvedQualifier: FirErrorResolvedQualifier) {
        visitResolvedQualifier(errorResolvedQualifier)
    }

    open fun visitResolvedReifiedParameterReference(resolvedReifiedParameterReference: FirResolvedReifiedParameterReference) {
        visitExpression(resolvedReifiedParameterReference)
    }

    open fun visitReturnExpression(returnExpression: FirReturnExpression) {
        visitJump(returnExpression)
    }

    open fun visitStringConcatenationCall(stringConcatenationCall: FirStringConcatenationCall) {
        visitCall(stringConcatenationCall)
    }

    open fun visitThrowExpression(throwExpression: FirThrowExpression) {
        visitExpression(throwExpression)
    }

    open fun visitVariableAssignment(variableAssignment: FirVariableAssignment) {
        visitQualifiedAccess(variableAssignment)
    }

    open fun visitWhenSubjectExpression(whenSubjectExpression: FirWhenSubjectExpression) {
        visitExpression(whenSubjectExpression)
    }

    open fun visitWrappedDelegateExpression(wrappedDelegateExpression: FirWrappedDelegateExpression) {
        visitWrappedExpression(wrappedDelegateExpression)
    }

    open fun visitNamedReference(namedReference: FirNamedReference) {
        visitReference(namedReference)
    }

    open fun visitErrorNamedReference(errorNamedReference: FirErrorNamedReference) {
        visitNamedReference(errorNamedReference)
    }

    open fun visitSuperReference(superReference: FirSuperReference) {
        visitReference(superReference)
    }

    open fun visitThisReference(thisReference: FirThisReference) {
        visitReference(thisReference)
    }

    open fun visitControlFlowGraphReference(controlFlowGraphReference: FirControlFlowGraphReference) {
        visitReference(controlFlowGraphReference)
    }

    open fun visitResolvedNamedReference(resolvedNamedReference: FirResolvedNamedReference) {
        visitNamedReference(resolvedNamedReference)
    }

    open fun visitDelegateFieldReference(delegateFieldReference: FirDelegateFieldReference) {
        visitResolvedNamedReference(delegateFieldReference)
    }

    open fun visitBackingFieldReference(backingFieldReference: FirBackingFieldReference) {
        visitResolvedNamedReference(backingFieldReference)
    }

    open fun visitResolvedCallableReference(resolvedCallableReference: FirResolvedCallableReference) {
        visitResolvedNamedReference(resolvedCallableReference)
    }

    open fun visitResolvedTypeRef(resolvedTypeRef: FirResolvedTypeRef) {
        visitTypeRef(resolvedTypeRef)
    }

    open fun visitErrorTypeRef(errorTypeRef: FirErrorTypeRef) {
        visitResolvedTypeRef(errorTypeRef)
    }

    open fun visitTypeRefWithNullability(typeRefWithNullability: FirTypeRefWithNullability) {
        visitTypeRef(typeRefWithNullability)
    }

    open fun visitUserTypeRef(userTypeRef: FirUserTypeRef) {
        visitTypeRefWithNullability(userTypeRef)
    }

    open fun visitDynamicTypeRef(dynamicTypeRef: FirDynamicTypeRef) {
        visitTypeRefWithNullability(dynamicTypeRef)
    }

    open fun visitFunctionTypeRef(functionTypeRef: FirFunctionTypeRef) {
        visitTypeRefWithNullability(functionTypeRef)
    }

    open fun visitImplicitTypeRef(implicitTypeRef: FirImplicitTypeRef) {
        visitTypeRef(implicitTypeRef)
    }

    open fun visitEffectDeclaration(effectDeclaration: FirEffectDeclaration) {
        visitElement(effectDeclaration)
    }

    open fun visitContractDescription(contractDescription: FirContractDescription) {
        visitElement(contractDescription)
    }

    open fun visitLegacyRawContractDescription(legacyRawContractDescription: FirLegacyRawContractDescription) {
        visitContractDescription(legacyRawContractDescription)
    }

    open fun visitRawContractDescription(rawContractDescription: FirRawContractDescription) {
        visitContractDescription(rawContractDescription)
    }

    open fun visitResolvedContractDescription(resolvedContractDescription: FirResolvedContractDescription) {
        visitContractDescription(resolvedContractDescription)
    }

    final override fun visitElement(element: FirElement, data: Nothing?) {
        visitElement(element)
    }

    final override fun visitAnnotationContainer(annotationContainer: FirAnnotationContainer, data: Nothing?) {
        visitAnnotationContainer(annotationContainer)
    }

    final override fun visitTypeRef(typeRef: FirTypeRef, data: Nothing?) {
        visitTypeRef(typeRef)
    }

    final override fun visitReference(reference: FirReference, data: Nothing?) {
        visitReference(reference)
    }

    final override fun visitLabel(label: FirLabel, data: Nothing?) {
        visitLabel(label)
    }

    final override fun visitResolvable(resolvable: FirResolvable, data: Nothing?) {
        visitResolvable(resolvable)
    }

    final override fun visitTargetElement(targetElement: FirTargetElement, data: Nothing?) {
        visitTargetElement(targetElement)
    }

    final override fun visitDeclarationStatus(declarationStatus: FirDeclarationStatus, data: Nothing?) {
        visitDeclarationStatus(declarationStatus)
    }

    final override fun visitResolvedDeclarationStatus(resolvedDeclarationStatus: FirResolvedDeclarationStatus, data: Nothing?) {
        visitResolvedDeclarationStatus(resolvedDeclarationStatus)
    }

    final override fun visitControlFlowGraphOwner(controlFlowGraphOwner: FirControlFlowGraphOwner, data: Nothing?) {
        visitControlFlowGraphOwner(controlFlowGraphOwner)
    }

    final override fun visitStatement(statement: FirStatement, data: Nothing?) {
        visitStatement(statement)
    }

    final override fun visitExpression(expression: FirExpression, data: Nothing?) {
        visitExpression(expression)
    }

    final override fun visitDeclaration(declaration: FirDeclaration, data: Nothing?) {
        visitDeclaration(declaration)
    }

    final override fun visitAnnotatedDeclaration(annotatedDeclaration: FirAnnotatedDeclaration, data: Nothing?) {
        visitAnnotatedDeclaration(annotatedDeclaration)
    }

    final override fun visitAnonymousInitializer(anonymousInitializer: FirAnonymousInitializer, data: Nothing?) {
        visitAnonymousInitializer(anonymousInitializer)
    }

    final override fun visitTypedDeclaration(typedDeclaration: FirTypedDeclaration, data: Nothing?) {
        visitTypedDeclaration(typedDeclaration)
    }

    final override fun visitTypeParameterRefsOwner(typeParameterRefsOwner: FirTypeParameterRefsOwner, data: Nothing?) {
        visitTypeParameterRefsOwner(typeParameterRefsOwner)
    }

    final override fun visitTypeParametersOwner(typeParametersOwner: FirTypeParametersOwner, data: Nothing?) {
        visitTypeParametersOwner(typeParametersOwner)
    }

    final override fun visitMemberDeclaration(memberDeclaration: FirMemberDeclaration, data: Nothing?) {
        visitMemberDeclaration(memberDeclaration)
    }

    final override fun visitCallableDeclaration(callableDeclaration: FirCallableDeclaration, data: Nothing?) {
        visitCallableDeclaration(callableDeclaration)
    }

    final override fun visitTypeParameterRef(typeParameterRef: FirTypeParameterRef, data: Nothing?) {
        visitTypeParameterRef(typeParameterRef)
    }

    final override fun visitTypeParameter(typeParameter: FirTypeParameter, data: Nothing?) {
        visitTypeParameter(typeParameter)
    }

    final override fun visitVariable(variable: FirVariable, data: Nothing?) {
        visitVariable(variable)
    }

    final override fun visitValueParameter(valueParameter: FirValueParameter, data: Nothing?) {
        visitValueParameter(valueParameter)
    }

    final override fun visitProperty(property: FirProperty, data: Nothing?) {
        visitProperty(property)
    }

    final override fun visitField(field: FirField, data: Nothing?) {
        visitField(field)
    }

    final override fun visitEnumEntry(enumEntry: FirEnumEntry, data: Nothing?) {
        visitEnumEntry(enumEntry)
    }

    final override fun visitClassLikeDeclaration(classLikeDeclaration: FirClassLikeDeclaration, data: Nothing?) {
        visitClassLikeDeclaration(classLikeDeclaration)
    }

    final override fun visitClass(klass: FirClass, data: Nothing?) {
        visitClass(klass)
    }

    final override fun visitRegularClass(regularClass: FirRegularClass, data: Nothing?) {
        visitRegularClass(regularClass)
    }

    final override fun visitTypeAlias(typeAlias: FirTypeAlias, data: Nothing?) {
        visitTypeAlias(typeAlias)
    }

    final override fun visitFunction(function: FirFunction, data: Nothing?) {
        visitFunction(function)
    }

    final override fun visitContractDescriptionOwner(contractDescriptionOwner: FirContractDescriptionOwner, data: Nothing?) {
        visitContractDescriptionOwner(contractDescriptionOwner)
    }

    final override fun visitSimpleFunction(simpleFunction: FirSimpleFunction, data: Nothing?) {
        visitSimpleFunction(simpleFunction)
    }

    final override fun visitPropertyAccessor(propertyAccessor: FirPropertyAccessor, data: Nothing?) {
        visitPropertyAccessor(propertyAccessor)
    }

    final override fun visitBackingField(backingField: FirBackingField, data: Nothing?) {
        visitBackingField(backingField)
    }

    final override fun visitConstructor(constructor: FirConstructor, data: Nothing?) {
        visitConstructor(constructor)
    }

    final override fun visitFile(file: FirFile, data: Nothing?) {
        visitFile(file)
    }

    final override fun visitPackageDirective(packageDirective: FirPackageDirective, data: Nothing?) {
        visitPackageDirective(packageDirective)
    }

    final override fun visitAnonymousFunction(anonymousFunction: FirAnonymousFunction, data: Nothing?) {
        visitAnonymousFunction(anonymousFunction)
    }

    final override fun visitAnonymousFunctionExpression(anonymousFunctionExpression: FirAnonymousFunctionExpression, data: Nothing?) {
        visitAnonymousFunctionExpression(anonymousFunctionExpression)
    }

    final override fun visitAnonymousObject(anonymousObject: FirAnonymousObject, data: Nothing?) {
        visitAnonymousObject(anonymousObject)
    }

    final override fun visitAnonymousObjectExpression(anonymousObjectExpression: FirAnonymousObjectExpression, data: Nothing?) {
        visitAnonymousObjectExpression(anonymousObjectExpression)
    }

    final override fun visitDiagnosticHolder(diagnosticHolder: FirDiagnosticHolder, data: Nothing?) {
        visitDiagnosticHolder(diagnosticHolder)
    }

    final override fun visitImport(import: FirImport, data: Nothing?) {
        visitImport(import)
    }

    final override fun visitResolvedImport(resolvedImport: FirResolvedImport, data: Nothing?) {
        visitResolvedImport(resolvedImport)
    }

    final override fun visitErrorImport(errorImport: FirErrorImport, data: Nothing?) {
        visitErrorImport(errorImport)
    }

    final override fun visitLoop(loop: FirLoop, data: Nothing?) {
        visitLoop(loop)
    }

    final override fun visitErrorLoop(errorLoop: FirErrorLoop, data: Nothing?) {
        visitErrorLoop(errorLoop)
    }

    final override fun visitDoWhileLoop(doWhileLoop: FirDoWhileLoop, data: Nothing?) {
        visitDoWhileLoop(doWhileLoop)
    }

    final override fun visitWhileLoop(whileLoop: FirWhileLoop, data: Nothing?) {
        visitWhileLoop(whileLoop)
    }

    final override fun visitBlock(block: FirBlock, data: Nothing?) {
        visitBlock(block)
    }

    final override fun visitBinaryLogicExpression(binaryLogicExpression: FirBinaryLogicExpression, data: Nothing?) {
        visitBinaryLogicExpression(binaryLogicExpression)
    }

    final override fun <E : FirTargetElement> visitJump(jump: FirJump<E>, data: Nothing?) {
        visitJump(jump)
    }

    final override fun visitLoopJump(loopJump: FirLoopJump, data: Nothing?) {
        visitLoopJump(loopJump)
    }

    final override fun visitBreakExpression(breakExpression: FirBreakExpression, data: Nothing?) {
        visitBreakExpression(breakExpression)
    }

    final override fun visitContinueExpression(continueExpression: FirContinueExpression, data: Nothing?) {
        visitContinueExpression(continueExpression)
    }

    final override fun visitCatch(catch: FirCatch, data: Nothing?) {
        visitCatch(catch)
    }

    final override fun visitTryExpression(tryExpression: FirTryExpression, data: Nothing?) {
        visitTryExpression(tryExpression)
    }

    final override fun <T> visitConstExpression(constExpression: FirConstExpression<T>, data: Nothing?) {
        visitConstExpression(constExpression)
    }

    final override fun visitTypeProjection(typeProjection: FirTypeProjection, data: Nothing?) {
        visitTypeProjection(typeProjection)
    }

    final override fun visitStarProjection(starProjection: FirStarProjection, data: Nothing?) {
        visitStarProjection(starProjection)
    }

    final override fun visitTypeProjectionWithVariance(typeProjectionWithVariance: FirTypeProjectionWithVariance, data: Nothing?) {
        visitTypeProjectionWithVariance(typeProjectionWithVariance)
    }

    final override fun visitArgumentList(argumentList: FirArgumentList, data: Nothing?) {
        visitArgumentList(argumentList)
    }

    final override fun visitCall(call: FirCall, data: Nothing?) {
        visitCall(call)
    }

    final override fun visitAnnotation(annotation: FirAnnotation, data: Nothing?) {
        visitAnnotation(annotation)
    }

    final override fun visitAnnotationCall(annotationCall: FirAnnotationCall, data: Nothing?) {
        visitAnnotationCall(annotationCall)
    }

    final override fun visitAnnotationArgumentMapping(annotationArgumentMapping: FirAnnotationArgumentMapping, data: Nothing?) {
        visitAnnotationArgumentMapping(annotationArgumentMapping)
    }

    final override fun visitComparisonExpression(comparisonExpression: FirComparisonExpression, data: Nothing?) {
        visitComparisonExpression(comparisonExpression)
    }

    final override fun visitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall, data: Nothing?) {
        visitTypeOperatorCall(typeOperatorCall)
    }

    final override fun visitAssignmentOperatorStatement(assignmentOperatorStatement: FirAssignmentOperatorStatement, data: Nothing?) {
        visitAssignmentOperatorStatement(assignmentOperatorStatement)
    }

    final override fun visitEqualityOperatorCall(equalityOperatorCall: FirEqualityOperatorCall, data: Nothing?) {
        visitEqualityOperatorCall(equalityOperatorCall)
    }

    final override fun visitWhenExpression(whenExpression: FirWhenExpression, data: Nothing?) {
        visitWhenExpression(whenExpression)
    }

    final override fun visitWhenBranch(whenBranch: FirWhenBranch, data: Nothing?) {
        visitWhenBranch(whenBranch)
    }

    final override fun visitQualifiedAccess(qualifiedAccess: FirQualifiedAccess, data: Nothing?) {
        visitQualifiedAccess(qualifiedAccess)
    }

    final override fun visitCheckNotNullCall(checkNotNullCall: FirCheckNotNullCall, data: Nothing?) {
        visitCheckNotNullCall(checkNotNullCall)
    }

    final override fun visitElvisExpression(elvisExpression: FirElvisExpression, data: Nothing?) {
        visitElvisExpression(elvisExpression)
    }

    final override fun visitArrayOfCall(arrayOfCall: FirArrayOfCall, data: Nothing?) {
        visitArrayOfCall(arrayOfCall)
    }

    final override fun visitAugmentedArraySetCall(augmentedArraySetCall: FirAugmentedArraySetCall, data: Nothing?) {
        visitAugmentedArraySetCall(augmentedArraySetCall)
    }

    final override fun visitClassReferenceExpression(classReferenceExpression: FirClassReferenceExpression, data: Nothing?) {
        visitClassReferenceExpression(classReferenceExpression)
    }

    final override fun visitErrorExpression(errorExpression: FirErrorExpression, data: Nothing?) {
        visitErrorExpression(errorExpression)
    }

    final override fun visitErrorFunction(errorFunction: FirErrorFunction, data: Nothing?) {
        visitErrorFunction(errorFunction)
    }

    final override fun visitErrorProperty(errorProperty: FirErrorProperty, data: Nothing?) {
        visitErrorProperty(errorProperty)
    }

    final override fun visitQualifiedAccessExpression(qualifiedAccessExpression: FirQualifiedAccessExpression, data: Nothing?) {
        visitQualifiedAccessExpression(qualifiedAccessExpression)
    }

    final override fun visitPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression, data: Nothing?) {
        visitPropertyAccessExpression(propertyAccessExpression)
    }

    final override fun visitFunctionCall(functionCall: FirFunctionCall, data: Nothing?) {
        visitFunctionCall(functionCall)
    }

    final override fun visitImplicitInvokeCall(implicitInvokeCall: FirImplicitInvokeCall, data: Nothing?) {
        visitImplicitInvokeCall(implicitInvokeCall)
    }

    final override fun visitDelegatedConstructorCall(delegatedConstructorCall: FirDelegatedConstructorCall, data: Nothing?) {
        visitDelegatedConstructorCall(delegatedConstructorCall)
    }

    final override fun visitComponentCall(componentCall: FirComponentCall, data: Nothing?) {
        visitComponentCall(componentCall)
    }

    final override fun visitCallableReferenceAccess(callableReferenceAccess: FirCallableReferenceAccess, data: Nothing?) {
        visitCallableReferenceAccess(callableReferenceAccess)
    }

    final override fun visitThisReceiverExpression(thisReceiverExpression: FirThisReceiverExpression, data: Nothing?) {
        visitThisReceiverExpression(thisReceiverExpression)
    }

    final override fun visitExpressionWithSmartcast(expressionWithSmartcast: FirExpressionWithSmartcast, data: Nothing?) {
        visitExpressionWithSmartcast(expressionWithSmartcast)
    }

    final override fun visitExpressionWithSmartcastToNull(expressionWithSmartcastToNull: FirExpressionWithSmartcastToNull, data: Nothing?) {
        visitExpressionWithSmartcastToNull(expressionWithSmartcastToNull)
    }

    final override fun visitSafeCallExpression(safeCallExpression: FirSafeCallExpression, data: Nothing?) {
        visitSafeCallExpression(safeCallExpression)
    }

    final override fun visitCheckedSafeCallSubject(checkedSafeCallSubject: FirCheckedSafeCallSubject, data: Nothing?) {
        visitCheckedSafeCallSubject(checkedSafeCallSubject)
    }

    final override fun visitGetClassCall(getClassCall: FirGetClassCall, data: Nothing?) {
        visitGetClassCall(getClassCall)
    }

    final override fun visitWrappedExpression(wrappedExpression: FirWrappedExpression, data: Nothing?) {
        visitWrappedExpression(wrappedExpression)
    }

    final override fun visitWrappedArgumentExpression(wrappedArgumentExpression: FirWrappedArgumentExpression, data: Nothing?) {
        visitWrappedArgumentExpression(wrappedArgumentExpression)
    }

    final override fun visitLambdaArgumentExpression(lambdaArgumentExpression: FirLambdaArgumentExpression, data: Nothing?) {
        visitLambdaArgumentExpression(lambdaArgumentExpression)
    }

    final override fun visitSpreadArgumentExpression(spreadArgumentExpression: FirSpreadArgumentExpression, data: Nothing?) {
        visitSpreadArgumentExpression(spreadArgumentExpression)
    }

    final override fun visitNamedArgumentExpression(namedArgumentExpression: FirNamedArgumentExpression, data: Nothing?) {
        visitNamedArgumentExpression(namedArgumentExpression)
    }

    final override fun visitVarargArgumentsExpression(varargArgumentsExpression: FirVarargArgumentsExpression, data: Nothing?) {
        visitVarargArgumentsExpression(varargArgumentsExpression)
    }

    final override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier, data: Nothing?) {
        visitResolvedQualifier(resolvedQualifier)
    }

    final override fun visitErrorResolvedQualifier(errorResolvedQualifier: FirErrorResolvedQualifier, data: Nothing?) {
        visitErrorResolvedQualifier(errorResolvedQualifier)
    }

    final override fun visitResolvedReifiedParameterReference(resolvedReifiedParameterReference: FirResolvedReifiedParameterReference, data: Nothing?) {
        visitResolvedReifiedParameterReference(resolvedReifiedParameterReference)
    }

    final override fun visitReturnExpression(returnExpression: FirReturnExpression, data: Nothing?) {
        visitReturnExpression(returnExpression)
    }

    final override fun visitStringConcatenationCall(stringConcatenationCall: FirStringConcatenationCall, data: Nothing?) {
        visitStringConcatenationCall(stringConcatenationCall)
    }

    final override fun visitThrowExpression(throwExpression: FirThrowExpression, data: Nothing?) {
        visitThrowExpression(throwExpression)
    }

    final override fun visitVariableAssignment(variableAssignment: FirVariableAssignment, data: Nothing?) {
        visitVariableAssignment(variableAssignment)
    }

    final override fun visitWhenSubjectExpression(whenSubjectExpression: FirWhenSubjectExpression, data: Nothing?) {
        visitWhenSubjectExpression(whenSubjectExpression)
    }

    final override fun visitWrappedDelegateExpression(wrappedDelegateExpression: FirWrappedDelegateExpression, data: Nothing?) {
        visitWrappedDelegateExpression(wrappedDelegateExpression)
    }

    final override fun visitNamedReference(namedReference: FirNamedReference, data: Nothing?) {
        visitNamedReference(namedReference)
    }

    final override fun visitErrorNamedReference(errorNamedReference: FirErrorNamedReference, data: Nothing?) {
        visitErrorNamedReference(errorNamedReference)
    }

    final override fun visitSuperReference(superReference: FirSuperReference, data: Nothing?) {
        visitSuperReference(superReference)
    }

    final override fun visitThisReference(thisReference: FirThisReference, data: Nothing?) {
        visitThisReference(thisReference)
    }

    final override fun visitControlFlowGraphReference(controlFlowGraphReference: FirControlFlowGraphReference, data: Nothing?) {
        visitControlFlowGraphReference(controlFlowGraphReference)
    }

    final override fun visitResolvedNamedReference(resolvedNamedReference: FirResolvedNamedReference, data: Nothing?) {
        visitResolvedNamedReference(resolvedNamedReference)
    }

    final override fun visitDelegateFieldReference(delegateFieldReference: FirDelegateFieldReference, data: Nothing?) {
        visitDelegateFieldReference(delegateFieldReference)
    }

    final override fun visitBackingFieldReference(backingFieldReference: FirBackingFieldReference, data: Nothing?) {
        visitBackingFieldReference(backingFieldReference)
    }

    final override fun visitResolvedCallableReference(resolvedCallableReference: FirResolvedCallableReference, data: Nothing?) {
        visitResolvedCallableReference(resolvedCallableReference)
    }

    final override fun visitResolvedTypeRef(resolvedTypeRef: FirResolvedTypeRef, data: Nothing?) {
        visitResolvedTypeRef(resolvedTypeRef)
    }

    final override fun visitErrorTypeRef(errorTypeRef: FirErrorTypeRef, data: Nothing?) {
        visitErrorTypeRef(errorTypeRef)
    }

    final override fun visitTypeRefWithNullability(typeRefWithNullability: FirTypeRefWithNullability, data: Nothing?) {
        visitTypeRefWithNullability(typeRefWithNullability)
    }

    final override fun visitUserTypeRef(userTypeRef: FirUserTypeRef, data: Nothing?) {
        visitUserTypeRef(userTypeRef)
    }

    final override fun visitDynamicTypeRef(dynamicTypeRef: FirDynamicTypeRef, data: Nothing?) {
        visitDynamicTypeRef(dynamicTypeRef)
    }

    final override fun visitFunctionTypeRef(functionTypeRef: FirFunctionTypeRef, data: Nothing?) {
        visitFunctionTypeRef(functionTypeRef)
    }

    final override fun visitImplicitTypeRef(implicitTypeRef: FirImplicitTypeRef, data: Nothing?) {
        visitImplicitTypeRef(implicitTypeRef)
    }

    final override fun visitEffectDeclaration(effectDeclaration: FirEffectDeclaration, data: Nothing?) {
        visitEffectDeclaration(effectDeclaration)
    }

    final override fun visitContractDescription(contractDescription: FirContractDescription, data: Nothing?) {
        visitContractDescription(contractDescription)
    }

    final override fun visitLegacyRawContractDescription(legacyRawContractDescription: FirLegacyRawContractDescription, data: Nothing?) {
        visitLegacyRawContractDescription(legacyRawContractDescription)
    }

    final override fun visitRawContractDescription(rawContractDescription: FirRawContractDescription, data: Nothing?) {
        visitRawContractDescription(rawContractDescription)
    }

    final override fun visitResolvedContractDescription(resolvedContractDescription: FirResolvedContractDescription, data: Nothing?) {
        visitResolvedContractDescription(resolvedContractDescription)
    }

}
