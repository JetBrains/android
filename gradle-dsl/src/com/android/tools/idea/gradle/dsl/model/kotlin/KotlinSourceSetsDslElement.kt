/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.kotlin

import com.android.tools.idea.gradle.dsl.api.kotlin.KotlinSourceSetModel
import com.android.tools.idea.gradle.dsl.model.kotlin.KotlinSourceSetsDslElement.KotlinSourceSetsDslElementSchema
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription

class KotlinSourceSetsDslElement(
  parent: GradleDslElement,
  name: GradleNameElement
) : GradleDslElementMap(parent, name), GradleDslNamedDomainContainer {

  override fun isBlockElement(): Boolean = true

  fun get(): List<KotlinSourceSetModel> {
    val result = mutableListOf<KotlinSourceSetModel>()
    for (dslElement in getValues(KotlinSourceSetDslElement::class.java)) {
      result.add(KotlinSourceSetModelImpl(dslElement!!))
    }
    return result
  }

  override fun getChildPropertiesElementDescription(converter: GradleDslNameConverter?, name: String?): PropertiesElementDescription<*> =
    KotlinSourceSetDslElement.KOTLIN_SOURCE_SET


  override fun implicitlyExists(name: String): Boolean = existingSourceSets.contains(name)

  class KotlinSourceSetsDslElementSchema : GradlePropertiesDslElementSchema() {
    override fun getBlockElementDescription(kind: GradleDslNameConverter.Kind?, name: String?): PropertiesElementDescription<*> =
      KotlinSourceSetDslElement.KOTLIN_SOURCE_SET
  }

  companion object {

    @JvmField
    val KOTLIN_SOURCE_SETS = PropertiesElementDescription(
      "sourceSets",
      KotlinSourceSetsDslElement::class.java,
      { parent: GradleDslElement, name: GradleNameElement -> KotlinSourceSetsDslElement(parent, name) }
    ) { KotlinSourceSetsDslElementSchema() }

    private val existingSourceSets = listOf("commonMain", "commonTest")
  }
}