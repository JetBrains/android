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

import com.android.tools.idea.templates.Parameter.Constraint
import com.google.common.base.Objects
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import com.google.common.collect.Maps
import com.google.common.collect.Sets

import com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces

/**
 * Class which handles setting up the relationships between a bunch of [Parameter]s and then
 * resolves them.
 */
class ParameterValueResolver private constructor(parameters: Iterable<Parameter>,
                                                 private val myUserValues: Map<Parameter, Any>,
                                                 private val myAdditionalValues: Map<String, Any>,
                                                 private val myDeduplicator: Deduplicator) {
  private val myComputedParameters = Sets.newHashSet<Parameter>()
  private val myStaticParameters = Sets.newHashSet<Parameter>()
  private val myStringEvaluator = StringEvaluator()

  init {
    for (parameter in parameters) {
      if (parameter != null && !isEmptyOrSpaces(parameter.id)) {
        if (!isEmptyOrSpaces(getSuggestOrInitial(parameter)) && !myUserValues.containsKey(parameter)) {
          myComputedParameters.add(parameter)
        }
        else {
          myStaticParameters.add(parameter)
        }
      }
    }
  }

  /**
   * Returns a map of parameters to their resolved values.
   */
  @Throws(CircularParameterDependencyException::class)
  fun resolve(): Map<Parameter, Any> {
    val staticValues = getStaticParameterValues(myUserValues, myAdditionalValues)
    val computedValues = computeParameterValues(staticValues)

    val allValues = Maps.newHashMapWithExpectedSize<Parameter, Any>(computedValues.size + staticValues.size)
    for (parameter in Iterables.concat(myStaticParameters, myComputedParameters)) {
      allValues[parameter] = computedValues[parameter.id]
    }
    return allValues
  }

  private fun computeParameterValue(computedParameter: Parameter, currentValues: Map<String, Any>): Any? {
    val suggest = getSuggestOrInitial(computedParameter)

    assert(!isEmptyOrSpaces(suggest))
    var value = myStringEvaluator.evaluate(suggest, currentValues)
    value = myDeduplicator.deduplicate(computedParameter, value)
    return decodeInitialValue(computedParameter, value)
  }

  private fun getStaticParameterValues(userValues: Map<Parameter, Any>,
                                       additionalValues: Map<String, Any>): Map<String, Any> {
    val knownValues = Maps.newHashMapWithExpectedSize<String, Any>(myStaticParameters.size + additionalValues.size)
    knownValues.putAll(additionalValues)
    for (parameter in myStaticParameters) {
      val value: Any?
      if (userValues.containsKey(parameter)) {
        value = userValues[parameter]
      }
      else if (additionalValues.containsKey(parameter.id)) {
        value = additionalValues[parameter.id]
      }
      else {
        val initial = parameter.initial
        value = decodeInitialValue(parameter, initial)
      }
      knownValues[parameter.id] = value
    }
    return knownValues
  }

  /**
   * Computes values of the parameters with non-static default values.
   *
   * These parameters may depend on other computable parameters. We do not have that information
   * (expressions are evaluated with FreeMarker), so we keep reevaluating the parameter values until
   * they stabilize.
   */
  @Throws(CircularParameterDependencyException::class)
  private fun computeParameterValues(staticValues: Map<String, Any>): Map<String, Any> {
    val computedValues = Maps.newHashMapWithExpectedSize<String, Any>(myComputedParameters.size + staticValues.size)
    computedValues.putAll(staticValues)
    for (parameter in myComputedParameters) {
      computedValues[parameter.id] = ""
    }

    // Limit the number of iterations before we recognize there's circular dependency.
    // The maximum depth of the parameter dependency tree would be a number of parameters,
    // hence this is our "worst case" number of iterations (note we'll need +1 iteration
    // to check the value is stable) we would need to compute the values.
    // Update: it turns out we do have circular dependencies which in most cases turn into
    // a stable situation, but worst case is 2 times the number of iterations since a value
    // can also be modified because of a "unique" qualifier.
    val maxIterations = 2 * myComputedParameters.size
    var updatedValues: Map<String, Any> = ImmutableMap.of()
    for (i in 0..maxIterations) {
      updatedValues = computeUpdatedValues(computedValues)
      if (updatedValues.isEmpty()) {
        return computedValues
      }
      else {
        computedValues.putAll(updatedValues)
      }
    }
    throw CircularParameterDependencyException(updatedValues.keys)
  }

  private fun computeUpdatedValues(values: Map<String, Any>): Map<String, Any> {
    val updatedValues = Maps.newHashMapWithExpectedSize<String, Any>(myComputedParameters.size)
    for (computedParameter in myComputedParameters) {
      val value = computeParameterValue(computedParameter, values)
      val id = computedParameter.id
      if (!Objects.equal(values[id], value)) {
        updatedValues[id] = value
      }
    }
    return updatedValues
  }

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
    fun resolve(parameters: Iterable<Parameter>,
                userValues: Map<Parameter, Any>,
                additionalValues: Map<String, Any>,
                deduplicator: Deduplicator = DO_NOTHING_DEDUPLICATOR): Map<Parameter, Any> {
      val resolver = ParameterValueResolver(parameters, userValues, additionalValues, deduplicator)
      return resolver.resolve()
    }

    private fun getSuggestOrInitial(parameter: Parameter): String =
      if (isEmptyOrSpaces(parameter.suggest) && parameter.constraints.contains(Constraint.UNIQUE))
        parameter.initial!!
      else
        parameter.suggest!!

    private fun decodeInitialValue(input: Parameter, initial: String?): Any? {
      return if (initial != null && input.type === Parameter.Type.BOOLEAN) {
        java.lang.Boolean.valueOf(initial)
      }
      else {
        initial
      }
    }
  }
}
/**
 * @see .resolve
 */
