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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import org.fest.swing.core.KeyPressInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class LintCheckWithKotlinTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  /**
   * Verifies Lint errors with Kotlin code.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 4dae3115-3dd1-4cd4-bc02-9d8608c814cb
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import KotlinInstrumentation project and wait for project sync to finish.
   *   2. Open MainActivity.kt file, and enter the following code:
   *        findViewById<TextView>(0).text="st"
   *   3. Then "Analyze->Inspect Code..." and verify.
   *   Verify:
   *   1. Observe the following text in inspect results:
   *      String literal in 'setText' can not be translated. Use Android resources instead.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void lintCheckWithKotlin() throws Exception {
    IdeFrameFixture myIdeFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("KotlinInstrumentation");
    GuiTests.waitForProjectIndexingToFinish(myIdeFrameFixture.getProject());

    String inspectionResults = myIdeFrameFixture
      .getEditor()
      .open("app/src/main/java/android/com/kotlininstrumentation/MainActivity.kt")
      .moveBetween("setContentView(R.layout.activity_main)", "")
      .moveBetween("setContentView(R.layout.activity_main)", "") //Adding to reduce flakiness
      .pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_ENTER))
      .typeText("\nfindViewById<TextView>(0).text=\"st\"")
      .getIdeFrame()
      .openFromMenu(InspectCodeDialogFixture::find, "Code", "Inspect Code...")
      .clickButton("Analyze")
      .getResults();

    assertThat(inspectionResults).contains("String literal in setText can not be translated. Use Android resources instead.");
  }
}
