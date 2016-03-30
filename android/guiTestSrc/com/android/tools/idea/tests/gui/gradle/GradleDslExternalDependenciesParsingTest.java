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

import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyTest.ExpectedArtifactDependency;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
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

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.assertNotNull;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GradleDslExternalDependenciesParsingTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Test
  public void testParseExternalDependenciesWithCompactNotation() throws IOException {
    guiTest.importSimpleApplication();

    GradleBuildModelFixture buildModel = guiTest.ideFrame().parseBuildFileForModule("app", true);

    DependenciesModel dependenciesModel = buildModel.getTarget().dependencies();
    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "23.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testSetVersionOnExternalDependencyWithCompactNotation() throws IOException {
    guiTest.importSimpleApplication();
    final GradleBuildModelFixture buildModel = guiTest.ideFrame().parseBuildFileForModule("app", true);

    DependenciesModel dependenciesModel = buildModel.getTarget().dependencies();
    assertNotNull(dependenciesModel);
    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    final ArtifactDependencyModel appCompat = dependencies.get(0);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "23.1.1");
    expected.assertMatches(appCompat);

    appCompat.setVersion("1.2.3");
    buildModel.applyChanges();

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    expected.configurationName = "compile";
    expected.group = "com.android.support";
    expected.name = "appcompat-v7";
    expected.version = "1.2.3";
    expected.assertMatches(dependencies.get(0));
  }
}
