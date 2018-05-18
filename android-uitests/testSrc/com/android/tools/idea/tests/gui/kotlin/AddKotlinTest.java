/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.android.tools.idea.tests.gui.framework.fixture.ConfigureKotlinDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class AddKotlinTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  private static final String PROJECT_DIR_NAME = "LinkProjectWithKotlin";
  private static final String PACKAGE_NAME = "com.android.linkprojectwithkotlin";

  /**
   * Verifies user can link project with Kotlin.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 30f26a59-108e-49cc-bec0-586f518ea3cb
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import LinkProjectWithKotlin project, which doesn't support Kotlin
   *      and wait for project sync to finish.
   *   2. Select Project view and expand directory to Java package and click on it.
   *   3. From menu, click on "File->New->Kotlin File/Class".
   *   4. In "New Kotlin File/Class" dialog, enter the name of class
   *      and choose "Class" from the dropdown list in Kind category, and click on OK.
   *   5. Click on the configure pop up on the top right corner or bottom right corner.
   *   6. Select all modules containing Kotlin files option from "Configure kotlin pop up".
   *   7. Continue this with File,interface,enum class and verify 1 & 2
   *   Verify:
   *   1. Observe the code in Kotlin language.
   *   2. Build and deploy on the emulator.
   *   </pre>
   * <p>
   */
  @Test
  @RunIn(TestGroup.SANITY)
  public void addKotlinClass() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish(PROJECT_DIR_NAME);

    ProjectViewFixture.PaneFixture projectPane = ideFrameFixture.getProjectView().selectProjectPane();

    ProjectWithKotlinTestUtil.newKotlinFileAndClass(
      projectPane,
      ideFrameFixture,
      PROJECT_DIR_NAME,
      PACKAGE_NAME,
      ProjectWithKotlinTestUtil.CLASS_NAME,
      "Class");
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
      ideFrameFixture,
      PROJECT_DIR_NAME,
      PACKAGE_NAME,
      ProjectWithKotlinTestUtil.FILE_NAME,
      "File");
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
      ideFrameFixture,
      PROJECT_DIR_NAME,
      PACKAGE_NAME,
      ProjectWithKotlinTestUtil.INTERFACE_NAME,
      "Interface");
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
      ideFrameFixture,
      PROJECT_DIR_NAME,
      PACKAGE_NAME,
      ProjectWithKotlinTestUtil.ENUM_NAME,
      "Enum class");
    ProjectWithKotlinTestUtil.newKotlinFileAndClass(projectPane,
      ideFrameFixture,
      PROJECT_DIR_NAME,
      PACKAGE_NAME,
      ProjectWithKotlinTestUtil.OBJECT_NAME,
      "Object");

    EditorNotificationPanelFixture editorNotificationPanelFixture =
      ideFrameFixture.getEditor().awaitNotification("Kotlin not configured");
    editorNotificationPanelFixture.performActionWithoutWaitingForDisappearance("Configure");

    // As default, "All modules containing Kotlin files" option is selected for now.
    ConfigureKotlinDialogFixture.find(ideFrameFixture)
                                .clickOk();
    ideFrameFixture.requestProjectSync();

    assertThat(ideFrameFixture.invokeProjectMake().isBuildSuccessful()).isTrue();
  }
}
