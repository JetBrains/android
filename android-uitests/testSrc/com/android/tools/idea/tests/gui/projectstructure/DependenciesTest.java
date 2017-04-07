/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.projectstructure;

import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Paths;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class DependenciesTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void createNewFlavors() throws Exception {
    String projPath = guiTest.importSimpleApplication()
      .getProjectPath()
      .getPath();

    File jarFile = Paths.get(projPath, "app", "libs", "local.jar").toFile();
    assertTrue(FileOpUtils.create().mkdirs(jarFile.getParentFile()));

    FileOpUtils.create().copyFile(new File(GuiTests.getTestDataDir() + "/LocalJarsAsModules/localJarAsModule/local.jar"), jarFile);

    String gradleFileContents = guiTest.ideFrame()
      .openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .selectConfigurable("app")
      .selectDependenciesTab()
      .addJarDependency(jarFile)
      .clickOk()
      .getEditor()
      .open("/app/build.gradle")
      .getCurrentFileContents();

    assertThat(gradleFileContents).contains("compile files('libs/local.jar')");
  }
}
