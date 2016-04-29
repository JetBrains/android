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
package com.android.tools.idea.tests.gui.framework.ndk;

import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Pause;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

public class MiscUtils {
  /**
   * Waits for the robot to be idle and then invokes the menu at path {@code path}.
   */
  public static void invokeMenuPathOnRobotIdle(IdeFrameFixture projectFrame, String... path) {
    projectFrame.robot().waitForIdle();
    projectFrame.invokeMenuPath(path);
  }

  /**
   * Opens the file with basename {@code fileBasename}
   */
  public static void openFile(IdeFrameFixture projectFrame,String fileBasename) {
    invokeMenuPathOnRobotIdle(projectFrame, "Navigate", "File...");
    projectFrame.robot().waitForIdle();
    typeText("multifunction-jni.c", projectFrame.robot(), 30);
    projectFrame.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
  }

  /**
   * Navigates to line number {@code lineNum} of the currently active editor window.
   */
  public static void navigateToLine(IdeFrameFixture projectFrame, int lineNum) {
    invokeMenuPathOnRobotIdle(projectFrame, "Navigate", "Line...");
    projectFrame.robot().enterText(Integer.toString(lineNum));
    projectFrame.robot().waitForIdle();
    projectFrame.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
  }

  private static void typeText(String text, Robot robot, long delayAfterEachCharacterMillis) {
    robot.waitForIdle();
    for (int i = 0; i < text.length(); ++i) {
      robot.type(text.charAt(i));
      Pause.pause(delayAfterEachCharacterMillis, TimeUnit.MILLISECONDS);
    }
  }
}
