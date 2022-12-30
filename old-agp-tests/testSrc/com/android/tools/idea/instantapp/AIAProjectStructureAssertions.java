/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeDependencies;
import com.android.tools.idea.gradle.model.IdeDependencyKt;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.util.containers.ContainerUtil;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Helper methods for checking modules in an AIA-enabled project are created correctly by a project sync.
 */
public final class AIAProjectStructureAssertions {
  /**
   * Asserts that the given module is an app module and that it has dependencies that match the given set of expected dependencies.
   */
  public static void assertModuleIsValidAIAApp(@NotNull Module module, @NotNull Collection<String> expectedDependencies)
    throws InterruptedException {
    assertModuleIsValidForAIA(module, expectedDependencies, IdeAndroidProjectType.PROJECT_TYPE_APP, true);
  }

  /**
   * Asserts that the given module is an instant app module and that it has dependencies that match the given set of expected dependencies.
   */
  public static void assertModuleIsValidAIAInstantApp(@NotNull Module module,
                                                      @NotNull Collection<String> expectedDependencies)
    throws InterruptedException {
    // At the moment we have no way to actually get InstantApp module dependencies (they aren't reported in the model) so we can't check
    // them. Hence expectedDependencies is replaced with ImmutableList.of() below
    assertModuleIsValidForAIA(module, ImmutableList.of(), IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP, false);
  }

  /**
   * Asserts that the given module is a Instant App Feature module and that it has dependencies that match the given set of
   * expected dependencies projects
   */
  public static void assertModuleIsValidAIAFeature(@NotNull Module module,
                                                   @NotNull Collection<String> expectedDependencies) throws InterruptedException {
    assertModuleIsValidForAIA(module, expectedDependencies, IdeAndroidProjectType.PROJECT_TYPE_FEATURE, false);
  }

  /**
   * Asserts that the given module is a base feature module and that it has dependencies that match the given set of expected dependencies.
   */
  public static void assertModuleIsValidAIABaseFeature(@NotNull Module module,
                                                       @NotNull Collection<String> expectedDependencies) throws InterruptedException {
    assertModuleIsValidForAIA(module, expectedDependencies, IdeAndroidProjectType.PROJECT_TYPE_FEATURE, true);
  }

  private static void assertModuleIsValidForAIA(@NotNull Module module,
                                                @NotNull Collection<String> expectedDependencies,
                                                IdeAndroidProjectType moduleType,
                                                boolean isBaseFeature) {
    GradleAndroidModel model = GradleAndroidModel.get(module);
    assertThat(module).isNotNull();
    IdeAndroidProjectType projectType = model.getAndroidProject().getProjectType();
    assertThat(projectType).named("Module type").isEqualTo(moduleType);
    assertThat(model.isBaseSplit()).named("IsBaseSplit").isEqualTo(isBaseFeature);

    IdeDependencies dependencies = model.getSelectedMainCompileDependencies();
    List<String> libraries =
      ContainerUtil.map(dependencies.getModuleDependencies(), IdeDependencyKt::getProjectPath);
    assertThat(libraries).containsExactlyElementsIn(expectedDependencies);
  }
}
