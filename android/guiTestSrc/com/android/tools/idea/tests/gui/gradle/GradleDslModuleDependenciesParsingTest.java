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

import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyTest.ExpectedModuleDependency;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.fest.assertions.Assertions.assertThat;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradleDslModuleDependenciesParsingTest extends GuiTestCase {

  @Test @IdeGuiTest
  public void testParsingProjectDependencies() throws IOException {
    myProjectFrame = importProjectAndWaitForProjectSyncToFinish("ModuleDependencies");

    GradleBuildModelFixture buildModel = myProjectFrame.parseBuildFileForModule("app", true);

    List<ModuleDependencyModel> dependencies = buildModel.getTarget().dependencies().modules();
    assertThat(dependencies).hasSize(4);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
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
    myProjectFrame = importProjectAndWaitForProjectSyncToFinish("ModuleDependencies");

    GradleBuildModelFixture buildModel = myProjectFrame.parseBuildFileForModule("app", true);

    List<ModuleDependencyModel> dependencies = buildModel.getTarget().dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("renamed");

    buildModel.applyChanges();

    dependencies = buildModel.getTarget().dependencies().modules();
    assertThat(dependencies).hasSize(4);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":renamed";
    expected.configuration = "flavor1Release";
    expected.assertMatches(dependencies.get(0));
  }
}
