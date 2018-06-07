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
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.FlavorsTabFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class CreateNewFlavorsTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String FLAVOR1 = "flavor1";
  private static final String FLAVOR2 = "flavor2";
  private static final String DIMEN_NAME = "demo";
  private static final String GRADLE_FILE_PATH = "/app/build.gradle";

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
   *   6. Gradle sync will fail
   *   7. Open /app/build.gradle file
   *   8. Add "flavorDimensions("demo")" to the android block,
   *      and add "dimension "demo"" to flavor1 and flavor2 blocks
   *   9. Sync the gradle, it should be successful
   *   Verification:
   *   1. Open the build.gradle file for that module and verify
   *   entries in productFlavors for "flavor1" and "flavor2"
   *   2. Verify the properties in the file match the values
   *   set in the project structure flavor dialog
   * </pre>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void createNewFlavors() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    FlavorsTabFixture flavorsTabFixture =
      ideFrameFixture.openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .selectConfigurable("app")
      .selectFlavorsTab()
      .clickAddButton()
      .setFlavorName(FLAVOR1)
      .setMinSdkVersion("API 24: Android 7.0 (Nougat)")
      .setTargetSdkVersion("API 24: Android 7.0 (Nougat)")
      .clickAddButton()
      .setFlavorName(FLAVOR2)
      .setVersionCode("2")
      .setVersionName("2.3");

    try {
      flavorsTabFixture.clickOk();
    } catch (RuntimeException e) {
      // Expected to fail here.
      // com.android.tools.idea.tests.gui.framework.fixture.gradle
      // .GradleProjectEventListener.syncFailed(GradleProjectEventListener.java:65)
    }
    ideFrameFixture.waitForGradleProjectSyncToFail();

    String dimenDemo = "dimension \"" + DIMEN_NAME + "\"";

    ideFrameFixture.getEditor()
      .open(GRADLE_FILE_PATH)
      .moveBetween("", "productFlavors {")
      .enterText("flavorDimensions(\"" + DIMEN_NAME + "\")\n")
      .moveBetween(FLAVOR2 + " {", "")
      .enterText("\n" + dimenDemo)
      .moveBetween(FLAVOR1 + " {", "")
      .enterText("\n" + dimenDemo)
      .invokeAction(EditorFixture.EditorAction.SAVE)
      .close();

    ideFrameFixture.requestProjectSync().waitForGradleProjectSyncToFinish(Wait.seconds(30));

    String gradleFileContents = ideFrameFixture.getEditor().open(GRADLE_FILE_PATH).getCurrentFileContents();
    String flavor1 = FLAVOR1 + " {\n            " + dimenDemo + "\n            minSdkVersion 24\n            targetSdkVersion 24\n        }";
    String flavor2 = FLAVOR2 + " {\n            " + dimenDemo + "\n            versionCode 2\n            versionName '2.3'\n        }";
    assertThat(gradleFileContents).contains(flavor1);
    assertThat(gradleFileContents).contains(flavor2);
  }
}
