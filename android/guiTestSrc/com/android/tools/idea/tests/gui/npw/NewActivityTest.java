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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.intellij.openapi.util.text.StringUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/*
  TODO: Missing Tests
- Changing "Activity Name" causes "Title" and "Layout Name" to change
- Once you change something, you can right-click on it and choose "Restore default value"
- Change "Activity Name", then "Title", the "Activity Name" again. "Title" should not update since it's been manually modified.
  However, "Restore default value" updates it correctly
- Create a project that already has another activity. Make sure you can set it as the "Hierarchical Parent" in the new activity that we're
  creating
*/

@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
public class NewActivityTest {
  private static final String PROVIDED_ACTIVITY = "app/src/main/java/google/simpleapplication/MyActivity.java";
  private static final String PROVIDED_MANIFEST = "app/src/main/AndroidManifest.xml";

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private EditorFixture myEditor;

  @Before
  public void setUp() throws IOException {
    guiTest.importSimpleApplication();
    myEditor = guiTest.ideFrame().getEditor();
    myEditor.open(PROVIDED_ACTIVITY);

    guiTest.ideFrame().getProjectView().assertFilesExist(
      "settings.gradle",
      "app",
      PROVIDED_ACTIVITY,
      PROVIDED_MANIFEST
    );
  }

  private NewActivityWizardFixture invokeNewActivityDialog() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Activity", "Basic Activity");
    return NewActivityWizardFixture.find(guiTest.robot());
  }

  @Test
  public void createDefaultActivity() {
    invokeNewActivityDialog().clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    guiTest.ideFrame().getProjectView().assertFilesExist(
      "app/src/main/java/google/simpleapplication/MainActivity.java",
      "app/src/main/res/layout/activity_main.xml"
    );

    myEditor.open(PROVIDED_MANIFEST);

    String text =  myEditor.getCurrentFileContents(false);
    assertNotNull(text);
    assertEquals(StringUtil.getOccurrenceCount(text, "android:name=\".MainActivity\""), 1);
    assertEquals(StringUtil.getOccurrenceCount(text, "@string/title_activity_main"), 1);
    assertEquals(StringUtil.getOccurrenceCount(text, "android.intent.category.LAUNCHER"), 1);
  }

  @Test
  public void createLauncherActivity() {
    NewActivityWizardFixture dialog = invokeNewActivityDialog();
    dialog.getConfigureActivityStep().selectLauncherActivity();
    dialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    myEditor.open(PROVIDED_MANIFEST);

    String text =  myEditor.getCurrentFileContents(false);
    assertNotNull(text);
    assertEquals(StringUtil.getOccurrenceCount(text, "android.intent.category.LAUNCHER"), 2);
  }

  @Test
  public void createActivityWithFragment() {
    NewActivityWizardFixture dialog = invokeNewActivityDialog();
    dialog.getConfigureActivityStep().selectUseFragment();
    dialog.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    guiTest.ideFrame().getProjectView().assertFilesExist(
      "app/src/main/java/google/simpleapplication/MainActivity.java",
      "app/src/main/res/layout/fragment_main.xml"
    );
  }
}
