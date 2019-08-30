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
package com.android.tools.idea.lang.databinding.validation

import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.lang.databinding.config.DbFile
import com.android.tools.idea.lang.databinding.model.ModelClassResolvable
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbId
import com.android.tools.idea.lang.databinding.psi.PsiDbInferredFormalParameterList
import com.android.tools.idea.lang.databinding.psi.PsiDbLambdaExpression
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbVisitor
import com.android.tools.idea.lang.databinding.reference.PsiMethodReference
import com.android.tools.idea.lang.databinding.reference.PsiParameterReference
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult


/**
 * This handles annotation in the data binding expressions (inside `@{}`).
 */
class DataBindingExpressionAnnotator : PsiDbVisitor(), Annotator {

  /**
   * Matches attribute types that are assignable from binding expression types and their conversions.
   */
  private class AttributeTypeMatcher(dbExprType: PsiType, facet: AndroidFacet) {
    private val bindingConversionTypes: List<PsiModelClass>

    init {
      val facade = JavaPsiFacade.getInstance(facet.module.project)
      val mode = DataBindingUtil.getDataBindingMode(facet)
      val bindingConversionAnnotation = facade.findClass(
        mode.bindingConversion,
        facet.module.getModuleWithDependenciesAndLibrariesScope(false))
      bindingConversionTypes = mutableListOf(PsiModelClass(dbExprType, mode).unwrapped)
      if (bindingConversionAnnotation != null) {
        AnnotatedElementsSearch.searchElements(
          bindingConversionAnnotation, facet.module.getModuleWithDependenciesAndLibrariesScope(false), PsiMethod::class.java)
          .forEach { annotatedMethod ->
            val parameters = annotatedMethod.parameterList.parameters
            val returnType = annotatedMethod.returnType ?: return@forEach
            if (parameters.size == 1 && parameters[0].type.isAssignableFrom(dbExprType)) {
              bindingConversionTypes.add(PsiModelClass(returnType, mode).unwrapped)
            }
          }
      }
    }

    fun matches(attributeType: PsiModelClass) = bindingConversionTypes.any { attributeType.isAssignableFrom(it) }
  }

  private var holder: AnnotationHolder? = null

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    try {
      this.holder = holder
      element.accept(this)
      matchAttributeTypeWhenAtRoot(element)
    }
    finally {
      this.holder = null
    }
  }

  private fun annotateError(element: PsiElement, error: String, vararg args: Any?) {
    holder!!.createErrorAnnotation(element, error.format(*args))
  }

  /**
   * Matches element type with its associated XML attribute when at the root of the data binding expression.
   *
   * As the attribute and its data binding expression may not be resolved properly,
   * we match their types only when both have at least one candidate type.
   */
  private fun matchAttributeTypeWhenAtRoot(rootExpression: PsiElement) {
    if (rootExpression.parent !is DbFile) {
      return
    }

    // Delegate attribute type check to visitInferredFormalParameterList and visitFunctionRefExpr.
    if (rootExpression is PsiDbLambdaExpression || rootExpression is PsiDbFunctionRefExpr) {
      return
    }

    val dbExprType = (rootExpression.reference as? ModelClassResolvable)?.resolvedType ?: return
    val attribute = rootExpression.containingFile.context?.parent as? XmlAttribute ?: return

    val androidFacet = AndroidFacet.getInstance(rootExpression) ?: return
    val attributeMatcher = AttributeTypeMatcher(dbExprType.type, androidFacet)
    val attributeTypes = attribute.references.filterIsInstance<PsiParameterReference>().map { it.resolvedType }
    if (attributeTypes.isNotEmpty() && attributeTypes.none { attributeMatcher.matches(it.unwrapped.erasure()) }) {
      val tagName = attribute.parentOfType<XmlTag>()?.references?.firstNotNullResult { it.resolve() as? PsiClass }?.name
                    ?: "View"
      annotateError(rootExpression, SETTER_NOT_FOUND, tagName, attribute.name, dbExprType.type.canonicalText)
    }
  }

  /*
   * Data binding expressions are called within the context of a parent ViewDataBinding
   * base class. These classes have a bunch of hidden API methods that users can
   * technically call, but since they are hidden (and stripped), we can't use reflection
   * to know what they are. So we whitelist those special methods here.
   * TODO: (b/135638810) Add additional methods here
   */
  private fun isViewDataBindingMethod(name: String) = name == "safeUnbox"

  private fun toNames(parameters: PsiDbInferredFormalParameterList): List<String> {
    return parameters.inferredFormalParameterList.map { it.text }
  }

  /**
   * Returns true if the name is occurred in the lambda expression as a parameter.
   *
   * As we can not resolve parameter types for lambda expression, these
   * parameters should be left unresolved without annotation.
   */
  private fun isLambdaParameter(psiElement: PsiElement, name: String): Boolean {
    var element: PsiElement? = psiElement
    while (element != null && element !is PsiDbLambdaExpression) {
      element = element.parent
    }
    if (element == null) {
      return false
    }
    val parameters = (element as PsiDbLambdaExpression).lambdaParameters.inferredFormalParameterList ?: return false
    return toNames(parameters).any { it == name }
  }

  /**
   * Annotates unresolvable [PsiDbId] with "Cannot find identifier" error.
   *
   * A [PsiDbId] is unresolvable when its container expression does not have
   * a valid reference.
   *
   * From db.bnf, we have three kinds of possible container expression:
   * [PsiDbRefExpr], [PsiDbFunctionRefExpr] and [PsiDbFunctionRefExpr]
   *
   * ```
   * fake refExpr ::= expr? '.' id
   * simpleRefExpr ::= id {extends=refExpr elementType=refExpr}
   * qualRefExpr ::= expr '.' id {extends=refExpr elementType=refExpr}
   * functionRefExpr ::= expr '::' id
   * callExpr ::= refExpr '(' expressionList? ')'
   * ```
   *
   * If the container is unresolvable because of its expr element, we will
   * not annotate its id element.
   */
  override fun visitId(id: PsiDbId) {
    super.visitId(id)

    val parent = id.parent
    // Container expression has a valid reference as [PsiDbRefExpr] or [PsiDbFunctionRefExpr].
    if (parent.reference != null) {
      return
    }

    when (parent) {
      is PsiDbRefExpr -> {
        // Container expression has a valid reference as [PsiDbCallExpr].
        if ((parent.parent as? PsiDbCallExpr)?.reference != null) {
          return
        }

        val expr = parent.expr
        // Whitelist special-case names when there's no better way to check if they resolve to references
        if (expr == null) {
          if (isViewDataBindingMethod(id.text)) {
            return
          }
          // TODO: (b/135948299) Once we can resolve lambda parameters, we don't need to whitelist their usages any more.
          if (isLambdaParameter(id, id.text)) {
            return
          }
        }
        // Don't annotate this id element because the container is unresolvable for
        // its expr element.
        else if (expr.reference == null) {
          return
        }
      }
      is PsiDbFunctionRefExpr -> {
        if (parent.expr.reference == null) {
          return
        }
      }
    }
    annotateError(id, UNRESOLVED_IDENTIFIER, id.text)
  }

  /**
   * Annotates duplicate parameters in lambda expression.
   *
   * e.g.
   * `@{(s, s) -> s.doSomething()}`
   */
  override fun visitInferredFormalParameterList(parameters: PsiDbInferredFormalParameterList) {
    super.visitInferredFormalParameterList(parameters)

    annotateIfLambdaParameterCountMismatch(parameters)
    parameters.inferredFormalParameterList.filter { parameter ->
      parameters.inferredFormalParameterList.count { it.text == parameter.text } > 1
    }.forEach {
      annotateError(it, DUPLICATE_CALLBACK_ARGUMENT, it.text)
    }
  }

  /**
   * Annotates function reference expressions if they are not matched by the attribute.
   */
  override fun visitFunctionRefExpr(psiDbFunctionRefExpr: PsiDbFunctionRefExpr) {
    super.visitFunctionRefExpr(psiDbFunctionRefExpr)

    val attribute = psiDbFunctionRefExpr.containingFile.context?.parent as? XmlAttribute ?: return
    val attributeMethods = attribute.references
      .filterIsInstance<PsiParameterReference>()
      .mapNotNull { LambdaUtil.getFunctionalInterfaceMethod(it.resolvedType.type) }
    if (attributeMethods.isEmpty()) {
      return
    }

    val dbMethods = psiDbFunctionRefExpr.references
      .filterIsInstance<PsiMethodReference>()
      .mapNotNull { it.resolve() as? PsiMethod }
    if (dbMethods.isNotEmpty() && dbMethods.none { method -> isMethodMatchingAttribute(method, attributeMethods) }) {
      val listenerClassName = attribute.references
                                .filterIsInstance<PsiParameterReference>()
                                .firstNotNullResult { it.resolvedType.type.canonicalText } ?: "Listener"
      annotateError(psiDbFunctionRefExpr, METHOD_SIGNATURE_MISMATCH, listenerClassName, attributeMethods[0].name, attribute.name)
    }
  }

  private fun isMethodMatchingAttribute(method: PsiMethod, attributeMethods: List<PsiMethod>): Boolean =
    attributeMethods.any { attributeMethod ->
      MethodSignatureUtil.areErasedParametersEqual(method.getSignature(PsiSubstitutor.EMPTY),
                                                   attributeMethod.getSignature(PsiSubstitutor.EMPTY))
    }

  private fun annotateIfLambdaParameterCountMismatch(parameters: PsiDbInferredFormalParameterList) {
    val found = parameters.inferredFormalParameterList.size
    if (found == 0) {
      return
    }

    val lambdaParameters = parameters.parent ?: return
    // The lambdaParameters should reference a functional class from [LambdaParametersReferenceProvider]
    val listenerMethod = lambdaParameters.references
                           .filterIsInstance<PsiMethodReference>()
                           .firstOrNull()
                           ?.resolve() as? PsiMethod
                         ?: return
    val expected = listenerMethod.parameterList.parameters.size
    if (found != expected) {
      annotateError(parameters, ARGUMENT_COUNT_MISMATCH, expected, found)
    }
  }

  companion object {
    const val UNRESOLVED_IDENTIFIER = "Cannot find identifier '%s'"

    const val DUPLICATE_CALLBACK_ARGUMENT = "Callback parameter '%s' is not unique"

    const val SETTER_NOT_FOUND = "Cannot find a setter for <%s %s> that accepts parameter type '%s'"

    const val ARGUMENT_COUNT_MISMATCH = "Unexpected parameter count. Expected %d, found %d."

    const val METHOD_SIGNATURE_MISMATCH = "Listener class '%s' with method '%s' did not match signature of any method '%s'"
  }
}