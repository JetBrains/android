/*
 * Copyright (C) 2017 The Android Open Source Project
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
public class FlavorsEditingTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verify flavor editing works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: bea78054-1424-4c19-a4e5-aff2596d04e2
   * <p>
   *   <pre>
   *   Steps:
   *   1. Change properties of a non-default flavor in the project structure flavor dialog
   *   2. Click "OK" button.
   *   3. Add dimension to the flavor, and check Gradle sync is successful
   *   Verify:
   *   1. Changes made to the flavors are saved to the build.gradle file of that module.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void editFlavors() throws  Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();

    String flavor = "flavor";

    FlavorsTabFixture flavorsTabFixture =
      ideFrameFixture.openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .selectConfigurable("app")
      .selectFlavorsTab()
      .clickAddButton()
      .selectFlavor(flavor)
      .setMinSdkVersion("API 25: Android 7.1.1 (Nougat)")
      .setTargetSdkVersion("API 24: Android 7.0 (Nougat)")
      .setVersionCode("5")
      .setVersionName("2.3");

    try {
      flavorsTabFixture.clickOk();
    } catch (RuntimeException e) {
      System.out.println("Expected to fail here. Need to add dimension feature.");
    }

    String dimenName = "demo";
    String dimen = "dimension \"" + dimenName + "\"";
    ideFrameFixture.getEditor()
      .open("/app/build.gradle")
      .moveBetween("", "productFlavors {")
      .enterText("flavorDimensions(\"" + dimenName + "\")\n")
      .moveBetween(flavor + " {", "")
      .enterText("\n" + dimen)
      .invokeAction(EditorFixture.EditorAction.SAVE)
      .close();

    ideFrameFixture.requestProjectSync().waitForGradleProjectSyncToFinish(Wait.seconds(30));

    String gradleFileContents = ideFrameFixture.getEditor()
      .open("/app/build.gradle")
      .getCurrentFileContents();

    assertThat(gradleFileContents).contains(flavor + " {\n" +
                                            "            " + dimen + "\n"+
                                            "            minSdkVersion 25\n" +
                                            "            targetSdkVersion 24\n" +
                                            "            versionCode 5\n" +
                                            "            versionName '2.3'\n" +
                                            "        }");
  }
}