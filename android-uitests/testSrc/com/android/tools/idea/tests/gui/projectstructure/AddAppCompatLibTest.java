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
package com.android.tools.idea.tests.gui.projectstructure;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;

@RunWith(GuiTestRemoteRunner.class)
public class AddAppCompatLibTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  @Before
  public void setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(false);
  }

  @After
  public void tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride();
  }

  /***
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: eb05dfdd-f751-47f1-820c-e9f71896dab4
   * <pre>
   *   Verifies transitive dependencies with Android Library and third party library are resolved in a gradle file.
   *   Test Steps
   *   1. Import a project.
   *   2. Right click on a module from the Project Explorer (left side menu on the IDE).
   *   3. Select "Open Module Settings".
   *   4. For the selected module, click on the dependencies tab.
   *   5. Click on the "+" option and choose "Library Dependency".
   *   6. From the list of available libraries, choose AppCompat (com.android.support:appcompat-v7).
   *   7. Click OK.
   *   Verification
   *   1. Verify that the library dependency line has been added to the module's build.gradle
   *   The line "implementation 'com.android.support:appcompat-v7:xxx'" will be added under Dependencies section in the module build.gradle file.
   * </pre>
   */
  @RunIn(TestGroup.FAT)
  @Test
  public void addAppCompatLib() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProject("NoAppCompatLibApp");
    ideFrame.waitForGradleProjectSyncToFinish();

   ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath(RIGHT_BUTTON, "NoAppCompatLibApp", "app");

    ideFrame.invokeMenuPath("Open Module Settings");

    ProjectStructureDialogFixture.find(ideFrame)
      .selectDependenciesTab()
      .addLibraryDependency("com.android.support:appcompat-v7")
      .clickOk();

    ideFrame.waitForGradleProjectSyncToFinish();

    EditorFixture editor = ideFrame.getEditor();
    editor.open("app/build.gradle", EditorFixture.Tab.EDITOR, Wait.seconds(30));
    String contents = editor.getCurrentFileContents();
    // Here, only verify the main part of the library, the exact revision number will vary.
    // As default, it's implementation type.
    assertThat(contents.contains("implementation 'com.android.support:appcompat-v7")).isTrue();
  }
}
