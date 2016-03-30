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
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GradleDslModuleDependenciesParsingTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Test
  public void testParsingProjectDependencies() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("ModuleDependencies");

    GradleBuildModelFixture buildModel = guiTest.ideFrame().parseBuildFileForModule("app", true);

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

  @Test
  public void testRenameProjectDependency() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("ModuleDependencies");

    GradleBuildModelFixture buildModel = guiTest.ideFrame().parseBuildFileForModule("app", true);

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
