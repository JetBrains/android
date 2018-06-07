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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class BuildTypesTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verifies addition of new build types
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 532c9d6c-18eb-49ea-99b0-be64dbecd5e1
   * <pre>
   *   Test Steps:
   *   1. Open the project structure dialog
   *   2. Select a module
   *   3. Click the Build Types tab
   *   4. Create new Build Type and name it newBuildType
   *   5. Set properties debuggable and version Name Suffix to valid values
   *   Verification:
   *   1. Open the build.gradle file for that module and verify
   *   entries for build types to contain new build type added.
   *   2. Verify the properties in the file match the values
   *   set in the project structure flavor dialog
   * </pre>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void addNewBuildType() throws Exception {
    String gradleFileContents = guiTest.importSimpleLocalApplication()
      .openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .selectConfigurable("app")
      .selectBuildTypesTab()
      .setName("newBuildType")
      .setDebuggable("true")
      .setVersionNameSuffix("suffix")
      .clickOk()
      .getEditor()
      .open("/app/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleFileContents)
      .containsMatch("newBuildType \\{\\n[\\s]*debuggable true\\n[\\s]*versionNameSuffix 'suffix'\\n[\\s]*\\}");
  }

  /**
   * Verifies that an existing build type can be updated.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 50840081-9584-4e66-9333-6a50902b5853
   * <pre>
   *   Test Steps:
   *   1. Open the project structure dialog
   *   2. Select a module
   *   3. Click the Build Types tab
   *   4. Select Debug or Release and modify some settings.
   *   Verification:
   *   1. Build type selection in gradle build file is updated with the changes.
   * </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void editBuildType() throws Exception {
    String gradleFileContents = guiTest.importSimpleLocalApplication()
      .openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .selectConfigurable("app")
      .selectBuildTypesTab()
      .selectBuildType("release")
      .setDebuggable("true")
      .setVersionNameSuffix("suffix")
      .clickOk()
      .getEditor()
      .open("/app/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleFileContents).containsMatch("release \\{\n[^\\}]* debuggable true\n");
    assertThat(gradleFileContents).containsMatch("release \\{\n[^\\}]* versionNameSuffix 'suffix'\n");
  }
}
