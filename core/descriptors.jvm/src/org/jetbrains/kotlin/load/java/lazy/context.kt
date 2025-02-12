/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.load.java.lazy

import org.jetbrains.kotlin.builtins.ReflectionTypes
import org.jetbrains.kotlin.descriptors.ClassOrPackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.SupertypeLoopChecker
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.load.java.*
import org.jetbrains.kotlin.load.java.JavaModuleAnnotationsProvider
import org.jetbrains.kotlin.load.java.components.JavaPropertyInitializerEvaluator
import org.jetbrains.kotlin.load.java.components.JavaResolverCache
import org.jetbrains.kotlin.load.java.components.SignaturePropagator
import org.jetbrains.kotlin.load.java.lazy.types.JavaTypeResolver
import org.jetbrains.kotlin.load.java.sources.JavaSourceElementFactory
import org.jetbrains.kotlin.load.java.structure.JavaTypeParameterListOwner
import org.jetbrains.kotlin.load.java.typeEnhancement.SignatureEnhancement
import org.jetbrains.kotlin.load.kotlin.DeserializedDescriptorResolver
import org.jetbrains.kotlin.load.kotlin.KotlinClassFinder
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.resolve.jvm.SyntheticJavaPartsProvider
import org.jetbrains.kotlin.resolve.sam.SamConversionResolver
import org.jetbrains.kotlin.serialization.deserialization.ErrorReporter
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker

class JavaResolverComponents(
    val storageManager: StorageManager,
    val finder: JavaClassFinder,
    val kotlinClassFinder: KotlinClassFinder,
    val deserializedDescriptorResolver: DeserializedDescriptorResolver,
    val signaturePropagator: SignaturePropagator,
    val errorReporter: ErrorReporter,
    val javaResolverCache: JavaResolverCache,
    val javaPropertyInitializerEvaluator: JavaPropertyInitializerEvaluator,
    val samConversionResolver: SamConversionResolver,
    val sourceElementFactory: JavaSourceElementFactory,
    val moduleClassResolver: ModuleClassResolver,
    val packagePartProvider: PackagePartProvider,
    val supertypeLoopChecker: SupertypeLoopChecker,
    val lookupTracker: LookupTracker,
    val module: ModuleDescriptor,
    val reflectionTypes: ReflectionTypes,
    val annotationTypeQualifierResolver: AnnotationTypeQualifierResolver,
    val signatureEnhancement: SignatureEnhancement,
    val javaClassesTracker: JavaClassesTracker,
    val settings: JavaResolverSettings,
    val kotlinTypeChecker: NewKotlinTypeChecker,
    val javaTypeEnhancementState: JavaTypeEnhancementState,
    val javaModuleResolver: JavaModuleAnnotationsProvider,
    val syntheticPartsProvider: SyntheticJavaPartsProvider = SyntheticJavaPartsProvider.EMPTY
) {
    fun replace(
        javaResolverCache: JavaResolverCache = this.javaResolverCache
    ) = JavaResolverComponents(
        storageManager, finder, kotlinClassFinder, deserializedDescriptorResolver,
        signaturePropagator, errorReporter, javaResolverCache,
        javaPropertyInitializerEvaluator, samConversionResolver, sourceElementFactory,
        moduleClassResolver, packagePartProvider, supertypeLoopChecker, lookupTracker, module, reflectionTypes,
        annotationTypeQualifierResolver, signatureEnhancement, javaClassesTracker, settings,
        kotlinTypeChecker, javaTypeEnhancementState, javaModuleResolver
    )
}

interface JavaResolverSettings {
    val correctNullabilityForNotNullTypeParameter: Boolean
    val typeEnhancementImprovementsInStrictMode: Boolean

    object Default : JavaResolverSettings {
        override val correctNullabilityForNotNullTypeParameter: Boolean
            get() = false

        override val typeEnhancementImprovementsInStrictMode: Boolean
            get() = false
    }

    companion object {
        fun create(
            correctNullabilityForNotNullTypeParameter: Boolean,
            typeEnhancementImprovementsInStrictMode: Boolean
        ): JavaResolverSettings =
            object : JavaResolverSettings {
                override val correctNullabilityForNotNullTypeParameter get() = correctNullabilityForNotNullTypeParameter
                override val typeEnhancementImprovementsInStrictMode get() = typeEnhancementImprovementsInStrictMode
            }
    }
}

class LazyJavaResolverContext internal constructor(
    val components: JavaResolverComponents,
    val typeParameterResolver: TypeParameterResolver,
    internal val delegateForDefaultTypeQualifiers: Lazy<JavaTypeQualifiersByElementType?>
) {
    constructor(
        components: JavaResolverComponents,
        typeParameterResolver: TypeParameterResolver,
        typeQualifiersComputation: () -> JavaTypeQualifiersByElementType?
    ) : this(components, typeParameterResolver, lazy(LazyThreadSafetyMode.NONE, typeQualifiersComputation))

    val defaultTypeQualifiers: JavaTypeQualifiersByElementType? by delegateForDefaultTypeQualifiers

    val typeResolver = JavaTypeResolver(this, typeParameterResolver)

    val storageManager: StorageManager
        get() = components.storageManager

    val module: ModuleDescriptor get() = components.module
}

fun LazyJavaResolverContext.child(
    typeParameterResolver: TypeParameterResolver
) = LazyJavaResolverContext(components, typeParameterResolver, delegateForDefaultTypeQualifiers)

fun LazyJavaResolverContext.computeNewDefaultTypeQualifiers(
    additionalAnnotations: Annotations
): JavaTypeQualifiersByElementType? =
    components.annotationTypeQualifierResolver.extractAndMergeDefaultQualifiers(defaultTypeQualifiers, additionalAnnotations)

fun LazyJavaResolverContext.replaceComponents(
    components: JavaResolverComponents
) = LazyJavaResolverContext(components, typeParameterResolver, delegateForDefaultTypeQualifiers)

private fun LazyJavaResolverContext.child(
    containingDeclaration: DeclarationDescriptor,
    typeParameterOwner: JavaTypeParameterListOwner?,
    typeParametersIndexOffset: Int = 0,
    delegateForTypeQualifiers: Lazy<JavaTypeQualifiersByElementType?>
) = LazyJavaResolverContext(
    components,
    typeParameterOwner?.let {
        LazyJavaTypeParameterResolver(this, containingDeclaration, it, typeParametersIndexOffset) }
        ?: typeParameterResolver,
    delegateForTypeQualifiers
)

fun LazyJavaResolverContext.childForMethod(
    containingDeclaration: DeclarationDescriptor,
    typeParameterOwner: JavaTypeParameterListOwner,
    typeParametersIndexOffset: Int = 0
) = child(containingDeclaration, typeParameterOwner, typeParametersIndexOffset, delegateForDefaultTypeQualifiers)

fun LazyJavaResolverContext.childForClassOrPackage(
    containingDeclaration: ClassOrPackageFragmentDescriptor,
    typeParameterOwner: JavaTypeParameterListOwner? = null,
    typeParametersIndexOffset: Int = 0
) = child(
    containingDeclaration, typeParameterOwner, typeParametersIndexOffset,
    lazy(LazyThreadSafetyMode.NONE) { computeNewDefaultTypeQualifiers(containingDeclaration.annotations) }
)

fun LazyJavaResolverContext.copyWithNewDefaultTypeQualifiers(
    additionalAnnotations: Annotations
) = when {
    additionalAnnotations.isEmpty() -> this
    else -> LazyJavaResolverContext(
        components, typeParameterResolver,
        lazy(LazyThreadSafetyMode.NONE) { computeNewDefaultTypeQualifiers(additionalAnnotations) }
    )
}
