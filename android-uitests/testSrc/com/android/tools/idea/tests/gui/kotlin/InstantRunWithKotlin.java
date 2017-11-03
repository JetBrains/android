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

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.ComponentWithMnemonics;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.util.Collection;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class InstantRunWithKotlin {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String APP_NAME = "app";
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final Pattern HOT_SWAP_OUTPUT =
    Pattern.compile(".*Hot swapped changes, activity restarted.*", Pattern.DOTALL);

  /**
   * Verifies Kotlin project with Instant run.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 2a79b599-d89b-4a32-98fe-bedd6668dd2c
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import KotlinInstrumentation project and wait for project sync to finish.
   *   2. Create an avd.
   *   3. Build and run project.
   *   4. Add Log.d("TAG", "Testing Instant apps").
   *   5. Run using Instant app and verify 1.
   *   Verify:
   *   1. At bottom left there should be user notification bubble saying "Instant Run applied code
   *      changes and restarted the current Activity." and
   *   2. Instant Run should apply code changes.
   *   </pre>
   * <p>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testInstantRunWithKotlin() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish("KotlinInstrumentation");

    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture.runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ideFrameFixture.getRunToolWindow().findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    ideFrameFixture.getEditor()
      .open("app/src/main/java/android/com/kotlininstrumentation/MainActivity.kt")
      .moveBetween("import android.os.Bundle", "")
      .enterText("\nimport android.util.Log")
      .moveBetween("setContentView(R.layout.activity_main)", "")
      .enterText("\nLog.d(\"TAG\", \"Testing Instant apps\")");

    int notificationBalloonCountBefore = getNotificationBalloonCount(ideFrameFixture);

    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .findApplyChangesButton()
      .click();

    ideFrameFixture.getRunToolWindow().findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(HOT_SWAP_OUTPUT), 120);

    // Since the message like "Instant Run applied code changes and restarted the current Activity."
    // in the notification balloon cannot be retrieved,
    // (NotificationsManagerImpl$5:model:Data:array), here we can only check there is only one
    // more BalloomImpl:MyComponent instance after instant run.
    int notificationBalloonCountAfter = getNotificationBalloonCount(ideFrameFixture);
    assertThat(notificationBalloonCountAfter - notificationBalloonCountBefore)
      .isEqualTo(1);
  }

  private int getNotificationBalloonCount(@NotNull IdeFrameFixture ideFrame) {
    Collection<JPanel> allFound = ideFrame.robot().finder()
      .findAll(ideFrame.target(), Matchers.byType(JPanel.class));
    int count = 0;
    for (JPanel jPanel : allFound) {
      try {
        if (jPanel instanceof ComponentWithMnemonics) {
          count++;
        }
      }
      catch (ComponentLookupException e) {
      }
    }
    return count;
  }
}
