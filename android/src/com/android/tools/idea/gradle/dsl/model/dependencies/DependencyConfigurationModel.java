/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependencyConfigurationDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DependencyConfigurationModel {
  @NonNls private static final String EXCLUDE = "exclude";
  @NonNls private static final String FORCE = "force";
  @NonNls private static final String TRANSITIVE = "transitive";

  @NotNull DependencyConfigurationDslElement myConfigurationElement;

  public DependencyConfigurationModel(@NotNull DependencyConfigurationDslElement configurationElement) {
    myConfigurationElement = configurationElement;
  }

  @NotNull
  public List<ExcludedDependencyModel> excludes() {
    GradleDslElementList elementList = myConfigurationElement.getPropertyElement(EXCLUDE, GradleDslElementList.class);
    if (elementList == null) {
      return ImmutableList.of();
    }

    List<ExcludedDependencyModel> excludedDependencies = new ArrayList<>();
    for (GradleDslExpressionMap excludeElement : elementList.getElements(GradleDslExpressionMap.class)) {
      excludedDependencies.add(new ExcludedDependencyModel(excludeElement));
    }
    return excludedDependencies;
  }

  @NotNull
  public GradleNullableValue<Boolean> force() {
    return myConfigurationElement.getLiteralProperty(FORCE, Boolean.class);
  }

  @NotNull
  public GradleNullableValue<Boolean> transitive() {
    return myConfigurationElement.getLiteralProperty(TRANSITIVE, Boolean.class);
  }
}
