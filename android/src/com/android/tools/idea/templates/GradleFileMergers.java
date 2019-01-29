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
package com.android.tools.idea.templates;

import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

/**
 * Utilities shared between {@link GradleFileSimpleMerger} and {@link GradleFilePsiMerger}.
 */
public class GradleFileMergers {
  /**
   * Name of the dependencies DSL block.
   */
  static final String DEPENDENCIES = "dependencies";

  private static final ImmutableList<String> KNOWN_CONFIGURATIONS_IN_ORDER =
    ImmutableList.of("feature", "api", "implementation", "compile",
                     "testApi", "testImplementation", "testCompile",
                     "androidTestApi", "androidTestImplementation", "androidTestCompile", "androidTestUtil");

  private static final ImmutableSet<ImmutableSet<String>> CONFIGURATION_GROUPS = ImmutableSet.of(
    ImmutableSet.of("feature", "api", "implementation", "compile"),
    ImmutableSet.of("testApi", "testImplementation", "testCompile"),
    ImmutableSet.of("androidTestApi", "androidTestImplementation", "androidTestCompile"));

  /**
   * Defined an ordering on gradle configuration names.
   */
  static final Ordering<String> CONFIGURATION_ORDERING =
    Ordering
      .natural()
      .onResultOf((@NotNull String input) -> {
        int result = KNOWN_CONFIGURATIONS_IN_ORDER.indexOf(input);
        return result != -1 ? result : KNOWN_CONFIGURATIONS_IN_ORDER.size();
      })
      .compound(Ordering.natural());

  /**
   * Perform an in-place removal of entries from {@code newDependencies} that are also in {@code existingDependencies}.
   * If {@code psiGradleCoordinates} and {@code factory} are supplied, it also increases the visibility of
   * {@code existingDependencies} if needed, for example from "implementation" to "api".
   */
  public static void updateExistingDependencies(@NotNull Map<String, Multimap<String, GradleCoordinate>> newDependencies,
                                                @NotNull Map<String, Multimap<String, GradleCoordinate>> existingDependencies,
                                                @Nullable Map<GradleCoordinate, PsiElement> psiGradleCoordinates,
                                                @Nullable GroovyPsiElementFactory factory) {
    for (String configuration : newDependencies.keySet()) {
      // If we already have an existing "compile" dependency, the same "implementation" or "api" dependency should not be added
      for (String possibleConfiguration : getConfigurationGroup(configuration)) {
        if (existingDependencies.containsKey(possibleConfiguration)) {
          for (Map.Entry<String, GradleCoordinate> possibleConfigurationEntry: existingDependencies.get(possibleConfiguration).entries()) {
            String coordinateId = possibleConfigurationEntry.getKey();
            newDependencies.get(configuration).removeAll(coordinateId);

            // Check if we need to convert the existing configuration. eg from "implementation" to "api", but not the other way around.
            if (psiGradleCoordinates != null && factory != null
                && CONFIGURATION_ORDERING.compare(configuration, possibleConfiguration) < 0) {
              PsiElement psiEntry = psiGradleCoordinates.get(possibleConfigurationEntry.getValue());
              psiEntry.replace(factory.createExpressionFromText(configuration));
            }
          }
        }
      }
    }
  }

  private GradleFileMergers() {}

  private static ImmutableSet<String> getConfigurationGroup(String configuration) {
    for (ImmutableSet<String> group : CONFIGURATION_GROUPS) {
      if (group.contains(configuration)) {
        return group;
      }
    }

    return ImmutableSet.of(configuration);
  }
}
