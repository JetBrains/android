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
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class LintCheckWithKotlinTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

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
  @RunIn(TestGroup.QA)
  @Test
  public void lintCheckWithKotlin() throws Exception {
    String inspectionResults = guiTest.importProjectAndWaitForProjectSyncToFinish("KotlinInstrumentation")
      .getEditor()
      .open("app/src/main/java/android/com/kotlininstrumentation/MainActivity.kt")
      .moveBetween("setContentView(R.layout.activity_main)", "")
      .enterText("\nfindViewById<TextView>(0).text=\"st\"")
      .getIdeFrame()
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    assertThat(inspectionResults).contains("String literal in 'setText' can not be translated. Use Android resources instead.");
  }
}
