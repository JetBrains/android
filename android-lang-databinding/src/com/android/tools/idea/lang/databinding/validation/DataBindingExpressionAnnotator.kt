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

import com.android.SdkConstants
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.lang.databinding.config.DbFile
import com.android.tools.idea.lang.databinding.model.ModelClassResolvable
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.lang.databinding.model.toModelClassResolvable
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbId
import com.android.tools.idea.lang.databinding.psi.PsiDbInferredFormalParameterList
import com.android.tools.idea.lang.databinding.psi.PsiDbLambdaExpression
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbVisitor
import com.android.tools.idea.lang.databinding.reference.PsiFieldReference
import com.android.tools.idea.lang.databinding.reference.PsiMethodReference
import com.android.tools.idea.lang.databinding.reference.PsiParameterReference
import com.android.tools.idea.lang.databinding.reference.XmlVariableReference
import com.android.tools.idea.lang.databinding.reference.getAllGetterTypes
import com.android.tools.idea.lang.databinding.reference.getAllSetterTypes
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.utils.addIfNotNull
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
      val moduleScope = facet.getModuleSystem().getResolveScope(ScopeType.MAIN)
      val bindingConversionAnnotation = facade.findClass(mode.bindingConversion, moduleScope)
      bindingConversionTypes = mutableListOf(PsiModelClass(dbExprType, mode).unwrapped)
      if (bindingConversionAnnotation != null) {
        AnnotatedElementsSearch.searchElements(
          bindingConversionAnnotation, moduleScope, PsiMethod::class.java)
          .forEach { annotatedMethod ->
            val parameters = annotatedMethod.parameterList.parameters
            val returnType = annotatedMethod.returnType ?: return@forEach
            // Convert parameters[0] to its erasure to remove unwanted type parameter e.g. "T" in List<T> when assigned from List<String>.
            if (parameters.size == 1 && TypeConversionUtil.erasure(parameters[0].type).isAssignableFrom(dbExprType)) {
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

    // Delegate attribute type check to visitInferredFormalParameterList.
    if (rootExpression is PsiDbLambdaExpression) {
      return
    }

    if ((rootExpression.reference as? PsiMethodReference)?.kind == PsiMethodReference.Kind.METHOD_REFERENCE) {
      annotateMethodsWithUnmatchedSignatures(rootExpression)
      return
    }

    val dbExprType = (rootExpression.reference as? ModelClassResolvable)?.resolvedType ?: return
    val attribute = rootExpression.containingFile.context?.parent as? XmlAttribute ?: return

    val androidFacet = AndroidFacet.getInstance(rootExpression) ?: return
    val attributeMatcher = AttributeTypeMatcher(dbExprType.type, androidFacet)
    val attributeSetterTypes = attribute.getAllSetterTypes()
    val tagName = attribute.parentOfType<XmlTag>()?.references?.firstNotNullResult { it.resolve() as? PsiClass }?.name
                  ?: SdkConstants.VIEW_TAG
    if (attributeSetterTypes.isNotEmpty() && attributeSetterTypes.none { attributeMatcher.matches(it.unwrapped.erasure()) }) {
      annotateError(rootExpression, SETTER_NOT_FOUND, tagName, attribute.name, dbExprType.type.canonicalText)
    }

    val attributeValue = attribute.value ?: return
    if (DataBindingUtil.isTwoWayBindingExpression(attributeValue)) {
      val assignableType = findAssignableTypeToBindingExpression(rootExpression, getInvertibleMethodNames(androidFacet))
      if (assignableType == null) {
        annotateError(rootExpression, EXPRESSION_NOT_INVERTIBLE, rootExpression.text)
        return
      }

      val attributeGetterTypes = attribute.getAllGetterTypes()
      if (attributeGetterTypes.isNotEmpty() &&
          attributeGetterTypes.none { attributeType -> assignableType.erasure().unwrapped.isAssignableFrom(attributeType) }) {
        annotateError(rootExpression, GETTER_NOT_FOUND, tagName, attribute.name, dbExprType.type.canonicalText)
      }
    }
  }

  /**
   * Returns a set of method names that can be inverted.
   *
   * e.g. "convertIntToString" can be inverted if there is a method with annotation @InverseMethod("convertIntToString")
   */
  private fun getInvertibleMethodNames(facet: AndroidFacet): Set<String> {
    val facade = JavaPsiFacade.getInstance(facet.module.project)
    val mode = DataBindingUtil.getDataBindingMode(facet)
    val moduleScope = facet.getModuleSystem().getResolveScope(ScopeType.MAIN)
    val inverseMethodAnnotation = facade.findClass(mode.inverseMethod, moduleScope) ?: return setOf()
    val nameSet = mutableSetOf<String>()
    AnnotatedElementsSearch.searchElements(
      inverseMethodAnnotation, moduleScope, PsiMethod::class.java)
      .forEach { annotatedMethod ->
        nameSet.addIfNotNull(annotatedMethod.name)
        val annotation = AnnotationUtil.findAnnotation(annotatedMethod, mode.inverseMethod) ?: return@forEach
        nameSet.addIfNotNull((annotation.findAttributeValue(null) as? PsiLiteralExpression)?.value as? String)
      }
    return nameSet
  }

  /**
   * Returns the type that can be assigned to a two-way data binding expression.
   */
  private fun findAssignableTypeToBindingExpression(dbExpr: PsiElement, invertibleMethodNames: Set<String>): PsiModelClass? {
    val type = dbExpr.references.firstNotNullResult { (it as? ModelClassResolvable)?.resolvedType } ?: return null
    // Observable types can be assigned to its unwrapped directly.
    if (type.isLiveData || type.isObservableField) {
      return type.unwrapped
    }
    // Return dbExpr's resolved type when it can be resolved to setter, field or variable.
    if (dbExpr is PsiDbRefExpr) {
      val settable = dbExpr.references
        .any {
          (it as? PsiMethodReference)?.isSetterReferenceFrom(dbExpr.id.text) == true
          || it is PsiFieldReference || it is XmlVariableReference
        }
      if (settable) {
        // When dbExpr references a setter method without a resolved type, we will use the getter's instead.
        return dbExpr.toModelClassResolvable()?.resolvedType
      }
    }
    // Unwrap method with @InverseMethod annotations.
    else if (dbExpr is PsiDbCallExpr) {
      val parameters = dbExpr.expressionList?.exprList ?: return null
      val parameter = parameters.lastOrNull() ?: return null
      // Check if its only parameter has a valid type for setter.
      findAssignableTypeToBindingExpression(parameter, invertibleMethodNames) ?: return null
      // Return the returnType of the original method.
      val reference = dbExpr.references.filterIsInstance<PsiMethodReference>().firstOrNull() ?: return null
      if (invertibleMethodNames.contains((reference.resolve() as PsiMethod).name)) {
        return reference.resolvedType
      }
    }
    return null
  }

  /**
   * Annotates method references when their signatures are not matched with the attribute.
   *
   * e.g. "var.onClick" in "android:onClick=@{var.OnClick}" or "var::onClick" in "android:onClick=@{var::OnClick}".
   */
  private fun annotateMethodsWithUnmatchedSignatures(rootExpression: PsiElement) {
    val attribute = rootExpression.containingFile.context?.parent as? XmlAttribute ?: return
    val attributeMethods = attribute.references
      .filterIsInstance<PsiParameterReference>()
      .mapNotNull { LambdaUtil.getFunctionalInterfaceMethod(it.resolvedType.type) }
    if (attributeMethods.isEmpty()) {
      return
    }

    val dbMethods = rootExpression.references
      .filterIsInstance<PsiMethodReference>()
      .mapNotNull { it.resolve() as? PsiMethod }
    if (dbMethods.isNotEmpty() && dbMethods.none { method -> isMethodMatchingAttribute(method, attributeMethods) }) {
      val listenerClassName = attribute.references
                                .filterIsInstance<PsiParameterReference>()
                                .firstNotNullResult { it.resolvedType.type.canonicalText } ?: "Listener"
      annotateError(rootExpression, METHOD_SIGNATURE_MISMATCH, listenerClassName, attributeMethods[0].name, attribute.name)
    }
  }

  /*
   * Data binding expressions are called within the context of a parent ViewDataBinding
   * base class. These classes have a bunch of hidden API methods that users can
   * technically call, but since they are hidden (and stripped), we can't use reflection
   * to know what they are. So we explicitly list those special methods here.
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
        // Allow special-case names when there's no better way to check if they resolve to references
        if (expr == null) {
          if (isViewDataBindingMethod(id.text)) {
            return
          }
          // TODO: (b/135948299) Once we can resolve lambda parameters, we don't need to explicitly allow their usages any more.
          if (isLambdaParameter(id, id.text)) {
            return
          }
        }
        // Don't annotate this id element when the container is unresolvable for
        // its expr element.
        else if (expr.reference == null) {
          return
        }
        // Don't annotate this id element when the container's expr element is resolved to an array whose references are not supported yet.
        // TODO: (b/141703341) Add references to array types.
        else if (expr.toModelClassResolvable()?.resolvedType?.isArray == true) {
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

    const val GETTER_NOT_FOUND = "Cannot find a getter for <%s %s> that accepts parameter type '%s'"

    const val ARGUMENT_COUNT_MISMATCH = "Unexpected parameter count. Expected %d, found %d."

    const val METHOD_SIGNATURE_MISMATCH = "Listener class '%s' with method '%s' did not match signature of any method '%s'"

    const val EXPRESSION_NOT_INVERTIBLE = "The expression '%s' cannot be inverted, so it cannot be used in a two-way binding"
  }
}