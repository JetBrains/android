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

import com.android.tools.idea.gradle.dsl.parser.ExternalDependency;
import com.android.tools.idea.gradle.dsl.parser.ExternalDependencyTest.ExpectedExternalDependency;
import com.android.tools.idea.gradle.dsl.parser.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.parser.ModuleDependency;
import com.android.tools.idea.gradle.dsl.parser.ModuleDependencyTest.ExpectedModuleDependency;
import com.intellij.openapi.command.WriteCommandAction;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

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

  public void requireDependency(@NotNull ExpectedExternalDependency expected) {
    for (ExternalDependency dependency : myTarget.getDependencies().getExternal()) {
      if (expected.matches(dependency)) {
        return;
      }
    }
    fail("Failed to find dependency '" + expected.name + "'");
  }

  public void requireDependency(@NotNull ExpectedModuleDependency expected) {
    for (ModuleDependency dependency : myTarget.getDependencies().getToModules()) {
      if (expected.path.equals(dependency.getPath()) && expected.configurationName.equals(dependency.getConfigurationName())) {
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
}
