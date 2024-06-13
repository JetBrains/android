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
package com.android.tools.idea.tests.gui.editors.translations;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.TranslationsEditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddLocaleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  /**
   * Verifies that the Translations plugin editor can be invoked.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 3ac681b9-e127-4285-98d1-987375731ad0
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication project.
   *   2. Open Translations Editor window.
   *   3. In the Translations Editor window, click on the globe icon and select a non-English language,
   *      say German or Spanish (Verify 1).
   *   Expectations:
   *   1. A column is added for the newly added language.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.FAT_BAZEL)
  public void addNewLocale() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleApplication();
    GuiTests.waitForProjectIndexingToFinish(ideFrame.getProject());

    // Open Translations Editor window.
    EditorFixture editor = ideFrame.getEditor();
    FileSystem fileSystem = FileSystems.getDefault();
    Path myStringsXmlPath = fileSystem.getPath("app", "src", "main", "res", "values", "strings.xml");
    EditorNotificationPanelFixture notificationPanel =
      editor.open(myStringsXmlPath)
            .awaitNotification("Edit translations for all locales in the translations editor.");
    notificationPanel.performAction("Open editor");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editor.getTranslationsEditor().finishLoading();

    TranslationsEditorFixture translationsEditor = ideFrame.getEditor().getTranslationsEditor();

    String expectedLocale = "Abkhazian (ab)";
    assertThat(translationsEditor.locales()).doesNotContain(expectedLocale);
    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();
    translationsEditor.addNewLocale("ab");

    assertThat(translationsEditor.locales()).contains(expectedLocale);
  }
}