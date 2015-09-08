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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import com.android.tools.idea.gradle.dsl.parser.DependenciesElement;
import com.android.tools.idea.gradle.dsl.parser.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.parser.ProjectDependencyElement;
import com.android.tools.idea.gradle.dsl.parser.ProjectDependencyElementTest.ExpectedProjectDependency;
import org.jetbrains.annotations.NotNull;

import static junit.framework.Assert.fail;

public class GradleBuildModelFixture {
  @NotNull private final GradleBuildModel myTarget;

  public GradleBuildModelFixture(@NotNull GradleBuildModel target) {
    myTarget = target;
  }

  @NotNull
  public GradleBuildModel getTarget() {
    return myTarget;
  }

  public void requireDependency(@NotNull ExpectedProjectDependency expected) {
    for (DependenciesElement dependenciesElement : myTarget.getDependenciesBlocks()) {
      for (ProjectDependencyElement element : dependenciesElement.getProjectDependencies()) {
        if (expected.path.equals(element.getPath()) && (expected.configurationName.equals(element.getConfigurationName()))) {
          return;
        }
      }
    }
    fail("Failed to find dependency '" + expected.path + "'");
  }
}
