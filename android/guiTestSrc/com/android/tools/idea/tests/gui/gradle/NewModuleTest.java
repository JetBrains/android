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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewModuleDialogFixture;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

/**
 * Tests, that newly generated modules work, even with older gradle plugin versions.
 */
@RunIn(TestGroup.TEST_SUPPORT)
@RunWith(GuiTestRunner.class)
public class NewModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testNewModuleOldGradle() throws Exception {
    guiTest.importSimpleApplication();
    // That's the oldest combination we support:
    guiTest.ideFrame().updateAndroidGradlePluginVersion("1.0.0");
    guiTest.ideFrame().updateGradleWrapperVersion("2.2.1");

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/build.gradle");
    editor.moveBetween("use", "Library");
    editor.invokeAction(EditorFixture.EditorAction.DELETE_LINE);

    guiTest.ideFrame().requestProjectSync();
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    guiTest.ideFrame()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .select("Android Library")
      .clickNext()
      .clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    // Sync worked, so that's good. Just make sure we didn't generate "testCompile" in build.gradle
    editor.open("mylibrary/build.gradle");
    assertThat(editor.getCurrentFileContents()).doesNotContain("testCompile");

    VirtualFile projectDir = guiTest.ideFrame().getProject().getBaseDir();
    assertNotNull(projectDir.findFileByRelativePath("mylibrary/src/main"));
    assertNull(projectDir.findFileByRelativePath("mylibrary/src/test"));
  }
}
