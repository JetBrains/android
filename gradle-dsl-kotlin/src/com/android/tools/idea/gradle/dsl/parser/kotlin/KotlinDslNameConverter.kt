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
package com.android.tools.idea.gradle.dsl.parser.kotlin

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.AUGMENTED_ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.METHOD
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.UNKNOWN
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.KOTLIN
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.ADD_AS_LIST
import com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.AUGMENT_LIST
import com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.AUGMENT_MAP
import com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.CLEAR_AND_AUGMENT_LIST
import com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER
import com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_LIST
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_MAP
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_SET
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VWO
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import java.util.regex.Pattern

interface KotlinDslNameConverter: GradleDslNameConverter {
  override fun getKind() = KOTLIN

  override fun psiToName(element: PsiElement): String {
    return when (element) {
      is KtStringTemplateExpression -> when (val contents = element.literalContents()) {
        null -> element.text
        else -> GradleNameElement.escape(contents)
      }
      is KtExpression -> gradleNameFor(element) ?: element.text
      else -> element.text
    }
  }

  override fun convertReferenceText(context: GradleDslElement, referenceText: String): String {
    var result : String? = null
    runReadAction {
      val referencePsi = KtPsiFactory(context.dslFile.project, false).createExpression(referenceText)
      result = gradleNameFor(referencePsi)
    }
    return result ?: referenceText
  }

  override fun convertReferencePsi(context: GradleDslElement, element: PsiElement): String {
    return (element as? KtExpression)?.let { gradleNameFor(element) } ?: convertReferenceText(context, element.text)
  }

  override fun convertReferenceToExternalText(context: GradleDslElement,
                                              referenceText: String,
                                              forInjection: Boolean): String {
    return when (context) {
      is GradleDslSimpleExpression -> convertToExternalTextValue(context, context.dslFile, referenceText, forInjection)
      else -> referenceText
    }
  }

  override fun convertReferenceToExternalText(context: GradleDslElement,
                                              dslElement: GradleDslElement,
                                              forInjection: Boolean): String {
    return when (context) {
      is GradleDslSimpleExpression -> convertToExternalTextValue(dslElement, context, context.dslFile, forInjection) ?: dslElement.name
      else -> dslElement.name
    }
  }

  override fun externalNameForParent(modelName: String, context: GradleDslElement): ExternalNameInfo {
    val map = context.getExternalToModelMap(this)
    val defaultResult = ExternalNameInfo(modelName, UNKNOWN)
    var result : ExternalNameInfo? = null
    for (e in map.entrySet) {
      if (e.modelEffectDescription.property.name == modelName) {
        if (e.versionConstraint?.isOkWith(this.context.agpVersion) == false) continue
        // prefer assignment if possible, or otherwise the first appropriate method we find
        when (e.modelEffectDescription.semantics) {
          VAR, VWO -> return ExternalNameInfo(e.surfaceSyntaxDescription.name, ASSIGNMENT)
          SET, ADD_AS_LIST, AUGMENT_LIST, CLEAR_AND_AUGMENT_LIST, AUGMENT_MAP, OTHER ->
            if (result == null) result = ExternalNameInfo(e.surfaceSyntaxDescription.name, METHOD)
          VAL -> when (e.modelEffectDescription.property.type) {
            MUTABLE_SET, MUTABLE_LIST, MUTABLE_MAP -> return ExternalNameInfo(e.surfaceSyntaxDescription.name, AUGMENTED_ASSIGNMENT)
            else -> Unit
          }
          else -> Unit
        }
      }
    }
    return result ?: defaultResult
  }

  override fun getPatternForUnwrappedVariables(): Pattern {
    return Pattern.compile("(([a-zA-Z0-9_]\\w*))")
  }

  override fun getPatternForWrappedVariables(): Pattern {
    return Pattern.compile("\\$\\{([^}]*)}")
  }

  override fun modelDescriptionForParent(externalName: String, context: GradleDslElement): ModelPropertyDescription? {
    val map = context.getExternalToModelMap(this)
    for (e in map.entrySet) {
      if (e.surfaceSyntaxDescription.name == externalName) return e.modelEffectDescription.property
    }
    return null
  }

  val internalContext: BuildModelContext

  override fun getContext(): BuildModelContext = internalContext
}