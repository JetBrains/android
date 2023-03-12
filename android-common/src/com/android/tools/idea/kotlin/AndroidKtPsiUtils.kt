/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.kotlin

import com.android.tools.idea.AndroidPsiUtils
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotated as KtAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.annotations.annotationsByClassId
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue.KtErrorConstantValue
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.calls.KtAnnotationCall
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.getAccessorLightMethods
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.caches.resolve.analyze as analyzeFe10
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.findAnnotation as findAnnotationK1
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils


/** Checks if the given offset is within [KtClass.getBody] of this [KtClass]. */
fun KtClass.insideBody(offset: Int): Boolean = (body as? PsiElement)?.textRange?.contains(offset) ?: false

// TODO(b/269691940): Require callers to provide their own [KtAnalysisSession], and remove this function.
@OptIn(KtAllowAnalysisOnEdt::class)
private inline fun <T> KtAnalysisSession?.applyOrAnalyze(element: KtElement, block: KtAnalysisSession.() -> T): T =
  if (this != null) {
    block()
  } else {
    allowAnalysisOnEdt {
      analyze(element) {
        block()
      }
    }
  }

/** Checks if this [KtProperty] has a backing field or implements get/set on its own. */
fun KtProperty.hasBackingField(analysisSession: KtAnalysisSession? = null): Boolean {
  if (isK2Plugin()) {
    analysisSession.applyOrAnalyze(this) {
      val symbol = getVariableSymbol() as? KtPropertySymbol ?: return false
      return symbol.hasBackingField
    }
  } else {
    val propertyDescriptor = descriptor as? PropertyDescriptor ?: return false
    return analyzeFe10(BodyResolveMode.PARTIAL)[BindingContext.BACKING_FIELD_REQUIRED, propertyDescriptor] ?: false
  }
}

/**
 * Computes the qualified name of this [KtAnnotationEntry].
 * Prefer to use [fqNameMatches], which checks the short name first and thus has better performance.
 */
fun KtAnnotationEntry.getQualifiedName(analysisSession: KtAnalysisSession? = null): String? {
  return if (isK2Plugin()) {
    analysisSession.applyOrAnalyze(this) {
      typeReference?.getKtType()?.expandedClassSymbol?.classIdIfNonLocal?.asFqNameString()
    }
  } else {
    analyzeFe10(BodyResolveMode.PARTIAL).get(BindingContext.ANNOTATION, this)?.fqName?.asString()
  }
}

/**
 * K2 version of [getQualifiedName]; computes the qualified name of
 * [ktAnnotationEntry].
 * Prefer to use the (K2 version of) [fqNameMatches], which checks the short
 * name first and thus has better performance.
 */
fun KtAnalysisSession.getQualifiedName(ktAnnotationEntry: KtAnnotationEntry): String? =
  (ktAnnotationEntry.resolveCall().singleFunctionCallOrNull() as? KtAnnotationCall)?.symbol?.containingClassIdIfNonLocal?.asFqNameString()

/**
 * Determines whether this [KtAnnotationEntry] has the specified qualified name.
 * Careful: this does *not* currently take into account Kotlin type aliases (https://kotlinlang.org/docs/reference/type-aliases.html).
 *   Fortunately, type aliases are extremely uncommon for simple annotation types.
 */
fun KtAnnotationEntry.fqNameMatches(fqName: String, analysisSession: KtAnalysisSession? = null): Boolean {
  // For inspiration, see IDELightClassGenerationSupport.KtUltraLightSupportImpl.findAnnotation in the Kotlin plugin.
  val shortName = shortName?.asString() ?: return false
  return fqName.endsWith(shortName) && fqName == getQualifiedName(analysisSession)
}

/**
 * Utility method to use [KtAnnotationEntry.fqNameMatches] with a set of names.
 */
fun KtAnnotationEntry.fqNameMatches(fqName: Set<String>, analysisSession: KtAnalysisSession? = null): Boolean {
  val shortName = shortName?.asString() ?: return false
  val fqNameFiltered = fqName.filter { it.endsWith(shortName) }
  if (fqNameFiltered.isEmpty()) return false

  // Note that we intentionally defer calling `getQualifiedName(..)` as much as possible because it has a performance intensive workload
  // (analysis). It is important check early returns before calling `getQualifiedName(..)`. Previously, we used `lazy { .. }`, but
  // we dropped it to avoid "Avoid `by lazy` for simple lazy initialization [AvoidByLazy]" lint error.
  val qualifiedName = getQualifiedName(analysisSession)
  return fqNameFiltered.any { it == qualifiedName }
}

/**
 * K2 version of [fqNameMatches]; determine if [ktAnnotationEntry] has one of a
 * set of fully qualified names [fqName].
 */
fun KtAnalysisSession.fqNameMatches(ktAnnotationEntry: KtAnnotationEntry, fqName: Set<String>): Boolean {
  val shortName = ktAnnotationEntry.shortName?.asString() ?: return false
  val fqNameFiltered = fqName.filter { it.endsWith(shortName) }
  if (fqNameFiltered.isEmpty()) return false

  // Note that we intentionally defer calling `getQualifiedName(..)` as much as possible because it has a performance intensive workload
  // (analysis). It is important check early returns before calling `getQualifiedName(..)`. Previously, we used `lazy { .. }`, but
  // we dropped it to avoid "Avoid `by lazy` for simple lazy initialization [AvoidByLazy]" lint error.
  val qualifiedName = getQualifiedName(ktAnnotationEntry)
  return fqNameFiltered.any { it == qualifiedName }
}

/** Computes the qualified name for a Kotlin Class. Returns null if the class is a kotlin built-in. */
fun KtClass.getQualifiedName(analysisSession: KtAnalysisSession? = null): String? {
  return if (isK2Plugin()) {
    analysisSession.applyOrAnalyze(this) {
      val symbol = getClassOrObjectSymbol()
      val classId = symbol.classIdIfNonLocal ?: return null

      if (symbol.classKind != KtClassKind.CLASS || classId.packageFqName.startsWith(StandardNames.BUILT_INS_PACKAGE_NAME)) {
        null
      } else {
        classId.asFqNameString()
      }
    }
  } else {
    val classDescriptor = analyzeFe10(BodyResolveMode.PARTIAL).get(BindingContext.CLASS, this) ?: return null
    if (KotlinBuiltIns.isUnderKotlinPackage(classDescriptor) || classDescriptor.kind != ClassKind.CLASS) {
      null
    } else {
      classDescriptor.fqNameSafe.asString()
    }
  }
}

/**
 * Computes the qualified name of the class containing this [KtNamedFunction].
 *
 * For functions defined within a Kotlin class, returns the qualified name of that class. For top-level functions, returns the JVM name of
 * the Java facade class generated instead.
 *
 */
fun KtNamedFunction.getClassName(analysisSession: KtAnalysisSession? = null): String? =
  if (isTopLevel) {
    ((parent as? KtFile)?.findFacadeClass())?.qualifiedName
  } else {
    parentOfType<KtClass>()?.getQualifiedName(analysisSession)
  }

/**
 * Finds the [KtExpression] assigned to [annotationAttributeName] in this [KtAnnotationEntry].
 *
 * @see org.jetbrains.kotlin.psi.ValueArgument.getArgumentExpression
 */
fun KtAnnotationEntry.findArgumentExpression(annotationAttributeName: String): KtExpression? =
  findValueArgument(annotationAttributeName)?.getArgumentExpression()

/** Finds the [KtValueArgument] assigned to [annotationAttributeName] in this [KtAnnotationEntry]. */
fun KtAnnotationEntry.findValueArgument(annotationAttributeName: String): KtValueArgument? =
  valueArguments.firstOrNull { it.getArgumentName()?.asName?.asString() == annotationAttributeName } as? KtValueArgument

private fun KtExpression.tryEvaluateConstantAsAny(analysisSession: KtAnalysisSession?): Any? =
  if (isK2Plugin()) {
    analysisSession.applyOrAnalyze(this) {
      evaluate(KtConstantEvaluationMode.CONSTANT_LIKE_EXPRESSION_EVALUATION)
        ?.takeUnless { it is KtErrorConstantValue }
        ?.value
    }
  } else {
    ConstantExpressionEvaluator.getConstant(this, analyzeFe10())
      ?.takeUnless { it.isError }
      ?.getValue(TypeUtils.NO_EXPECTED_TYPE)
  }

/**
 * Tries to evaluate this [KtExpression] as a constant-time constant string.
 *
 * Based on InterpolatedStringInjectorProcessor in the Kotlin plugin.
 */
fun KtExpression.tryEvaluateConstant(analysisSession: KtAnalysisSession? = null): String? =
  tryEvaluateConstantAsAny(analysisSession) as? String

/**
 * Tries to evaluate this [KtExpression] and return it's value coerced as a string.
 *
 * Similar to [tryEvaluateConstant] with the different that for non-string constants, they will be converted to string.
 */
fun KtExpression.tryEvaluateConstantAsText(analysisSession: KtAnalysisSession? = null): String? =
  tryEvaluateConstantAsAny(analysisSession)?.toString()

/**
 * When given an element in a qualified chain expression (eg. `activity` in `R.layout.activity`), this finds the previous element in the
 * chain (in this case `layout`).
 */
fun KtExpression.getPreviousInQualifiedChain(): KtExpression? {
  val receiverExpression = getQualifiedExpressionForSelector()?.receiverExpression
  return (receiverExpression as? KtQualifiedExpression)?.selectorExpression ?: receiverExpression
}

/**
 * When given an element in a qualified chain expression (eg. `R` in `R.layout.activity`), this finds the next element in the chain (in this
 * case `layout`).
 */
fun KtExpression.getNextInQualifiedChain(): KtExpression? {
  return getQualifiedExpressionForReceiver()?.selectorExpression
         ?: getQualifiedExpressionForSelector()?.getQualifiedExpressionForReceiver()?.selectorExpression
}

fun KotlinType.getQualifiedName() = constructor.declarationDescriptor?.fqNameSafe

fun KotlinType.isSubclassOf(className: String, strict: Boolean = false): Boolean {
  return (!strict && getQualifiedName()?.asString() == className) || constructor.supertypes.any {
    it.getQualifiedName()?.asString() == className || it.isSubclassOf(className, true)
  }
}

val KtProperty.psiType: PsiType?
  get() {
    val accessors = getAccessorLightMethods()
    return accessors.backingField?.type ?: accessors.getter?.returnType
  }
val KtParameter.psiType get() = toLightElements().filterIsInstance(PsiParameter::class.java).firstOrNull()?.type
val KtFunction.psiType get() = LightClassUtil.getLightClassMethod(this)?.returnType
fun KtClass.toPsiType() = toLightElements().filterIsInstance(PsiClass::class.java).firstOrNull()?.let { AndroidPsiUtils.toPsiType(it) }
fun KtAnnotated.hasAnnotation(fqn: String) = findAnnotation(FqName(fqn)) != null

// TODO(jsjeon): Once available, use upstream util in `AnnotationModificationUtils`
@OptIn(KtAllowAnalysisOnEdt::class)
fun KtAnnotated.findAnnotation(fqName: FqName): KtAnnotationEntry? =
  if (isK2Plugin()) {
    allowAnalysisOnEdt {
      analyze(this) {
        val annotatedSymbol =
          (this@findAnnotation as? KtDeclaration)?.getSymbol() as? KtAnnotatedSymbol
        val annotations = annotatedSymbol?.annotationsByClassId(ClassId.topLevel(fqName))
        annotations?.singleOrNull()?.psi as? KtAnnotationEntry
      }
    }
  } else {
    findAnnotationK1(fqName)
  }

