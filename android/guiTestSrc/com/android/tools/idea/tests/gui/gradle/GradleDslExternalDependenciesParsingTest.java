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

import com.android.tools.idea.gradle.dsl.dependencies.external.ExternalDependency;
import com.android.tools.idea.gradle.dsl.dependencies.external.ExternalDependencyTest.ExpectedExternalDependency;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.fest.assertions.Assertions.assertThat;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradleDslExternalDependenciesParsingTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testParseExternalDependenciesWithCompactNotation() throws IOException {
    myProjectFrame = importSimpleApplication();

    GradleBuildModelFixture buildModel = myProjectFrame.parseBuildFileForModule("app", true);

    List<ExternalDependency> dependencies = buildModel.getTarget().dependencies().external();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedExternalDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));
  }

  @Test @IdeGuiTest
  public void testSetVersionOnExternalDependencyWithCompactNotation() throws IOException {
    myProjectFrame = importSimpleApplication();
    final GradleBuildModelFixture buildModel = myProjectFrame.parseBuildFileForModule("app", true);

    List<ExternalDependency> dependencies = buildModel.getTarget().dependencies().external();
    assertThat(dependencies).hasSize(2);

    final ExternalDependency appCompat = dependencies.get(0);

    ExpectedExternalDependency expected = new ExpectedExternalDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(appCompat);

    appCompat.version("1.2.3");
    buildModel.applyChanges();

    dependencies = buildModel.getTarget().dependencies().external();
    assertThat(dependencies).hasSize(2);

    expected.configurationName = "compile";
    expected.group = "com.android.support";
    expected.name = "appcompat-v7";
    expected.version = "1.2.3";
    expected.assertMatches(dependencies.get(0));
  }
}
