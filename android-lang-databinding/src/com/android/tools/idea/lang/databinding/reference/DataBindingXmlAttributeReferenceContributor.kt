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
package com.android.tools.idea.lang.databinding.reference

import com.android.SdkConstants
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.lang.databinding.model.PsiModelMethod
import com.android.utils.usLocaleCapitalize
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.PsiType
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.asJava.elements.KtLightPsiArrayInitializerMemberValue
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

/**
 * For references found inside XML attributes assigned to data binding expression.
 */
class DataBindingXmlAttributeReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlAttribute::class.java), object : PsiReferenceProvider() {
      override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val attribute = element as XmlAttribute
        val attributeValue = attribute.value ?: return arrayOf()
        if (!DataBindingUtil.isBindingExpression(attributeValue)) {
          return arrayOf()
        }
        val model = AttributeReferenceModel.getInstance(attribute) ?: return arrayOf()
        val referenceList = mutableListOf<PsiReference>()

        referenceList.addAll(model.getReferencesFromBindingAdapter())
        referenceList.addAll(model.getReferencesFromBindingMethods())
        referenceList.addAll(model.getReferencesFromViewSetter())

        val attributeType = referenceList.filterIsInstance<PsiParameterReference>().firstNotNullResult { it.resolvedType }?.type
        if (attributeType != null && DataBindingUtil.isTwoWayBindingExpression(attributeValue)) {
          referenceList.addAll(model.getReferencesFromInverseBindingAdapter())
          referenceList.addAll(model.getReferencesFromInverseBindingMethods(attributeType))
        }

        return referenceList.toTypedArray()
      }
    })
  }

  private class AttributeReferenceModel private constructor(val attribute: XmlAttribute,
                                                            val facet: AndroidFacet,
                                                            facade: JavaPsiFacade,
                                                            val mode: DataBindingMode,
                                                            val viewType: PsiType) {
    companion object {
      fun getInstance(attribute: XmlAttribute): AttributeReferenceModel? {
        val facet = AndroidFacet.getInstance(attribute) ?: return null
        val facade = JavaPsiFacade.getInstance(facet.module.project)
        val mode = DataBindingUtil.getDataBindingMode(facet)
        val viewClass = attribute.parentOfType<XmlTag>()
                          ?.references
                          ?.firstNotNullResult { it.resolve() as? PsiClass }
                        ?: return null
        val viewType = viewClass.qualifiedName?.let { viewName ->
          DataBindingUtil.parsePsiType(viewName, facet, null)
        } ?: return null
        return AttributeReferenceModel(attribute, facet, facade, mode, viewType)
      }
    }

    private val bindingAdapterAnnotation = facade.findClass(
      mode.bindingAdapter,
      facet.module.getModuleWithDependenciesAndLibrariesScope(false))

    private val bindingMethodsAnnotation = facade.findClass(
      mode.bindingMethods,
      facet.module.getModuleWithDependenciesAndLibrariesScope(false))

    private val inverseBindingAdapterAnnotation = facade.findClass(
      mode.inverseBindingAdapter,
      facet.module.getModuleWithDependenciesAndLibrariesScope(false))

    private val inverseBindingMethodsAnnotation = facade.findClass(
      mode.inverseBindingMethods,
      facet.module.getModuleWithDependenciesAndLibrariesScope(false))

    fun isAttributeNameMatched(name: String): Boolean {
      // local name should be matched
      if (name.substringAfter(':') != attribute.localName) {
        return false
      }
      // prefix could be omitted for AUTO_URI namespace
      if (attribute.namespace == SdkConstants.AUTO_URI && name.indexOf(':') == -1) {
        return true
      }
      return attribute.namespacePrefix == name.substringBefore(':', "")
    }

    /**
     * Provides references from DataBindingAdapter.
     *
     * Example: `@BindingAdapter("text") void bindText(View view, String text)` that sets attribute `app:text` to String.
     */
    fun getReferencesFromBindingAdapter(): List<PsiReference> {
      if (bindingAdapterAnnotation == null) {
        return listOf()
      }
      val referenceList = mutableListOf<PsiReference>()
      AnnotatedElementsSearch.searchElements(
        bindingAdapterAnnotation, facet.module.getModuleWithDependenciesAndLibrariesScope(false), PsiMethod::class.java)
        .forEach { annotatedMethod ->
          val annotation = AnnotationUtil.findAnnotation(annotatedMethod, mode.bindingAdapter) ?: return@forEach
          val annotationValue = annotation.findAttributeValue("value") ?: return@forEach
          var attributeNameLiterals: Array<PsiElement> = annotationValue.children
          if (annotationValue is KtLightPsiArrayInitializerMemberValue) {
            attributeNameLiterals = annotationValue.getInitializers().map { it as PsiElement }.toTypedArray()
          }
          val parameters = (annotatedMethod as PsiMethod).parameterList.parameters
          if (parameters.size == attributeNameLiterals.size + 1 && parameters[0].type.isAssignableFrom(viewType)) {
            val index = attributeNameLiterals.indexOfFirst { literal ->
              isAttributeNameMatched(literal.text.trim('"'))
            }
            if (index != -1) {
              referenceList.add(PsiParameterReference(attribute, parameters[index + 1]))
              val adapterContainingClass = annotatedMethod.containingClass ?: return@forEach
              referenceList.add(PsiMethodReference(attribute, PsiModelMethod(
                PsiModelClass(PsiTypesUtil.getClassType(adapterContainingClass), mode), annotatedMethod)))
            }
          }
        }
      return referenceList
    }

    /**
     * Provides references from BindingMethods.
     *
     * Example: `@BindingMethod(type = View.class, attribute = "text", method = "setTextSpecial")` that redirects the setter of attribute
     * `app:text` to setTextSpecial(String str).
     */
    fun getReferencesFromBindingMethods(): List<PsiReference> {
      if (bindingMethodsAnnotation == null) {
        return listOf()
      }
      val referenceList = mutableListOf<PsiReference>()
      AnnotatedElementsSearch.searchElements(
        bindingMethodsAnnotation, facet.module.getModuleWithDependenciesAndLibrariesScope(false), PsiClass::class.java)
        .forEach { bindingMethodsAnnotatedElements ->
          val bindingMethodsAnnotation = AnnotationUtil.findAnnotation(bindingMethodsAnnotatedElements, mode.bindingMethods)
                                         ?: return@forEach
          // Find @BindingMethod annotations from  the "value" attribute of @BindingMethods annotation.
          val bindingMethodAnnotations = bindingMethodsAnnotation.findAttributeValue("value")?.children ?: return@forEach
          // We do not handle conflict @BindingMethod annotations within the same @BindingMethods annotation.
          bindingMethodAnnotations.firstOrNull { annotation ->
            val attributeValue = (annotation as? PsiAnnotation)?.findAttributeValue("attribute") ?: return@firstOrNull false
            val value = (attributeValue as? PsiLiteralExpression)?.value as? String ?: return@firstOrNull false
            isAttributeNameMatched(value)
          }?.let { bindingMethodAnnotation ->
            val typeValue = (bindingMethodAnnotation as PsiAnnotation).findAttributeValue("type") ?: return@forEach
            val type = (typeValue as? PsiClassObjectAccessExpression)?.operand?.type ?: return@forEach
            if (type.isAssignableFrom(viewType)) {
              val methodValue = bindingMethodAnnotation.findAttributeValue("method") as? PsiLiteralExpression ?: return@forEach
              val methodName = methodValue.value as? String ?: return@forEach
              for (method in PsiModelClass(type, mode).findMethods(methodName, false)) {
                val parameters = method.psiMethod.parameterList.parameters
                if (parameters.size == 1) {
                  referenceList.add(PsiParameterReference(attribute, parameters[0]))
                  referenceList.add(PsiMethodReference(attribute, method))
                }
              }
            }
          }
        }
      return referenceList
    }

    /**
     * Provides references from View's setter.
     *
     * Example: `setText(String s)` for attribute app:text in its containing view.
     */
    fun getReferencesFromViewSetter(): List<PsiReference> {
      if (attribute.namespace == SdkConstants.AUTO_URI) {
        val setterName = "set" + attribute.name.substringAfter(":").usLocaleCapitalize()
        for (method in PsiModelClass(viewType, mode).findMethods(setterName, false)) {
          val parameters = method.psiMethod.parameterList.parameters
          if (parameters.size == 1) {
            return listOf(PsiParameterReference(attribute, parameters[0]), PsiMethodReference(attribute, method))
          }
        }
      }
      return listOf()
    }

    /**
     * Provides references from InverseDataBindingAdapter.
     *
     * Example: `@InverseBindingAdapter(type = android.widget.TextView.class, attribute = "android:text")` that gets value from
     * attribute `android:text` in TextView.
     */
    fun getReferencesFromInverseBindingAdapter(): List<PsiReference> {
      if (inverseBindingAdapterAnnotation == null) {
        return listOf()
      }
      val referenceList = mutableListOf<PsiReference>()
      AnnotatedElementsSearch.searchElements(
        inverseBindingAdapterAnnotation, facet.module.getModuleWithDependenciesAndLibrariesScope(false), PsiMethod::class.java)
        .forEach { annotatedMethod ->
          val annotation = AnnotationUtil.findAnnotation(annotatedMethod, mode.inverseBindingAdapter) ?: return@forEach
          val annotationAttributeName = (annotation.findAttributeValue("attribute") as? PsiLiteralExpression)
                                          ?.value as? String ?: return@forEach
          if (isAttributeNameMatched(annotationAttributeName) && annotatedMethod.parameterList.parametersCount == 1) {
            val annotationViewParameter = annotatedMethod.parameterList.parameters[0]
            annotatedMethod.returnTypeElement ?: return@forEach
            if (!annotationViewParameter.type.isAssignableFrom(viewType)) {
              return@forEach
            }
            val annotationContainingClass = annotatedMethod.containingClass ?: return@forEach
            referenceList.add(PsiMethodReference(attribute, PsiModelMethod(
              PsiModelClass(PsiTypesUtil.getClassType(annotationContainingClass), mode), annotatedMethod)))
          }
        }
      return referenceList
    }

    /**
     * Provides references from InverseBindingMethods.
     *
     * Example: `@InverseBindingMethods({@InverseBindingMethod(type = android.widget.TextView.class,attribute = "android:text",
     * method = "getText"})` that redirects getter of attribute `android:text` to `getText`.
     */
    fun getReferencesFromInverseBindingMethods(attributeType: PsiType): List<PsiReference> {
      if (inverseBindingMethodsAnnotation == null) {
        return listOf()
      }
      val referenceList = mutableListOf<PsiReference>()
      AnnotatedElementsSearch.searchElements(
        inverseBindingMethodsAnnotation, facet.module.getModuleWithDependenciesAndLibrariesScope(false), PsiClass::class.java)
        .forEach { inverseBindingMethodsAnnotatedElements ->
          val inverseBindingMethodsAnnotation = AnnotationUtil.findAnnotation(inverseBindingMethodsAnnotatedElements,
                                                                              mode.inverseBindingMethods)
                                                ?: return@forEach
          // Find @InverseBindingMethod annotations from  the "value" attribute of @InverseBindingMethods annotation.
          val inverseBindingMethodAnnotations = inverseBindingMethodsAnnotation.findAttributeValue("value")?.children
                                                ?: return@forEach
          for (annotation in inverseBindingMethodAnnotations) {
            val attributeValue = (annotation as? PsiAnnotation)?.findAttributeValue("attribute") ?: continue
            val annotationAttributeName = (attributeValue as? PsiLiteralExpression)?.value as? String ?: continue
            if (!isAttributeNameMatched(annotationAttributeName)) {
              continue
            }
            val type = (annotation.findAttributeValue("type") as? PsiClassObjectAccessExpression)?.operand?.type
                       ?: continue
            if (type.isAssignableFrom(viewType)) {
              val methodAttribute = annotation.findAttributeValue("method") ?: continue
              var methodName = (methodAttribute as? PsiLiteralExpression)?.value as? String ?: continue
              // If method isn't provided, the attribute name is used to find its name, either prefixing with "is" or "get".
              if (methodName.isEmpty()) {
                val methodPrefix = if (attributeType.isAssignableFrom(PsiPrimitiveType.BOOLEAN)) "is" else "get"
                methodName = methodPrefix + attribute.name.substringAfter(":").usLocaleCapitalize()
              }
              for (method in PsiModelClass(type, mode).findMethods(methodName, false)) {
                if (method.parameterTypes.isEmpty()) {
                  referenceList.add(PsiMethodReference(attribute, method))
                  break
                }
              }
            }
          }
        }
      return referenceList
    }
  }
}