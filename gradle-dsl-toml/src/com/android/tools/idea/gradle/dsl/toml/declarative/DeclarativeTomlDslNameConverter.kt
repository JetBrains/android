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
package com.android.tools.idea.gradle.dsl.toml.declarative

import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.DECLARATIVE_TOML
import com.android.tools.idea.gradle.dsl.toml.TomlDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.AUGMENT_LIST
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR

interface DeclarativeTomlDslNameConverter: TomlDslNameConverter {
  override fun getKind() = DECLARATIVE_TOML

  override fun externalNameForParent(modelName: String, context: GradleDslElement): ExternalNameInfo {
    val map = context.getExternalToModelMap(this)
    var result = ExternalNameInfo(modelName, ExternalNameInfo.ExternalNameSyntax.UNKNOWN)
    for (e in map.entrySet) {
      if (e.modelEffectDescription.property.name == modelName) {

        if (e.versionConstraint?.isOkWith(this.context.agpVersion) == false) continue
        when (e.modelEffectDescription.semantics) {
          VAR -> return ExternalNameInfo(e.surfaceSyntaxDescription.name,
                                         ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT)
          AUGMENT_LIST ->
            result = ExternalNameInfo(e.surfaceSyntaxDescription.name, ExternalNameInfo.ExternalNameSyntax.METHOD)

          else -> Unit
        }
      }
    }
    return result
  }
}