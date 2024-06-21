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

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.collect.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

class KotlinSourceSetDslElement(
  parent: GradleDslElement,
  name: GradleNameElement
) : GradleDslBlockElement(parent, name), GradleDslNamedDomainElement {

  private var methodName: String? = null

  override fun getMethodName(): String? {
    return methodName
  }

  override fun setMethodName(value: String?) {
    methodName = value
  }

  override fun addParsedElement(element: GradleDslElement) {
    super.addParsedElement(element)
  }

  override fun getChildPropertiesElementsDescriptionMap(kind: GradleDslNameConverter.Kind?): ImmutableMap<String, PropertiesElementDescription<*>> =
    CHILD_PROPERTIES_ELEMENTS_MAP


  override fun getExternalToModelMap(converter: GradleDslNameConverter): ExternalToModelMap =
    getExternalToModelMap(converter, ktsToModelNameMap, groovyToModelNameMap, declarativeToModelNameMap)

  class KotlinSourceSetDslElementSchema : GradlePropertiesDslElementSchema() {
    override fun getPropertiesInfo(kind: GradleDslNameConverter.Kind): ExternalToModelMap {
      return getExternalProperties(kind, ktsToModelNameMap, groovyToModelNameMap, declarativeToModelNameMap)
    }

    override fun getAllBlockElementDescriptions(kind: GradleDslNameConverter.Kind): ImmutableMap<String, PropertiesElementDescription<*>> =
      CHILD_PROPERTIES_ELEMENTS_MAP
  }

  companion object {
    @JvmField
    val KOTLIN_SOURCE_SET = PropertiesElementDescription(
      null,
      KotlinSourceSetDslElement::class.java,
      { parent: GradleDslElement, name: GradleNameElement -> KotlinSourceSetDslElement(parent, name) }
    ) { KotlinSourceSetDslElementSchema() }

    val CHILD_PROPERTIES_ELEMENTS_MAP: ImmutableMap<String, PropertiesElementDescription<*>> =
      ImmutableMap.copyOf(persistentMapOf("dependencies" to DependenciesDslElement.DEPENDENCIES))

    private val ktsToModelNameMap: ExternalToModelMap = ExternalToModelMap.empty

    private val groovyToModelNameMap: ExternalToModelMap = ExternalToModelMap.empty

    private val declarativeToModelNameMap: ExternalToModelMap = ExternalToModelMap.empty
  }
}