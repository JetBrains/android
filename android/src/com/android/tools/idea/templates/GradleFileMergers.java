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

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Utilities shared between {@link GradleFileSimpleMerger} and {@link GradleFilePsiMerger}.
 */
public class GradleFileMergers {
  /**
   * Name of the dependencies DSL block.
   */
  static final String DEPENDENCIES = "dependencies";

  /**
   * Defined an ordering on gradle configuration names.
   *
   * <p>The order is:
   * <ol>
   *   <li>compile
   *   <li>testCompile
   *   <li>androidTestCompile
   *   <li>everything else, in alphabetical order
   * </ol>
   * @return
   */
  static final Ordering<String> CONFIGURATION_ORDERING =
    Ordering
      .natural()
      .onResultOf((String input) -> {
        switch (input) {
          case SdkConstants.GRADLE_COMPILE_CONFIGURATION:
            return 1;
          case SdkConstants.GRADLE_TEST_COMPILE_CONFIGURATION:
            return 2;
          case SdkConstants.GRADLE_ANDROID_TEST_COMPILE_CONFIGURATION:
            return 3;
          default:
            return 4;
        }
      })
      .compound(Ordering.natural());

  /**
   * Perform an in-place removal of entries from {@code newDependencies} that are also in {@code existingDependencies}
   */
  public static void removeExistingDependencies(@NotNull Map<String, Multimap<String, GradleCoordinate>> newDependencies,
                                                @NotNull Map<String, Multimap<String, GradleCoordinate>> existingDependencies) {
    for (String configuration : newDependencies.keySet()) {
      if (existingDependencies.containsKey(configuration)) {
        for (Map.Entry<String, GradleCoordinate> entry : existingDependencies.get(configuration).entries()) {
          newDependencies.get(configuration).remove(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  private GradleFileMergers() {}
}
