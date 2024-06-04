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
package com.android.tools.idea.gradle.declarative.dsl.parser.declarative

import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.intellij.psi.PsiElement
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription

interface DeclarativeDslNameConverter : GradleDslNameConverter {

  override fun getKind() = GradleDslNameConverter.Kind.DECLARATIVE

  override fun psiToName(element: PsiElement): String = GradleNameElement.escape(element.text)

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
    val result = ExternalNameInfo(modelName, ExternalNameInfo.ExternalNameSyntax.UNKNOWN)
    for (e in map.entrySet) {
      if (e.modelEffectDescription.property.name == modelName) {

        if (e.versionConstraint?.isOkWith(this.context.agpVersion) == false) continue
        when (e.modelEffectDescription.semantics) {
          PropertySemanticsDescription.VAR -> return ExternalNameInfo(e.surfaceSyntaxDescription.name,
                                                                      ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT)

          else -> Unit
        }
      }
    }
    return result
  }

}