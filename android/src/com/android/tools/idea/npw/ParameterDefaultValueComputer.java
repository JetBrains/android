/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.StringEvaluator;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Function for computing default parameter value based on current values of
 * other parameters.
 */
public final class ParameterDefaultValueComputer {
  private static final Deduplicator DO_NOTHING_DEDUPLICATOR = new Deduplicator() {
    @Nullable
    @Override
    public String deduplicate(@NotNull Parameter parameter, @Nullable String value) {
      return value;
    }
  };
  @NotNull private final Set<Parameter> myComputedParameters = Sets.newHashSet();
  @NotNull private final Set<Parameter> myStaticParameters = Sets.newHashSet();
  @NotNull private final Deduplicator myDeduplicator;
  @NotNull private final StringEvaluator myStringEvaluator = new StringEvaluator();
  @NotNull private final Map<Parameter, Object> myUserValues;
  @NotNull private final Map<String, Object> myImplicitParameters;

  @Nullable
  private static Object decodeInitialValue(Parameter input, @Nullable String initial) {
    if (initial != null && input.type == Parameter.Type.BOOLEAN) {
      return Boolean.valueOf(initial);
    }
    else {
      return initial;
    }
  }

  /**
   * Creates a new computer instance
   *
   * @param parameters         list of parameters that need to be present in the map
   * @param userValues         parameter values changed by the user.
   * @param implicitParameters parameters that were not declared by the template but are instead a part of our "runtime"
   * @param deduplicator       a function that ensures uniqueness of the parameter value
   */
  public ParameterDefaultValueComputer(@NotNull Iterable<Parameter> parameters,
                                       @NotNull Map<Parameter, Object> userValues,
                                       @NotNull Map<String, Object> implicitParameters,
                                       @Nullable Deduplicator deduplicator) {
    myUserValues = userValues;
    myImplicitParameters = implicitParameters;
    for (Parameter parameter : parameters) {
      if (parameter != null && !StringUtil.isEmptyOrSpaces(parameter.id)) {
        if (!StringUtil.isEmptyOrSpaces(parameter.suggest) && !userValues.containsKey(parameter)) {
          myComputedParameters.add(parameter);
        }
        else {
          myStaticParameters.add(parameter);
        }
      }
    }
    myDeduplicator = deduplicator == null ? DO_NOTHING_DEDUPLICATOR : deduplicator;
  }

  /**
   * Returns a map of parameter values
   *
   * @return mapping between paramaeter and its current value
   * @throws CircularParameterDependencyException if there is a circular dependecy between
   *                                              parameters preventing us from computing the default values.
   */
  public Map<Parameter, Object> getParameterValues() throws CircularParameterDependencyException {
    Map<String, Object> staticValues = getStaticParameterValues(myUserValues, myImplicitParameters);
    Map<String, Object> computedValues = computeParameterValues(staticValues);

    HashMap<Parameter, Object> allValues = Maps.newHashMapWithExpectedSize(computedValues.size() + staticValues.size());
    for (Parameter parameter : Iterables.concat(myStaticParameters, myComputedParameters)) {
      allValues.put(parameter, computedValues.get(parameter.id));
    }
    return allValues;
  }

  @Nullable
  private Object computeParameterValue(@NotNull Parameter computedParameter, @NotNull Map<String, Object> currentValues) {
    String suggest = computedParameter.suggest;

    assert !StringUtil.isEmptyOrSpaces(suggest);
    String value = myStringEvaluator.evaluate(suggest, currentValues);
    value = myDeduplicator.deduplicate(computedParameter, value);
    return decodeInitialValue(computedParameter, value);
  }

  private Map<String, Object> getStaticParameterValues(@NotNull Map<Parameter, Object> values,
                                                       @NotNull Map<String, Object> implicitParameters) {
    final Map<String, Object> knownValues = Maps.newHashMapWithExpectedSize(myStaticParameters.size() + implicitParameters.size());
    knownValues.putAll(implicitParameters);
    for (Parameter parameter : myStaticParameters) {
      Object value;
      if (values.containsKey(parameter)) {
        value = values.get(parameter);
      }
      else {
        String initial = parameter.initial;
        value = decodeInitialValue(parameter, initial);
      }
      knownValues.put(parameter.id, value);
    }
    return knownValues;
  }

  /**
   * <p>Computes values of the parameters with non-static default values.</p>
   * <p>These parameters may depend on other computable parameters. We do not have that information
   * (expressions are evaluated with FreeMarker), so we keep reevaluating the parameter values until
   * they stabilize.</p>
   *
   * @return a map of parameter ID to parameter values.
   * @throws CircularParameterDependencyException means it is not possible to stabilize parameter values
   *                                              due to circular dependency
   */
  private Map<String, Object> computeParameterValues(Map<String, Object> staticValues) throws CircularParameterDependencyException {
    Map<String, Object> computedValues = Maps.newHashMapWithExpectedSize(myComputedParameters.size() + staticValues.size());
    computedValues.putAll(staticValues);
    for (Parameter parameter : myComputedParameters) {
      computedValues.put(parameter.id, "");
    }

    // Limit the number of iterations before we recognize there's circular dependency.
    // The maximum depth of the parameter dependency tree would be a number of parameters,
    // hence this is our "worst case" number of iterations (note we'll need +1 iteration
    // to check the value is stable) we would need to compute the values.
    // Update: it turns out we do have circular dependencies which in most cases turn into
    // a stable situation, but worst case is 2 times the number of iterations since a value
    // can also be modified because of a "unique" qualifier.
    final int maxIterations = 2 * myComputedParameters.size();
    Map<String, Object> updatedValues = ImmutableMap.of();
    for (int i = 0; i <= maxIterations; i++) {
      updatedValues = computeUpdatedValues(computedValues);
      if (updatedValues.isEmpty()) {
        return computedValues;
      }
      else {
        computedValues.putAll(updatedValues);
      }
    }
    throw new CircularParameterDependencyException(updatedValues.keySet());
  }

  @NotNull
  private Map<String, Object> computeUpdatedValues(@NotNull Map<String, Object> values) {
    Map<String, Object> updatedValues = Maps.newHashMapWithExpectedSize(myComputedParameters.size());
    for (Parameter computedParameter : myComputedParameters) {
      Object value = computeParameterValue(computedParameter, values);
      String id = computedParameter.id;
      if (!Objects.equal(values.get(id), value)) {
        updatedValues.put(id, value);
      }
    }
    return updatedValues;
  }

  public interface Deduplicator {
    @Nullable
    String deduplicate(@NotNull Parameter parameter, @Nullable String value);
  }
}
