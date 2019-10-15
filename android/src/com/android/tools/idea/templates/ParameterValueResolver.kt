/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.tools.idea.wizard.template.Constraint

/**
 * Class which handles setting up the relationships between a bunch of [Parameter]s and then resolves them.
 */
class ParameterValueResolver private constructor(
  private val parameters: Iterable<Parameter>,
  private val userValues: Map<Parameter, Any>,
  private val additionalValues: Map<String, Any>,
  private val deduplicator: Deduplicator
) {
  private val computedParameters: Collection<Parameter>
  private val staticParameters: Collection<Parameter>
  private val stringEvaluator = StringEvaluator()

  init {
    parameters
      .filterNot { it.id.isNullOrBlank() }
      .partition { getSuggestOrInitial(it).isBlank() || userValues.containsKey(it) }.run {
        staticParameters = first.toSet()
        computedParameters = second.toSet()
      }
  }

  /**
   * Returns a map of parameters to their resolved values.
   */
  @Throws(CircularParameterDependencyException::class)
  fun resolve(): Map<Parameter, Any> {
    val staticValues = getStaticParameterValues(userValues, additionalValues)
    val computedValues = computeParameterValues(staticValues)

    return parameters.associateWith { computedValues[it.id]!! }
  }

  private fun computeParameterValue(computedParameter: Parameter, currentValues: Map<String, Any>): Any? {
    val suggest = getSuggestOrInitial(computedParameter)

    assert(!suggest.isBlank())
    var value = stringEvaluator.evaluate(suggest, currentValues)
    value = deduplicator.deduplicate(computedParameter, value)
    return decodeInitialValue(computedParameter, value)
  }

  private fun getStaticParameterValues(userValues: Map<Parameter, Any>,
                                       additionalValues: Map<String, Any>): Map<String, Any> {
    val knownValues = HashMap(additionalValues)
    for (parameter in staticParameters) {
      knownValues[parameter.id!!] = when {
        userValues.containsKey(parameter) -> userValues[parameter]
        additionalValues.containsKey(parameter.id) -> additionalValues[parameter.id]
        else -> decodeInitialValue(parameter, parameter.initial)
      }!!
    }
    return knownValues
  }

  /**
   * Computes values of the parameters with non-static default values.
   *
   * These parameters may depend on other computable parameters. We do not have that information
   * (expressions are evaluated with FreeMarker), so we keep reevaluating the parameter values until they stabilize.
   */
  @Throws(CircularParameterDependencyException::class)
  private fun computeParameterValues(staticValues: Map<String, Any>): Map<String, Any> {
    val computedValues = HashMap(staticValues)
    for (parameter in computedParameters) {
      computedValues[parameter.id!!] = ""
    }

    // Limit the number of iterations before we recognize there's circular dependency.
    // The maximum depth of the parameter dependency tree would be a number of parameters,
    // hence this is our "worst case" number of iterations (note we'll need +1 iteration
    // to check the value is stable) we would need to compute the values.
    // Update: it turns out we do have circular dependencies which in most cases turn into
    // a stable situation, but worst case is 2 times the number of iterations since a value
    // can also be modified because of a "unique" qualifier.
    val maxIterations = 1 + 2 * computedParameters.size
    var updatedValues: Map<String, Any> = mapOf()
    repeat(maxIterations) {
      updatedValues = computeUpdatedValues(computedValues)
      if (updatedValues.isEmpty()) {
        return computedValues
      }
      computedValues.putAll(updatedValues)
    }
    throw CircularParameterDependencyException(updatedValues.keys)
  }

  private fun computeUpdatedValues(values: Map<String, Any>): Map<String, Any> = computedParameters
    .associate { it.id!! to computeParameterValue(it, values)!! }
    .filterNot { (id, v) -> values[id] == v }

  interface Deduplicator {
    fun deduplicate(parameter: Parameter, value: String?): String?
  }

  companion object {
    private val DO_NOTHING_DEDUPLICATOR = object : Deduplicator {
      override fun deduplicate(parameter: Parameter, value: String?): String? = value
    }

    /**
     * Resolve input parameters, returning a mapping of parameters to their resolved values.
     *
     * @param parameters       parameters to resolve
     * @param userValues       parameter values supplied by the user.
     * @param additionalValues parameters that were not declared by the template but are instead a part of our "runtime"
     * @param deduplicator     a function that ensures uniqueness of the parameter value
     */
    @Throws(CircularParameterDependencyException::class)
    @JvmOverloads
    fun resolve(
      parameters: Iterable<Parameter>,
      userValues: Map<Parameter, Any>,
      additionalValues: Map<String, Any>,
      deduplicator: Deduplicator = DO_NOTHING_DEDUPLICATOR
    ): Map<Parameter, Any> = ParameterValueResolver(parameters, userValues, additionalValues, deduplicator).resolve()

    private fun getSuggestOrInitial(parameter: Parameter): String =
      if (parameter.suggest.isNullOrBlank() && parameter.constraints.contains(Constraint.UNIQUE))
        parameter.initial!!
      else
        parameter.suggest!!

    private fun decodeInitialValue(input: Parameter, initial: String?): Any? =
      if (initial != null && input.type === Parameter.Type.BOOLEAN)
        initial.toBoolean()
      else
        initial
  }
}