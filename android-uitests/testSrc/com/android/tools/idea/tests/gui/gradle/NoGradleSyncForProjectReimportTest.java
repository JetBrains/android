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
package com.android.tools.idea.tests.gui.gradle;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class NoGradleSyncForProjectReimportTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies that no more Gradle sync after re-opening a recent project, which was Gradle sync successfully before.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: TODO
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import simple local application project
   *   2. Wait for gradle sync to be finished (Verify 1)
   *   3. Close the project
   *   4. Re-open the same project from Recent Projects Panel on the left side of Welcome Screen (Verify 2)
   *   Verify:
   *   1. Check if build is successful
   *   2. Check if Gradle sync needed, which means the Gradle Sync state is saved in/got from cache
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void noGradleSyncForProjectReimport() throws Exception {
    assertThat(guiTest.importSimpleApplication()
      .closeProject()
      .openTheMostRecentProject(guiTest)
      .isGradleSyncNotNeeded()
    ).isTrue();
  }
}
