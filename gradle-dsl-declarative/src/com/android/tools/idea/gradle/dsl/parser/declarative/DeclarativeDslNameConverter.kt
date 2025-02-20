/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.declarative

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeLiteral
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePair
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.AUGMENTED_ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.METHOD
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.UNKNOWN
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_LIST
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_MAP
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_SET
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.GRADLE_PROPERTY
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR
import com.intellij.psi.PsiElement

interface DeclarativeDslNameConverter : GradleDslNameConverter {

  override fun getKind() = GradleDslNameConverter.Kind.DECLARATIVE

  override fun psiToName(element: PsiElement): String {
    val text = when (element) {
      is DeclarativeLiteral -> element.value.toString()
      is DeclarativePair -> element.first.toString()
      else -> element.text
    }
    return GradleNameElement.escape(text)
  }

  override fun convertReferenceText(context: GradleDslElement, referenceText: String): String {
    return referenceText
  }

  override fun externalNameForPropertiesParent(modelName: String, context: GradlePropertiesDslElement): String {
    val descriptions = context.getChildPropertiesElementsDescriptionMap(kind)
    val instance = descriptions.toList().find { (_, v) -> v.name == modelName }
    return instance?.first ?: modelName
  }

  override fun externalNameForParent(modelName: String, context: GradleDslElement): ExternalNameInfo {
    val map = context.getExternalToModelMap(this)
    val result = ExternalNameInfo(modelName, UNKNOWN)
    for (e in map.entrySet) {
      if (e.modelEffectDescription.property.name == modelName) {

        if (e.versionConstraint?.isOkWith(this.context.agpVersion) == false) continue
        when (e.modelEffectDescription.semantics) {
          SET -> return ExternalNameInfo(e.surfaceSyntaxDescription.name, METHOD)
          VAR, GRADLE_PROPERTY -> return ExternalNameInfo(e.surfaceSyntaxDescription.name, ASSIGNMENT)
          VAL -> when (e.modelEffectDescription.property.type) {
            MUTABLE_SET, MUTABLE_LIST, MUTABLE_MAP -> return ExternalNameInfo(e.surfaceSyntaxDescription.name, AUGMENTED_ASSIGNMENT)
            else -> Unit
          }
          else -> Unit
        }
      }
    }
    return result
  }

}