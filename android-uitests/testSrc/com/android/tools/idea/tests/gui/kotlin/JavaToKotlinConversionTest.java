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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ConfigureKotlinDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.KotlinIsNotConfiguredDialogFixture;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import com.intellij.openapi.project.DumbService;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class JavaToKotlinConversionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies it can convert Java class to Kotlin Class.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 7adb5104-9244-4cac-a1df-7d04991c8f14
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication project and wait for project sync to finish.
   *   2. Open up MyActivity.java file.
   *   3. Invoke Code > Convert Java to Kotlin
   *   4. Build the app.
   *   5. Verify 1, 2, 3.
   *   Verify:
   *   1. Ensure the code java class is converted to Kotlin.
   *   2. Check if the Activities are getting converted to Kotlin.
   *   3. App is built successfully.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testJavaToKotlinConversion() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication");

    openJavaAndPressConvertToKotlin(ideFrameFixture);

    KotlinIsNotConfiguredDialogFixture.find(ideFrameFixture.robot())
      .clickOkAndWaitDialogDisappear();

    guiTest.ideFrame().actAndWaitForGradleProjectSyncToFinish(
      it ->
        ConfigureKotlinDialogFixture.find(ideFrameFixture.robot())
          .clickOkAndWaitDialogDisappear()

      // We need to wait for sync because otherwise Studio will not understand that we have added Kotlin support
    );

    // Doing it twice because after the first time we have only added Kotlin support to the project
    openJavaAndPressConvertToKotlin(ideFrameFixture);

    EditorFixture editor = ideFrameFixture.getEditor();

    Wait.seconds(10).expecting("Wait for kt file is generated.")
      .until(() -> "MyActivity.kt".equals(editor.getCurrentFileName()));

    assertThat(editor.getCurrentFileContents()).contains("class MyActivity : Activity() {");

    ideFrameFixture.invokeAndWaitForBuildAction(Wait.seconds(120), "Build", "Rebuild Project");
  }

  private static void openJavaAndPressConvertToKotlin(@NotNull IdeFrameFixture ideFrameFixture) {
    // Wait for indexing to finish
    DumbService.getInstance(ideFrameFixture.getProject()).waitForSmartMode();

    ideFrameFixture.getEditor().open("app/src/main/java/google/simpleapplication/MyActivity.java");
    ideFrameFixture.waitAndInvokeMenuPath("Code", "Convert Java File to Kotlin File");
  }
}
