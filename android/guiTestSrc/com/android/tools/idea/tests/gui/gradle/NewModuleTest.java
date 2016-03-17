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

import com.android.tools.idea.npw.ModuleTemplate;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.ui.ASGallery;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.DialogFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButtonWhenEnabled;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.*;

/**
 * Tests, that newly generated modules work, even with older gradle plugin versions.
 */
@RunIn(TestGroup.TEST_SUPPORT)
@RunWith(GuiTestRunner.class)
public class NewModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testNewModuleOldGradle() throws Exception {
    guiTest.importSimpleApplication();
    // That's the oldest combination we support:
    guiTest.ideFrame().updateAndroidGradlePluginVersion("1.0.0");
    guiTest.ideFrame().updateGradleWrapperVersion("2.2.1");

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/build.gradle");
    editor.moveTo(editor.findOffset("use", "Library", false));
    editor.invokeAction(EditorFixture.EditorAction.DELETE_LINE);

    guiTest.ideFrame().requestProjectSync();
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module...");
    Dialog dialog = guiTest.robot().finder().find(DialogMatcher.withTitle("Create New Module"));
    DialogFixture dialogFixture = new DialogFixture(guiTest.robot(), dialog);

    selectItemInGallery(dialog, 1, "Android Library");
    findAndClickButton(dialogFixture, "Next");
    guiTest.waitForBackgroundTasks();
    findAndClickButtonWhenEnabled(dialogFixture, "Finish");

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    // Sync worked, so that's good. Just make sure we didn't generate "testCompile" in build.gradle
    editor.open("mylibrary/build.gradle");
    assertEquals(-1, editor.findOffset("test", "Compile", true));

    VirtualFile projectDir = guiTest.ideFrame().getProject().getBaseDir();
    assertNotNull(projectDir.findFileByRelativePath("mylibrary/src/main"));
    assertNull(projectDir.findFileByRelativePath("mylibrary/src/test"));
  }

  private void selectItemInGallery(@NotNull Dialog dialog,
                                   final int selectedIndex,
                                   @NotNull final String expectedName) {
    final ASGallery gallery = guiTest.robot().finder().findByType(dialog, ASGallery.class);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        gallery.setSelectedIndex(selectedIndex);
        ModuleTemplate selected = (ModuleTemplate)gallery.getSelectedElement();
        assertNotNull(selected);
        assertEquals(expectedName, selected.getName());
      }
    });
  }
}
