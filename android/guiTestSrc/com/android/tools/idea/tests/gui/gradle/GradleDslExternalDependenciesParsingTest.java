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
import com.android.tools.idea.gradle.dsl.parser.ExternalDependencyElement;
import com.android.tools.idea.gradle.dsl.parser.ExternalDependencyElementTest.ExpectedExternalDependency;
import com.android.tools.idea.gradle.dsl.parser.GradleBuildModel;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.command.WriteCommandAction;
import org.fest.swing.edt.GuiTask;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradleDslExternalDependenciesParsingTest extends GradleDslTestCase {
  @Test @IdeGuiTest
  public void testParseExternalDependenciesWithCompactNotation() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    GradleBuildModel buildModel = openAndParseAppBuildFile(projectFrame);

    List<DependenciesElement> dependenciesBlocks = buildModel.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);

    DependenciesElement dependenciesBlock = dependenciesBlocks.get(0);
    List<ExternalDependencyElement> dependencies = dependenciesBlock.getExternalDependenciesView();
    assertThat(dependencies).hasSize(2);

    ExpectedExternalDependency expected = new ExpectedExternalDependency();
    expected.configurationName = "compile";
    expected.group = "com.android.support";
    expected.name = "appcompat-v7";
    expected.version = "22.1.1";
    expected.assertMatches(dependencies.get(0));

    expected.reset();

    expected.configurationName = "compile";
    expected.group = "com.google.guava";
    expected.name = "guava";
    expected.version = "18.0";
    expected.assertMatches(dependencies.get(1));
  }

  @Test @IdeGuiTest
  public void testSetVersionOnExternalDependencyWithCompactNotation() throws IOException {
    final IdeFrameFixture projectFrame = importSimpleApplication();
    final GradleBuildModel buildModel = openAndParseAppBuildFile(projectFrame);

    List<DependenciesElement> dependenciesBlocks = buildModel.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);
    DependenciesElement dependenciesBlock = dependenciesBlocks.get(0);

    List<ExternalDependencyElement> dependencies = dependenciesBlock.getExternalDependenciesView();
    assertThat(dependencies).hasSize(2);

    final ExternalDependencyElement appCompat = dependencies.get(0);

    ExpectedExternalDependency expected = new ExpectedExternalDependency();
    expected.configurationName = "compile";
    expected.group = "com.android.support";
    expected.name = "appcompat-v7";
    expected.version = "22.1.1";
    expected.assertMatches(appCompat);

    expected.reset();

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        WriteCommandAction.runWriteCommandAction(projectFrame.getProject(), new Runnable() {
          @Override
          public void run() {
            appCompat.setVersion("1.2.3");
          }
        });
        buildModel.reparse();
      }
    });

    dependenciesBlocks = buildModel.getDependenciesBlocksView();
    assertThat(dependenciesBlocks).hasSize(1);
    dependenciesBlock = dependenciesBlocks.get(0);

    dependencies = dependenciesBlock.getExternalDependenciesView();
    assertThat(dependencies).hasSize(2);

    expected.configurationName = "compile";
    expected.group = "com.android.support";
    expected.name = "appcompat-v7";
    expected.version = "1.2.3";
    expected.assertMatches(dependencies.get(0));
  }
}
