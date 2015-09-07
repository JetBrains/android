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

import com.android.tools.idea.gradle.dsl.parser.GradleBuildModel;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.GradleBuildModel.parseBuildFile;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

class GradleBuildFileFixture {
  @NonNls private static final String APP_BUILD_GRADLE_RELATIVE_PATH = "app/build.gradle";

  @NotNull private final IdeFrameFixture myProjectFrame;

  GradleBuildFileFixture(@NotNull IdeFrameFixture projectFrame) {
    myProjectFrame = projectFrame;
  }

  @NotNull
  GradleBuildModel openAndParseAppBuildFile() {
    return openAndParseBuildFile(APP_BUILD_GRADLE_RELATIVE_PATH);
  }

  @NotNull
  GradleBuildModel openAndParseBuildFile(@NotNull String relativePath) {
    myProjectFrame.getEditor().open(relativePath).getCurrentFile();
    final VirtualFile buildFile = myProjectFrame.findFileByRelativePath(relativePath, true);
    final Project project = myProjectFrame.getProject();
    GradleBuildModel buildModel = execute(new GuiQuery<GradleBuildModel>() {
      @Override
      @NotNull
      protected GradleBuildModel executeInEDT() throws Throwable {
        return parseBuildFile(buildFile, project);
      }
    });
    assertNotNull(buildModel);
    return buildModel;
  }
}
