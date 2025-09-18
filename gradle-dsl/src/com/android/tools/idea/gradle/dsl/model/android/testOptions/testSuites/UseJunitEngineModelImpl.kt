/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android.testOptions.testSuites

import com.android.tools.idea.gradle.dsl.api.android.testOptions.testSuites.UseJunitEngineModel
import com.android.tools.idea.gradle.dsl.api.ext.RawText
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.testSuites.UseJunitEngineDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType

class UseJunitEngineModelImpl(dslElement: UseJunitEngineDslElement) : GradleDslBlockModel(dslElement), UseJunitEngineModel {

  override fun inputs(): ResolvedPropertyModel {
    return getModelForProperty(INPUTS)
  }

  override fun addInput(input: String): ResolvedPropertyModel {
    val model = inputs()

    val existingInputs = model.toList()?.mapNotNull { it.valueAsString() } ?: emptyList()
    if (!existingInputs.contains(input)) {
      val newInput = model.addListValue()
      newInput?.setValue(RawText(input, input))
    }

    return model
  }

  override fun includeEngines(): ResolvedPropertyModel {
    return getModelForProperty(INCLUDED_ENGINES)
  }

  override fun addIncludeEngine(engine: String): ResolvedPropertyModel {
    val model = includeEngines()

    val existingEngines = model.toList()?.mapNotNull { it.valueAsString() } ?: emptyList()
    if (!existingEngines.contains(engine)) {
      val newEngine = model.addListValue()
      newEngine?.setValue(engine)
    }

    return model
  }

  /**
   * Returns the list of engine dependencies declared for this JUnit engine configuration.
   *
   * Only engine dependencies configured with the compact string notation
   * (e.g., "group:name:version") are returned.
   *
   * TODO(b/446141700): Add support for other dependency notations
   * (e.g., map notation, version catalog alias).
   */
  override fun enginesDependencies(): List<String> {
    return myDslElement.children
      .filter { isEngineDependency(it) }
      .mapNotNull { parseEngineDependency(it) }
  }

  private fun parseEngineDependency(dslElement: GradleDslElement): String? {
    return when (dslElement) {
      is GradleDslMethodCall -> {
        dslElement.arguments.firstNotNullOf { (it as GradleDslLiteral).getValue(String::class.java) }
      }

      is GradleDslLiteral -> {
        dslElement.getValue(String::class.java)
      }

      else -> throw NoSuchElementException("Unexpected '$ENGINES_DEPENDENCIES' GradleDslElement in useJunitEngineModel: $myDslElement")
    }
  }

  private fun isEngineDependency(element: GradleDslElement): Boolean {
    return when (element) {
      is GradleDslMethodCall -> {
        return ENGINES_DEPENDENCIES == element.methodName
      }

      is GradleDslLiteral -> {
        return ENGINES_DEPENDENCIES == element.name
      }

      else -> false
    }
  }

  /**
   * Adds an engine dependency to this JUnit engine configuration.
   *
   * Only engine dependencies configured with the compact string notation
   * (e.g., "group:name:version") are supported.
   *
   * TODO(b/446141700): Add support for other dependency notations
   * (e.g., map notation, version catalog alias).
   */
  override fun addEngineDependency(compactNotation: String) {
    val enginesDependencies = enginesDependencies()
    if (enginesDependencies.contains(compactNotation)) {
      return
    }

    val methodCall = GradleDslMethodCall(myDslElement, GradleNameElement.empty(), ENGINES_DEPENDENCIES)
    val nameArgument = GradleDslLiteral(methodCall, GradleNameElement.empty())
    nameArgument.setValue(compactNotation)
    methodCall.addNewArgument(nameArgument)
    myDslElement.setNewElement(methodCall)
  }

  companion object {
    @JvmField
    val INPUTS: ModelPropertyDescription = ModelPropertyDescription("mInputs", ModelPropertyType.MUTABLE_LIST)

    @JvmField
    val INCLUDED_ENGINES: ModelPropertyDescription = ModelPropertyDescription("mIncludeEngines", ModelPropertyType.MUTABLE_LIST)

    private const val ENGINES_DEPENDENCIES = "enginesDependencies"
  }
}