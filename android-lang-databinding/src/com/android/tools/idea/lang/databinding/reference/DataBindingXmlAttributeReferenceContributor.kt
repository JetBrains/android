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
import com.android.tools.idea.databinding.DataBindingUtil
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
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
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
 *
 * Example: For attribute `app:text`, its type adapter could be
 * 1. custom logic provided by @BindingAdapter("text")
 * 2. setter renaming annotation provided by @BindingMethod(type = View.class, attribute = "text", method = "setTextSpecial")
 * 3. default setter method `setText(Text text)`
 *
 * For each adapter, find the parameter for the attribute from the adapter function:
 * 1. @BindingAdapter("text") void bindText(View view, String text) -> "String text"
 * 2. @BindingMethod(type = View.class, attribute = "text", method = "setTextSpecial") -> setTextSpecial(Text x) -> "Text x"
 * 3. setText(Text text) -> "Text text"
 */
class DataBindingXmlAttributeReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlAttribute::class.java), object : PsiReferenceProvider() {
      override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val attribute = element as XmlAttribute
        if (!DataBindingUtil.isBindingExpression(attribute.value ?: "")) {
          return arrayOf()
        }
        val containerClass = attribute.parentOfType<XmlTag>()?.references?.firstNotNullResult { it.resolve() as? PsiClass }
                             ?: return arrayOf()

        val facet = AndroidFacet.getInstance(element) ?: return arrayOf()
        val facade = JavaPsiFacade.getInstance(facet.module.project)
        val mode = DataBindingUtil.getDataBindingMode(facet)
        val containerType = containerClass.qualifiedName?.let { DataBindingUtil.parsePsiType(it, facet, null) } ?: return arrayOf()

        val referenceList = mutableListOf<PsiReference>()

        val attributeName = attribute.name
        // If the attribute's namespace is App, its prefix can be stripped e.g. app:onClick -> onClick.
        val backupAttributeName =
          if (attribute.namespace == SdkConstants.AUTO_URI) attributeName.substringAfter(':') else null

        // Find possible attribute types from @BindingAdapter.
        val bindingAdapterAnnotation = facade.findClass(
          mode.bindingAdapter,
          facet.module.getModuleWithDependenciesAndLibrariesScope(false))

        if (bindingAdapterAnnotation != null) {
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
              if (parameters.size == attributeNameLiterals.size + 1 && parameters[0].type.isAssignableFrom(containerType)) {
                val index = attributeNameLiterals.indexOfFirst { literal ->
                  val name = literal.text
                  // attributeNameLiterals are always surrounded by quotes, so we add them here.
                  name == "\"$attributeName\"" || (backupAttributeName != null && name == "\"$backupAttributeName\"")
                }
                if (index != -1) {
                  referenceList.add(PsiParameterReference(element, parameters[index + 1]))
                  val adapterContainingClass = annotatedMethod.containingClass ?: return@forEach
                  referenceList.add(PsiMethodReference(element, PsiModelMethod(
                    PsiModelClass(PsiTypesUtil.getClassType(adapterContainingClass), mode), annotatedMethod)))
                }
              }
            }
        }

        // Find possible attribute types from @BindingMethods.
        val bindingMethodAnnotation = facade.findClass(
          mode.bindingMethods,
          facet.module.getModuleWithDependenciesAndLibrariesScope(false))

        if (bindingMethodAnnotation != null) {
          AnnotatedElementsSearch.searchElements(
            bindingMethodAnnotation, facet.module.getModuleWithDependenciesAndLibrariesScope(false), PsiClass::class.java)
            .forEach { bindingMethodsAnnotatedElements ->
              val bindingMethodsAnnotation = AnnotationUtil.findAnnotation(bindingMethodsAnnotatedElements, mode.bindingMethods)
                                             ?: return@forEach
              // Find @BindingMethod annotations from  the "value" attribute of @BindingMethods annotation.
              val bindingMethodAnnotations = bindingMethodsAnnotation.findAttributeValue("value")?.children ?: return@forEach
              // We do not handle conflict @BindingMethod annotations within the same @BindingMethods annotation.
              bindingMethodAnnotations.firstOrNull { annotation ->
                val value = ((annotation as? PsiAnnotation)?.findAttributeValue("attribute") as? PsiLiteralExpression)?.value
                value is String && attributeName.contains(value)
              }?.let { bindingMethodAnnotation ->
                val type = ((bindingMethodAnnotation as PsiAnnotation).findAttributeValue(
                  "type") as? PsiClassObjectAccessExpression)?.operand?.type
                           ?: return@forEach
                if (type.isAssignableFrom(containerType)) {
                  val methodName = (bindingMethodAnnotation.findAttributeValue("method") as? PsiLiteralExpression)?.value as? String
                                   ?: return@forEach
                  for (method in PsiModelClass(type, mode).findMethods(methodName, false)) {
                    val parameters = method.psiMethod.parameterList.parameters
                    if (parameters.size == 1) {
                      referenceList.add(PsiParameterReference(element, parameters[0]))
                      referenceList.add(PsiMethodReference(element, method))
                    }
                  }
                }
              }
            }

          // Try to find a matching setter that shares a name with an attribute, e.g. app:text -> setText
          if (attribute.namespace == SdkConstants.AUTO_URI) {
            val setterName = "set" + attributeName.substringAfter(":").usLocaleCapitalize()
            for (method in PsiModelClass(containerType, mode).findMethods(setterName, false)) {
              val parameters = method.psiMethod.parameterList.parameters
              if (parameters.size == 1) {
                referenceList.add(PsiParameterReference(element, parameters[0]))
                referenceList.add(PsiMethodReference(element, method))
              }
            }
          }
        }
        return referenceList.toTypedArray()
      }
    })
  }
}