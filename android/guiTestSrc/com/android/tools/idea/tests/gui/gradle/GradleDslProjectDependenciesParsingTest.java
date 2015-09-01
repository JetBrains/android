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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.dsl.parser.DependenciesElement;
import com.android.tools.idea.gradle.dsl.parser.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.parser.ProjectDependencyElement;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.command.WriteCommandAction;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.*;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradleDslProjectDependenciesParsingTest extends GradleDslTestCase {

  @Test @IdeGuiTest
  public void testParsingProjectDependencyInDifferentForm() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("ModuleDependencies");
    GradleBuildModel buildModel = openAndParseAppBuildFile(projectFrame);

    // compile project(":javalib1")
    ProjectDependencyElement javalib1 = findProjectDependency(buildModel, "javalib1");
    // compile project(path: ":javalib2")
    ProjectDependencyElement javalib2 = findProjectDependency(buildModel, "javalib2");
    // compile project(path: ":androidlib1", configuration: "flavor1Release");
    ProjectDependencyElement androidlib1 = findProjectDependency(buildModel, "androidlib1");

    assertNotNull(javalib1);
    assertNotNull(javalib2);
    assertNotNull(androidlib1);
  }

  @Test @IdeGuiTest
  public void testParsingProjectDependencyInDifferentConfiguration() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiAndroidModule");
    GradleBuildModel buildModel = openAndParseAppBuildFile(projectFrame);

    // debugCompile project(path: ':library', configuration: 'debug')
    ProjectDependencyElement debug = findProjectDependency(buildModel, "library", "debugCompile");
    // releaseCompile project(path: ':library', configuration: 'release')
    ProjectDependencyElement release = findProjectDependency(buildModel, "library", "releaseCompile");

    assertNotNull(debug);
    assertNotNull(release);

    assertEquals("debug", debug.getTargetConfigurationName());
    assertEquals("release", release.getTargetConfigurationName());
  }

  @Test @IdeGuiTest
  public void testRenameProjectDependency() throws IOException {
    final IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("ModuleDependencies");
    final GradleBuildModel buildModel = openAndParseAppBuildFile(projectFrame);

    final ProjectDependencyElement dep = findProjectDependency(buildModel, "androidlib2");
    assertNotNull(dep);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        WriteCommandAction.runWriteCommandAction(projectFrame.getProject(), new Runnable() {
          @Override
          public void run() {
            dep.setName("renamed");
          }
        });
        buildModel.reparse();
      }
    });

    assertEquals("renamed", dep.getName());
    assertNull(findProjectDependency(buildModel, "androidlib2"));
    assertNotNull(findProjectDependency(buildModel, "renamed"));
  }


  @Nullable
  private static ProjectDependencyElement findProjectDependency(@NotNull GradleBuildModel buildModel,
                                                                @NotNull String projectName) {
    return findProjectDependency(buildModel, projectName, null);
  }

  @Nullable
  private static ProjectDependencyElement findProjectDependency(@NotNull GradleBuildModel buildModel,
                                                                @NotNull String projectName,
                                                                @Nullable String configurationName) {
    for (DependenciesElement dependenciesElement : buildModel.getDependenciesBlocksView()) {
      for (ProjectDependencyElement element : dependenciesElement.getProjectDependenciesView()) {
        if (projectName.equals(element.getName()) &&
            (configurationName == null || configurationName.equals(element.getConfigurationName()))) {
          return element;
        }
      }
    }
    return null;
  }
}
