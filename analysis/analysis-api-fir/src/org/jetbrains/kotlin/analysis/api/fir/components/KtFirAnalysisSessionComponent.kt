/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.components

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.analysis.api.KtStarProjectionTypeArgument
import org.jetbrains.kotlin.analysis.api.KtTypeArgument
import org.jetbrains.kotlin.analysis.api.KtTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.KtFirAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KT_DIAGNOSTIC_CONVERTER
import org.jetbrains.kotlin.analysis.api.fir.types.KtFirType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.fir.analysis.diagnostics.KtPsiDiagnostic
import org.jetbrains.kotlin.fir.analysis.diagnostics.toFirDiagnostics
import org.jetbrains.kotlin.fir.diagnostics.ConeDiagnostic
import org.jetbrains.kotlin.fir.typeContext
import org.jetbrains.kotlin.fir.types.ConeInferenceContext
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.types.TypeCheckerState
import org.jetbrains.kotlin.types.model.convertVariance

internal interface KtFirAnalysisSessionComponent {
    val analysisSession: KtFirAnalysisSession

    val rootModuleSession: FirSession get() = analysisSession.firResolveState.rootModuleSession
    val typeContext: ConeInferenceContext get() = rootModuleSession.typeContext
    val firSymbolBuilder get() = analysisSession.firSymbolBuilder
    val firResolveState get() = analysisSession.firResolveState

    fun ConeKotlinType.asKtType() = analysisSession.firSymbolBuilder.typeBuilder.buildKtType(this)

    fun KtPsiDiagnostic.asKtDiagnostic(): KtDiagnosticWithPsi<*> =
        KT_DIAGNOSTIC_CONVERTER.convert(analysisSession, this as KtDiagnostic)

    fun ConeDiagnostic.asKtDiagnostic(
        source: KtSourceElement,
        qualifiedAccessSource: KtSourceElement?,
        diagnosticCache: MutableList<KtDiagnostic>
    ): KtDiagnosticWithPsi<*>? {
        val firDiagnostic = toFirDiagnostics(source, qualifiedAccessSource).firstOrNull() ?: return null
        diagnosticCache += firDiagnostic
        check(firDiagnostic is KtPsiDiagnostic)
        return firDiagnostic.asKtDiagnostic()
    }

    val KtType.coneType: ConeKotlinType
        get() {
            require(this is KtFirType)
            return coneType
        }

    val KtTypeArgument.coneTypeProjection: ConeTypeProjection
        get() = when (this) {
            is KtStarProjectionTypeArgument -> ConeStarProjection
            is KtTypeArgumentWithVariance -> {
                typeContext.createTypeArgument(type.coneType, variance.convertVariance()) as ConeTypeProjection
            }
        }

    fun createTypeCheckerContext(): TypeCheckerState {
        // TODO use correct session here,
        return analysisSession.firResolveState.rootModuleSession.typeContext.newTypeCheckerState(errorTypesEqualToAnything = true, stubTypesEqualToAnything = true)
    }
}
