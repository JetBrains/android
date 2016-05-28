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
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

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
    // This isn't really a UI test; it's not simulating user interaction.
    DependenciesModel dependencies = guiTest.importSimpleApplication()
      .parseBuildFileForModule("app", true)
      .getTarget()  // gets GradleBuildModel from GradleBuildModelFixture (gross)
      .dependencies();
    assertThat(compactNotation(dependencies)).containsExactly(
      "com.android.support:appcompat-v7:23.1.1",
      "com.google.guava:guava:18.0",
      "com.android.support.constraint:constraint-layout:+",
      "junit:junit:4.+");
  }

  @Test
  public void testSetVersionOnExternalDependencyWithCompactNotation() throws IOException {
    // This isn't really a UI test; it's not simulating user interaction.
    GradleBuildModelFixture buildModel = guiTest.importSimpleApplication()
      .parseBuildFileForModule("app", true);
    DependenciesModel dependencies = buildModel.getTarget().dependencies();
    dependencies.artifacts().get(0).setVersion("1.2.3");  // change appcompat-v7 from 23.1.1 to 1.2.3
    buildModel.applyChanges();
    assertThat(compactNotation(dependencies)).contains("com.android.support:appcompat-v7:1.2.3");
  }

  private static List<String> compactNotation(DependenciesModel dependencies) {
    return dependencies.artifacts().stream().map((dep) -> dep.compactNotation().value()).collect(Collectors.toList());
  }
}
