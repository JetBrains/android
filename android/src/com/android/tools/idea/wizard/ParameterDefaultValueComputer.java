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
package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.Parameter;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Function for computing default parameter value based on current values of
 * other parameters.
 */
public final class ParameterDefaultValueComputer implements Function<Parameter, Object> {
  private final Map<Parameter, Object> nonDefaultValues;
  @Nullable private final Deduplicator myDeduplicateValueFunction;
  private final StringEvaluator myStringEvaluator = new StringEvaluator();
  private final Map<String, Parameter> myParameterIds;
  private Map<Parameter, Object> myDefaultsMap;
  private Set<Parameter> inComputation = Sets.newHashSet();

  private ParameterDefaultValueComputer(Set<Parameter> parameterSet,
                                        Map<Parameter, Object> nonCurrentValues,
                                        @Nullable Deduplicator deduplicateValueFunction) {
    nonDefaultValues = nonCurrentValues;
    myDeduplicateValueFunction = deduplicateValueFunction;
    myParameterIds = Maps.uniqueIndex(parameterSet, new Function<Parameter, String>() {
      @Override
      public String apply(Parameter input) {
        return input.id;
      }
    });
  }

  /**
   * Return a dynamic map of parameter values.
   * <p/>
   * Performing get on the map will return the most current parameter value.
   * Changes to values map will be immediately reflected on the computed
   * default values.
   *
   * @param parameters list of parameters that need to be present in the map
   * @param values map of parameters with non-default values.
   * @return dynamic map
   */
  public static Map<Parameter, Object> newDefaultValuesMap(Iterable<Parameter> parameters,
                                                           Map<Parameter, Object> values,
                                                           @Nullable Deduplicator deduplicateValueFunction) {
    Set<Parameter> parameterSet = FluentIterable.from(parameters).filter(new Predicate<Parameter>() {
      @Override
      public boolean apply(Parameter input) {
        return input != null && !StringUtil.isEmpty(input.name);
      }
    }).toSet();
    ParameterDefaultValueComputer computer = new ParameterDefaultValueComputer(parameterSet, values, deduplicateValueFunction);
    Map<Parameter, Object> defaultsMap = Maps.asMap(parameterSet, computer);
    computer.setDefaultsMap(defaultsMap);
    return defaultsMap;
  }

  @Nullable
  private static Object decodeInitialValue(Parameter input, @Nullable String initial) {
    if (initial != null && input.type == Parameter.Type.BOOLEAN) {
      return Boolean.valueOf(initial);
    }
    else {
      return initial;
    }
  }

  private synchronized void setDefaultsMap(Map<Parameter, Object> defaultsMap) {
    myDefaultsMap = defaultsMap;
  }

  @Override
  public Object apply(Parameter parameter) {
    if (nonDefaultValues.containsKey(parameter)) {
      return nonDefaultValues.get(parameter);
    }
    else {
      String value = !StringUtil.isEmpty(parameter.suggest) ? deriveValue(parameter) : parameter.initial;
      if (myDeduplicateValueFunction != null) {
        value = myDeduplicateValueFunction.deduplicate(parameter, value);
      }
      return decodeInitialValue(parameter, value);
    }
  }

  @Nullable
  private synchronized String deriveValue(Parameter parameter) {
    if (StringUtil.isEmpty(parameter.suggest)) {
      return null;
    }
    if (inComputation.contains(parameter)) {
      return "";
    }
    inComputation.add(parameter);
    try {
      Function<Parameter, Object> values = Functions.forMap(myDefaultsMap);
      Function<String, Parameter> name = Functions.forMap(myParameterIds);
      Function<String, Object> nameToValue = Functions.compose(values, name);
      return myStringEvaluator.evaluate(parameter.suggest, Maps.asMap(myParameterIds.keySet(), nameToValue));
    }
    finally {
      inComputation.remove(parameter);
    }
  }

  public interface Deduplicator {
    @Nullable
    String deduplicate(@NotNull Parameter parameter, @Nullable String value);
  }
}
