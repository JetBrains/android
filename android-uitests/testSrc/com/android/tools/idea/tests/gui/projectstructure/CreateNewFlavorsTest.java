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
public class CreateNewFlavorsTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verifies addition of new flavors from project structure dialog.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 8eab6486-073b-4b60-b979-470fb3163920
   * <pre>
   *   Test Steps:
   *   1. Open the project structure dialog
   *   2. Select a module
   *   3. Click the flavors tab
   *   4. Create two new Flavors named "flavor1" and "flavor2"
   *   5. Set some properties of the flavors
   *   Verification:
   *   1. Open the build.gradle file for that module and verify
   *   entries in productFlavors for "flavor1" and "flavor2"
   *   2. Verify the properties in the file match the values
   *   set in the project structure flavor dialog
   * </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37478645
  @Test
  public void createNewFlavors() throws Exception {
    String gradleFileContents = guiTest.importSimpleApplication()
      .openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .selectConfigurable("app")
      .selectFlavorsTab()
      .clickAddButton()
      .setFlavorName("flavor1")
      .setMinSdkVersion("API 24: Android 7.0 (Nougat)")
      .setTargetSdkVersion("API 24: Android 7.0 (Nougat)")
      .clickAddButton()
      .setFlavorName("flavor2")
      .setVersionCode("2")
      .setVersionName("2.3")
      .clickOk()
      .getEditor()
      .open("/app/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleFileContents).contains("flavor1 {\n            minSdkVersion 24\n            targetSdkVersion 24\n        }");
    assertThat(gradleFileContents).contains("flavor2 {\n            versionCode 2\n            versionName '2.3'\n        }");
  }
}
