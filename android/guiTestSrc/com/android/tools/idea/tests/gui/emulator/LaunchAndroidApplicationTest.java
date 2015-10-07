/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.emulator;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.regex.Pattern;

import static com.android.tools.idea.tests.gui.framework.GuiTests.LONG_TIMEOUT;
import static junit.framework.Assert.assertTrue;

public class LaunchAndroidApplicationTest extends GuiTestCase {
  private static final String APP_NAME = "app";
  private static final String PROCESS_NAME = "com.android.simple.application";
  private static final Pattern LOCAL_PATH_OUTPUT = Pattern.compile(
    ".*local path: (?:[^\\/]*[\\/])*SimpleApplication/app/build/outputs/apk/app-debug\\.apk.*", Pattern.DOTALL);

  @Ignore
  @Test @IdeGuiTest
  public void testRunOnEmulator() throws IOException, ClassNotFoundException {
    myProjectFrame = importSimpleApplication();

    myProjectFrame.runApp(APP_NAME);
    myProjectFrame.findChooseDeviceDialog().selectEmulator("Nexus7")
                                         .clickOk();

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    myProjectFrame.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), LONG_TIMEOUT);

    myProjectFrame.getAndroidToolWindow().selectDevicesTab()
                                       .selectProcess(PROCESS_NAME)
                                       .clickTerminateApplication();
  }

  @Ignore
  @Test @IdeGuiTest
  public void testDebugOnEmulator() throws IOException, ClassNotFoundException, EvaluateException {
    myProjectFrame = importSimpleApplication();
    final EditorFixture editor = myProjectFrame.getEditor();
    final int offset = editor.open("app/src/main/java/google/simpleapplication/MyActivity.java", EditorFixture.Tab.EDITOR).findOffset(
      "setContentView", "(R.layout.activity_my);", true);
    assertTrue(offset >= 0);

    myProjectFrame.debugApp(APP_NAME);
    myProjectFrame.findChooseDeviceDialog().selectEmulator("Nexus7")
                                         .clickOk();

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    myProjectFrame.getDebugToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), LONG_TIMEOUT);

    myProjectFrame.getAndroidToolWindow().selectDevicesTab()
                                       .selectProcess(PROCESS_NAME)
                                       .clickTerminateApplication();
  }
}
