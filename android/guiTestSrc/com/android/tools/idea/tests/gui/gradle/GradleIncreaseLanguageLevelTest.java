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

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.EditorAction.UNDO;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GradleIncreaseLanguageLevelTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final ScreenshotsDuringTest screenshotsDuringTest = new ScreenshotsDuringTest();

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Test
  public void testIncreaseLanguageLevelForJava() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiModule");
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("library2/src/main/java/com/example/MyClass.java");
    editor.moveTo(editor.findOffset("MyClass {^"));

    useJava7FeatureAndIncreaseLanguageLevel(editor);
  }

  @Test
  public void testIncreaseLanguageLevelForAndroid() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiModule");
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("library/src/androidTest/java/com/android/library/ApplicationTest.java");
    editor.moveTo(editor.findOffset("super(Application.class);^"));

    useJava7FeatureAndIncreaseLanguageLevel(editor);
  }

  private void useJava7FeatureAndIncreaseLanguageLevel(@NotNull EditorFixture editor) {
    editor.enterText("\nfloat x = 1_000;");
    editor.moveTo(editor.findOffset("1_0^00;"));
    editor.waitForCodeAnalysisHighlightCount(ERROR, 1);
    editor.invokeQuickfixAction("Set language level to 7");
    editor.waitForCodeAnalysisHighlightCount(ERROR, 0);

    guiTest.ideFrame().getEditor().invokeAction(UNDO);
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    guiTest.ideFrame().findMessageDialog("Undo").clickOk();
    editor.waitForCodeAnalysisHighlightCount(ERROR, 1);
  }
}
