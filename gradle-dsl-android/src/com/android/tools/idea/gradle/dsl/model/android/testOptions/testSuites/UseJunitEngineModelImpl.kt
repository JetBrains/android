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
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.dependencies.NotationStrategy
import com.android.tools.idea.gradle.dsl.model.dependencies.CompactNotationStrategy
import com.android.tools.idea.gradle.dsl.model.dependencies.MapNotationStrategy
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyCollectorDependencyModel
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.testSuites.UseJunitEngineDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType
import com.android.tools.idea.gradle.dsl.utils.isInVersionCatalogFile
import com.android.tools.idea.gradle.dsl.utils.resolveElement

class UseJunitEngineModelImpl(dslElement: UseJunitEngineDslElement) : GradleDslBlockModel(dslElement), UseJunitEngineModel {

  /**
   * Returns the [ResolvedPropertyModel] for the "inputs" property.
   */
  override fun inputs(): ResolvedPropertyModel {
    return getModelForProperty(INPUTS)
  }

  /**
   * Adds a new input to the "inputs" property if it doesn't already exist.
   *
   * @param input The input string to add.
   * @return The updated [ResolvedPropertyModel] for the "inputs" property.
   */
  override fun addInput(input: String): ResolvedPropertyModel {
    val model = inputs()

    val existingInputs = model.toList()?.mapNotNull { it.valueAsString() } ?: emptyList()
    if (!existingInputs.contains(input)) {
      val newInput = model.addListValue()
      newInput?.setValue(RawText(input, input))
    }

    return model
  }

  /**
   * Returns the [ResolvedPropertyModel] for the "includeEngines" property.
   */
  override fun includeEngines(): ResolvedPropertyModel {
    return getModelForProperty(INCLUDED_ENGINES)
  }

  /**
   * Adds a new engine to the "includeEngines" property if it doesn't already exist.
   *
   * @param engine The engine string to add.
   * @return The updated [ResolvedPropertyModel] for the "includeEngines" property.
   */
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
   */
  override fun enginesDependencies(): List<DependencyCollectorDependencyModel> {
    return myDslElement.allPropertyElements
      .flatMap { parseEngineDependency(it) }
  }

  private fun parseEngineDependency(dslElement: GradleDslElement): List<DependencyCollectorDependencyModel> {
    return when (val resolved = resolveElement(dslElement)) {
      is GradleDslMethodCall -> {
        if (resolved.methodName != ENGINES_DEPENDENCIES) return emptyList()

        val argument = resolved.arguments.first()
        val resolvedArgument = resolveElement(argument)

        listOfNotNull(toDependencyCollectorDependencyModel(argument, resolvedArgument))
      }

      else -> {
        if (dslElement.name != ENGINES_DEPENDENCIES) return emptyList()

        listOfNotNull(toDependencyCollectorDependencyModel(dslElement, resolved))
      }
    }
  }

  private fun toDependencyCollectorDependencyModel(
    element: GradleDslElement,
    resolved: GradleDslElement,
  ): DependencyCollectorDependencyModel? {
    if (element !is GradleDslExpression || resolved !is GradleDslExpressionMap && resolved !is GradleDslSimpleExpression) {
      return null
    }

    val notationStrategy = getNotationStrategy(element) ?: return null
    return DependencyCollectorDependencyModel(
      element,
      notationStrategy,
      isInVersionCatalogFile(resolved)
    )
  }

  private fun getNotationStrategy(dslExpression: GradleDslExpression): NotationStrategy? {
    val resolvedExpression: GradleDslExpression = resolveElement(dslExpression) as? GradleDslExpression ?: dslExpression
    if (resolvedExpression is GradleDslExpressionMap) {
      return MapNotationStrategy(resolvedExpression)
    }
    else if (dslExpression is GradleDslSimpleExpression) {
      return CompactNotationStrategy(dslExpression, false)
    }
    return null
  }

  /**
   * Adds an engine dependency to this JUnit engine configuration using a compact notation.
   * If the dependency already exists, this method does nothing.
   *
   * @param compactNotation The dependency in compact notation (e.g., "group:name:version").
   */
  override fun addEngineDependency(compactNotation: String) {
    if (hasEngineDependency(compactNotation)) return

    val methodCall = GradleDslMethodCall(myDslElement, GradleNameElement.empty(), ENGINES_DEPENDENCIES)
    val nameArgument = GradleDslLiteral(methodCall, GradleNameElement.empty())
    nameArgument.setValue(compactNotation)
    methodCall.addNewArgument(nameArgument)
    myDslElement.setNewElement(methodCall)
  }

  /**
   * Returns true if an engine dependency with the given [compactNotation] already exists.
   */
  override fun hasEngineDependency(compactNotation: String): Boolean {
    val enginesDependencies = enginesDependencies().map { it.compactNotation() }
    return enginesDependencies.contains(compactNotation)
  }

  /**
   * Adds an engine dependency to this JUnit engine configuration using a [ReferenceTo].
   * If the dependency already exists, this method does nothing.
   *
   * @param reference A [ReferenceTo] object pointing to a dependency.
   */
  override fun addEngineDependency(reference: ReferenceTo) {
    if (hasEngineDependency(reference)) {
      return
    }

    val methodCall = GradleDslMethodCall(myDslElement, GradleNameElement.empty(), ENGINES_DEPENDENCIES)
    val nameArgument = GradleDslLiteral(methodCall, GradleNameElement.empty())
    nameArgument.setValue(reference)
    methodCall.addNewArgument(nameArgument)
    myDslElement.setNewElement(methodCall)
  }

  /**
   * Returns true if an engine dependency referring to the given [reference] already exists.
   */
  override fun hasEngineDependency(reference: ReferenceTo): Boolean {
    val existingDependencies = enginesDependencies().map { it.dslElement }
    return existingDependencies.any { resolveElement(it) == reference.referredElement }
  }

  companion object {
    @JvmField
    val INPUTS: ModelPropertyDescription = ModelPropertyDescription("mInputs", ModelPropertyType.MUTABLE_LIST)

    @JvmField
    val INCLUDED_ENGINES: ModelPropertyDescription = ModelPropertyDescription("mIncludeEngines", ModelPropertyType.MUTABLE_LIST)

    private const val ENGINES_DEPENDENCIES = "enginesDependencies"
  }
}