/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lang

import com.android.tools.idea.lint.common.findAnnotation
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.or
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationParameterList
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiCall
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.containingClass

private const val intDefAnnotationName = "androidx.annotation.IntDef"
private val intDefAnnotationFqName = FqName(intDefAnnotationName)
private const val valuesAttrName = "value"

/**
 * Adds named constants from @IntDef annotation for a code completion for a [KtValueArgument].
 *
 * See also [IntDef documentation](https://developer.android.com/reference/androidx/annotation/IntDef)
 */
class IntDefCompletionContributorKotlin : CompletionContributor() {

  private val intDefValuesCompletionProvider = object : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val argumentToComplete = parameters.position.parentOfType<KtValueArgument>() ?: return
      val intDefValues = argumentToComplete.getIntDefValues() ?: return
      val lookupElements = intDefValues.map { LookupElementBuilder.create(it) }
      result.addAllElements(lookupElements)
    }
  }

  /**
   * Returns @IntDef values for [KtValueArgument] as Strings, if there is no @IntDef annotation for given argument returns null.
   *
   * Returns values for the first encountered @IntDef annotation.
   */
  private fun KtValueArgument.getIntDefValues(): List<String>? {
    val argument = this.takeIf { it !is KtLambdaArgument } ?: return null

    val callExpression = argument.parentOfType<KtCallElement>() ?: return null
    val calleeElement = if (callExpression is KtAnnotationEntry) {
      callExpression.calleeExpression?.constructorReferenceExpression?.resolve()?.navigationElement
    }
    else {
      callExpression.calleeExpression?.mainReference?.resolve()?.navigationElement
    }

    val argumentIndex = (argument.parent as KtValueArgumentList).arguments.indexOf(argument)
    val argumentName = argument.getArgumentName()?.asName?.asString()
    return getIntDefValues(calleeElement, argumentIndex, argumentName)
  }

  init {
    extend(CompletionType.BASIC, psiElement().inside(psiElement(KtValueArgument::class.java)), intDefValuesCompletionProvider)
  }
}


/**
 * Adds named constants from @IntDef annotation for a code completion for a [PsiReferenceExpression](argument).
 *
 * See also [IntDef documentation](https://developer.android.com/reference/androidx/annotation/IntDef)
 */
class IntDefCompletionContributorJava : CompletionContributor() {

  private val intDefValuesCompletionProvider = object : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val argumentToComplete = parameters.position.parentOfType<PsiReferenceExpression>() ?: return
      val intDefValues = argumentToComplete.getIntDefValues() ?: return
      val lookupElements = intDefValues.map { LookupElementBuilder.create(it) }
      result.addAllElements(lookupElements)
    }
  }

  /**
   * Returns @IntDef values for [PsiReferenceExpression](argument) as Strings, if there is no @IntDef annotation for given argument returns null.
   *
   * Returns values for the first encountered @IntDef annotation.
   */
  private fun PsiReferenceExpression.getIntDefValues(): List<String>? {
    val call = parentOfType<PsiCall>() ?: parentOfType<PsiAnnotation>()
    when (call) {
      is PsiCall -> {
        val calleeElement = call.resolveMethod() ?: return null
        val argumentIndex = call.argumentList?.expressions?.indexOf(this) ?: return null
        return getIntDefValues(calleeElement.navigationElement, argumentIndex, null)
      }
      is PsiAnnotation -> {
        var calleeElement = call.resolveAnnotationType()?.navigationElement
        if (calleeElement is KtClass) {
          calleeElement = calleeElement.primaryConstructor
        }
        if (calleeElement == null) return null

        val argumentName = (this.parent as? PsiNameValuePair)?.name
        return getIntDefValues(calleeElement.navigationElement, -1, argumentName)
      }
    }
    return null
  }

  init {
    extend(CompletionType.BASIC,
           psiElement().withParent(
             psiElement(PsiReferenceExpression::class.java).inside(
               or(
                 psiElement(PsiExpressionList::class.java).withParent(PsiCall::class.java),
                 psiElement(PsiNameValuePair::class.java).withParent(psiElement(PsiAnnotationParameterList::class.java))
               )
             )
           ),
           intDefValuesCompletionProvider
    )
  }
}

/**
 * Returns @IntDef values for an argument with given [argumentName] or [argumentIndex] within [calleeElement].
 *
 * Returns values for the first encountered @IntDef annotation.
 */
private fun getIntDefValues(calleeElement: PsiElement?, argumentIndex: Int, argumentName: String?): List<String>? {
  when (calleeElement) {
    is PsiClass -> {
      val funcName = argumentName ?: "value"
      val function = calleeElement.findMethodsByName(funcName, false).firstOrNull()
      return function?.annotations?.firstNotNullOfOrNull { it.intDefValues }
    }
    is PsiMethod -> {
      val parameter = (calleeElement.parameters.getOrNull(argumentIndex) as? PsiParameter)?.takeIf { it.type == PsiType.INT }
      return parameter?.annotations?.firstNotNullOfOrNull { it.intDefValues }
    }
    is KtFunction -> {
      val parameter = if (argumentName != null) {
        calleeElement.valueParameters.find { it.name == argumentName }
      }
      else {
        calleeElement.valueParameters.getOrNull(argumentIndex)
      }
      return parameter?.annotationEntries?.firstNotNullOfOrNull { it.intDefValues }
    }
  }
  return null
}

/**
 * Returns values as strings of attribute "value" of [intDefAnnotation].
 */
private fun valuesFromPsiAnnotation(intDefAnnotation: PsiAnnotation): List<String> {
  val values = intDefAnnotation.findDeclaredAttributeValue(valuesAttrName) as? PsiArrayInitializerMemberValue ?: return emptyList()
  val resultsPsi = values.initializers.map { (it as PsiReferenceExpression).resolve()!! }
  return resultsPsi.mapNotNull { (it as PsiNamedElement).name }
}

/**
 * Returns values as strings of attribute "value" of [intDefAnnotation].
 */
private fun valuesFromKtAnnotationEntry(intDefAnnotation: KtAnnotationEntry): List<String> {
  val values = intDefAnnotation.valueArguments.firstOrNull { it.getArgumentName()?.asName?.asString() == valuesAttrName }?.getArgumentExpression()
  val ktNamedReferences = (values as? KtCollectionLiteralExpression)?.getInnerExpressions() ?: return emptyList()
  return ktNamedReferences.mapNotNull { it.text }
}

/**
 * Returns @IntDef values of an annotation class given [KtAnnotationEntry] resolves to, if there is no @IntDef annotation returns null.
 */
private val KtAnnotationEntry.intDefValues: List<String>?
  get() {
    val annotationElement = calleeExpression?.constructorReferenceExpression?.resolve() ?: return null

    return CachedValuesManager.getCachedValue(annotationElement) {
      var source = annotationElement.navigationElement
      if (source is KtPrimaryConstructor) {
        source = source.containingClass()
      }
      CachedValueProvider.Result(source?.let { intDefValuesFromAnnotationClass(it) }, annotationElement)
    }
  }

/**
 * Returns @IntDef values of an annotation class given [PsiAnnotation] resolves to, if there is no @IntDef annotation returns null.
 */
private val PsiAnnotation.intDefValues: List<String>?
  get() {
    val annotationElement = resolveAnnotationType() ?: return null

    return CachedValuesManager.getCachedValue(annotationElement) {
      CachedValueProvider.Result(intDefValuesFromAnnotationClass(annotationElement.navigationElement), annotationElement)
    }
  }

/**
 * Returns @IntDef values for a given [annotationClass], if there is no @IntDef annotation returns null.
 */
private fun intDefValuesFromAnnotationClass(annotationClass: PsiElement?): List<String>? {
  return when (annotationClass) {
    is PsiClass -> annotationClass.annotations.find { it.qualifiedName == intDefAnnotationName }?.let { valuesFromPsiAnnotation(it) }
    is KtClass -> annotationClass.findAnnotation(intDefAnnotationFqName)?.let { valuesFromKtAnnotationEntry(it) }
    else -> null
  }
}