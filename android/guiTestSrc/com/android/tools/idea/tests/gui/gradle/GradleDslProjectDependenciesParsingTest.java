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
import com.android.tools.idea.gradle.dsl.parser.ProjectDependencyElement;
import com.android.tools.idea.gradle.dsl.parser.ProjectDependencyElementTest.ExpectedProjectDependency;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import com.intellij.openapi.command.WriteCommandAction;
import org.fest.swing.edt.GuiTask;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradleDslProjectDependenciesParsingTest extends GuiTestCase {

  @Test @IdeGuiTest
  public void testParsingProjectDependencies() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("ModuleDependencies");

    GradleBuildModelFixture buildModel = projectFrame.openAndParseBuildFileForModule("app");

    List<DependenciesElement> dependenciesBlocks = buildModel.getTarget().getDependenciesBlocks();
    assertThat(dependenciesBlocks).hasSize(1);

    DependenciesElement dependenciesBlock = dependenciesBlocks.get(0);
    List<ProjectDependencyElement> dependencies = dependenciesBlock.getProjectDependencies();
    assertThat(dependencies).hasSize(4);

    ExpectedProjectDependency expected = new ExpectedProjectDependency();
    expected.configurationName = "compile";
    expected.path = ":androidlib1";
    expected.configuration = "flavor1Release";
    expected.assertMatches(dependencies.get(0));

    expected.reset();

    expected.configurationName = "compile";
    expected.path = ":androidlib2";
    expected.configuration = "flavor1Release";
    expected.assertMatches(dependencies.get(1));

    expected.reset();

    expected.configurationName = "compile";
    expected.path = ":javalib1";
    expected.assertMatches(dependencies.get(2));

    expected.reset();

    expected.configurationName = "compile";
    expected.path = ":javalib2";
    expected.assertMatches(dependencies.get(3));
  }

  @Test @IdeGuiTest
  public void testRenameProjectDependency() throws IOException {
    final IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("ModuleDependencies");

    final GradleBuildModelFixture buildModel = projectFrame.openAndParseBuildFileForModule("app");

    List<DependenciesElement> dependenciesBlocks = buildModel.getTarget().getDependenciesBlocks();
    DependenciesElement dependenciesBlock = dependenciesBlocks.get(0);
    List<ProjectDependencyElement> dependencies = dependenciesBlock.getProjectDependencies();

    final ProjectDependencyElement dependency = dependencies.get(0);
    assertNotNull(dependency);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        WriteCommandAction.runWriteCommandAction(projectFrame.getProject(), new Runnable() {
          @Override
          public void run() {
            dependency.setName("renamed");
          }
        });
        buildModel.getTarget().reparse();
      }
    });

    dependenciesBlocks = buildModel.getTarget().getDependenciesBlocks();
    assertThat(dependenciesBlocks).hasSize(1);

    dependenciesBlock = dependenciesBlocks.get(0);
    dependencies = dependenciesBlock.getProjectDependencies();
    assertThat(dependencies).hasSize(4);

    ExpectedProjectDependency expected = new ExpectedProjectDependency();
    expected.configurationName = "compile";
    expected.path = ":renamed";
    expected.configuration = "flavor1Release";
    expected.assertMatches(dependencies.get(0));
  }
}
