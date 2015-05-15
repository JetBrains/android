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

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.wizard.ASGallery;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.DialogFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.awt.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButtonWhenEnabled;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.*;

/**
 * Tests, that newly generated modules work, even with older gradle plugin versions.
 */
@BelongsToTestGroups({TestGroup.UNIT_TESTING})
public class NewModuleTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testNewModuleOldGradle() throws Exception {
    final IdeFrameFixture ideFrameFixture = importSimpleApplication();
    ideFrameFixture.updateAndroidModelVersion("1.0.0");

    ideFrameFixture.requestProjectSync();
    ideFrameFixture.waitForGradleProjectSyncToFinish();

    ideFrameFixture.invokeMenuPath("File", "New", "New Module...");
    Dialog dialog = myRobot.finder().find(DialogMatcher.withTitle("Create New Module"));
    DialogFixture dialogFixture = new DialogFixture(myRobot, dialog);

    selectItemInGallery(dialog, 1);
    findAndClickButton(dialogFixture, "Next");
    findAndClickButton(dialogFixture, "Next");
    selectItemInGallery(dialog, 0);
    ideFrameFixture.waitForBackgroundTasksToFinish();
    findAndClickButtonWhenEnabled(dialogFixture, "Finish");

    ideFrameFixture.waitForGradleProjectSyncToFinish();

    // Sync worked, so that's good. Just make sure we didn't generate "testCompile" in build.gradle
    EditorFixture editor = ideFrameFixture.getEditor();
    editor.open("mylibrary/build.gradle");
    assertEquals(-1, editor.findOffset("test", "Compile", true));

    VirtualFile projectDir = ideFrameFixture.getProject().getBaseDir();
    assertNotNull(projectDir.findFileByRelativePath("mylibrary/src/main"));
    assertNull(projectDir.findFileByRelativePath("mylibrary/src/test"));
  }

  private void selectItemInGallery(@NotNull Dialog dialog, final int selectedIndex) {
    final ASGallery gallery = myRobot.finder().findByType(dialog, ASGallery.class);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        gallery.setSelectedIndex(selectedIndex);
      }
    });
  }
}
