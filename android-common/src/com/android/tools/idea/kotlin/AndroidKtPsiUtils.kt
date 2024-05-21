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
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.annotationsByClassId
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue.KaErrorConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.getAccessorLightMethods
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
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
import org.jetbrains.kotlin.idea.caches.resolve.analyze as analyzeFe10
import org.jetbrains.kotlin.idea.util.findAnnotation as findAnnotationK1

/** Checks if the given offset is within [KtClass.getBody] of this [KtClass]. */
fun KtClass.insideBody(offset: Int): Boolean = (body as? PsiElement)?.textRange?.contains(offset) ?: false

// TODO(b/269691940): Require callers to provide their own [KtAnalysisSession], and remove this function.
@OptIn(KaAllowAnalysisOnEdt::class)
inline fun <T> KtAnalysisSession?.applyOrAnalyze(element: KtElement, block: KtAnalysisSession.() -> T): T =
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
  if (KotlinPluginModeProvider.isK2Mode()) {
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
  return if (KotlinPluginModeProvider.isK2Mode()) {
    analysisSession.applyOrAnalyze(this) {
      resolveCallOld()?.singleConstructorCallOrNull()?.symbol?.containingClassId?.asFqNameString()
    }
  } else {
    analyzeFe10(BodyResolveMode.PARTIAL).get(BindingContext.ANNOTATION, this)?.fqName?.asString()
  }
}

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
fun KtAnnotationEntry.fqNameMatches(fqNames: Set<String>, analysisSession: KtAnalysisSession? = null): Boolean {
  val shortName = shortName?.asString() ?: return false
  val fqNamesFiltered = fqNames.filter { it.endsWith(shortName) }
  if (fqNamesFiltered.isEmpty()) return false

  // Note that we intentionally defer calling `getQualifiedName(..)` as much as possible because it has a performance intensive workload
  // (analysis). It is important check early returns before calling `getQualifiedName(..)`. Previously, we used `lazy { .. }`, but
  // we dropped it to avoid "Avoid `by lazy` for simple lazy initialization [AvoidByLazy]" lint error.
  val qualifiedName = getQualifiedName(analysisSession)
  return fqNamesFiltered.any { it == qualifiedName }
}

/**
 * K2 version of [fqNameMatches]; determine if [ktAnnotationEntry] has one of a
 * set of fully qualified names [fqName].
 */
fun KtAnalysisSession.fqNameMatches(ktAnnotationEntry: KtAnnotationEntry, fqName: String): Boolean {
  val shortName = ktAnnotationEntry.shortName?.asString() ?: return false
  if (!fqName.endsWith(shortName)) return false

  // Note that we intentionally defer calling `getQualifiedName(..)` as much as possible because it has a performance intensive workload
  // (analysis). It is important check early returns before calling `getQualifiedName(..)`. Previously, we used `lazy { .. }`, but
  // we dropped it to avoid "Avoid `by lazy` for simple lazy initialization [AvoidByLazy]" lint error.
  val qualifiedName = ktAnnotationEntry.getQualifiedName(this)
  return fqName == qualifiedName
}

/** Computes the qualified name for a Kotlin Class. Returns null if the class is a kotlin built-in. */
fun KtClass.getQualifiedName(analysisSession: KtAnalysisSession? = null): String? {
  return if (KotlinPluginModeProvider.isK2Mode()) {
    analysisSession.applyOrAnalyze(this) {
      val symbol = getClassOrObjectSymbol()
      val classId = symbol?.classId ?: return null

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

inline fun <reified T> KtExpression.evaluateConstant(analysisSession: KtAnalysisSession? = null): T? =
  if (KotlinPluginModeProvider.isK2Mode()) {
    analysisSession.applyOrAnalyze(this) {
      evaluate()
        ?.takeUnless { it is KaErrorConstantValue }
        ?.value as? T
    }
  } else {
    ConstantExpressionEvaluator.getConstant(this, analyzeFe10())
      ?.takeUnless { it.isError }
      ?.getValue(TypeUtils.NO_EXPECTED_TYPE) as? T
  }

/**
 * Tries to evaluate this [KtExpression] as a constant-time constant string.
 *
 * Based on InterpolatedStringInjectorProcessor in the Kotlin plugin.
 */
fun KtExpression.tryEvaluateConstant(analysisSession: KtAnalysisSession? = null): String? =
  evaluateConstant<String>(analysisSession)

/**
 * Tries to evaluate this [KtExpression] and return its value coerced as a string.
 *
 * Similar to [tryEvaluateConstant] with the different that for non-string constants, they will be converted to string.
 */
fun KtExpression.tryEvaluateConstantAsText(analysisSession: KtAnalysisSession? = null): String? =
  evaluateConstant<Any>(analysisSession)?.toString()

/**
 * When given an element in a qualified chain expression (e.g. `activity` in `R.layout.activity`), this finds the previous element in the
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
fun KtClassOrObject.toPsiType() =
  toLightElements().filterIsInstance(PsiClass::class.java).firstOrNull()?.let { AndroidPsiUtils.toPsiType(it) }

fun KtAnnotated.hasAnnotation(classId: ClassId): Boolean =
  if (KotlinPluginModeProvider.isK2Mode()) {
    mapOnDeclarationSymbol { it.hasAnnotation(classId) } ?: (findAnnotationEntryByClassId(classId) != null)
  } else {
    findAnnotationK1(classId) != null
  }

fun KtAnnotated.findAnnotation(classId: ClassId): KtAnnotationEntry? =
  if (KotlinPluginModeProvider.isK2Mode()) {
    findAnnotationK2(classId)
  } else {
    findAnnotationK1(classId)
  }

private fun KtAnnotated.findAnnotationK2(classId: ClassId): KtAnnotationEntry? = mapOnDeclarationSymbol {
  it.annotationsByClassId(classId).singleOrNull()?.psi as? KtAnnotationEntry
} ?: findAnnotationEntryByClassId(classId)

@OptIn(KaAllowAnalysisOnEdt::class)
private inline fun <T> KtAnnotated.mapOnDeclarationSymbol(block: KtAnalysisSession.(KtDeclarationSymbol) -> T?): T? =
  allowAnalysisOnEdt {
    @OptIn(KaAllowAnalysisFromWriteAction::class) // TODO(b/310045274)
    allowAnalysisFromWriteAction {
      analyze(this) {
        val declaration = this@mapOnDeclarationSymbol as? KtDeclaration
        declaration?.getSymbol()?.let { block(it) }
      }
    }
  }

/**
 * Fallback of [mapOnDeclarationSymbol] in the case the given [KtAnnotated] is not [KtDeclaration]. One example is [KtTypeReference].
 * This function resolves [annotationEntries] and finds a symbol (a constructor symbol in the [KtTypeReference] case) whose class symbol
 * is [classId].
 */
@OptIn(KaAllowAnalysisOnEdt::class)
private inline fun KtAnnotated.findAnnotationEntryByClassId(classId: ClassId): KtAnnotationEntry? =
  allowAnalysisOnEdt {
    @OptIn(KaAllowAnalysisFromWriteAction::class) // TODO(b/310045274)
    allowAnalysisFromWriteAction {
      analyze(this) {
        annotationEntries.find { annotationEntry ->
          val annotationConstructorCall = annotationEntry.resolveCallOld()?.singleConstructorCallOrNull() ?: return null
          annotationConstructorCall.symbol.containingClassId == classId
        }
      }
    }
  }