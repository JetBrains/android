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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyTest.ExpectedModuleDependency;
import com.intellij.openapi.command.WriteCommandAction;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static junit.framework.Assert.fail;
import static org.fest.swing.edt.GuiActionRunner.execute;

public class GradleBuildModelFixture {
  @NotNull private final GradleBuildModel myTarget;

  public GradleBuildModelFixture(@NotNull GradleBuildModel target) {
    myTarget = target;
  }

  @NotNull
  public GradleBuildModel getTarget() {
    return myTarget;
  }

  public void requireDependency(@NotNull String configurationName, @NotNull ArtifactDependencySpec expected) {
    DependenciesModel dependenciesModel = myTarget.dependencies();
    for (ArtifactDependencyModel dependency : dependenciesModel.artifacts()) {
      ArtifactDependencySpec actual = dependency.getSpec();
      if (configurationName.equals(dependency.configurationName()) && expected.equals(actual)) {
        return;
      }
    }
    fail("Failed to find dependency '" + expected.compactNotation() + "'");
  }

  public void requireDependency(@NotNull ExpectedModuleDependency expected) {
    DependenciesModel dependenciesModel = myTarget.dependencies();
    for (final ModuleDependencyModel dependency : dependenciesModel.modules()) {
      String path = execute(new GuiQuery<String>() {
        @Nullable
        @Override
        protected String executeInEDT() throws Throwable {
          return dependency.path();
        }
      });
      if (expected.path.equals(path) && expected.configurationName.equals(dependency.configurationName())) {
        return;
      }
    }
    fail("Failed to find dependency '" + expected.path + "'");
  }

  public void applyChanges() {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        WriteCommandAction.runWriteCommandAction(myTarget.getProject(), new Runnable() {
          @Override
          public void run() {
            myTarget.applyChanges();
          }
        });
      }
    });
  }

  public void reparse() {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        myTarget.reparse();
      }
    });
  }
}
