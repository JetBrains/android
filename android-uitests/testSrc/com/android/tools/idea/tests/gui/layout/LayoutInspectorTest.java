/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.tests.gui.emulator.TestWithEmulator;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.AndroidProcessChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.LayoutInspectorFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class LayoutInspectorTest extends TestWithEmulator {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String PROCESS_NAME = "google.simpleapplication";
  private static final Pattern LOCAL_PATH_OUTPUT = Pattern.compile(".*adb shell am start .*google\\.simpleapplication.*", Pattern.DOTALL);

  @Before
  public void setUp() throws Exception {
    guiTest.importSimpleApplication();
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
  }

  /**
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TR ID: C14581570
   * <pre>
   *   Verifies that the layout inspector launches successfully and shows relevant layout elements
   *   Test Steps
   *   1. Import a project
   *   2. Run the Project to Emulator
   *   3. Click on Layout Inspector
   *   Verification
   *   1. Verify for Layout elements in Hierarchy View of Layout Inspector
   * </pre>
   */
  @Ignore("Deprecated. TODO: remove the test case and the related fixture code.")
  @Test
  @RunIn(TestGroup.QA)
  public void launchLayoutInspector() throws Exception {
    guiTest.ideFrame().runApp("app").selectDevice(AVD_NAME).clickOk();
    // wait for background tasks to finish before requesting run tool window. otherwise run tool window won't activate.
    guiTest.waitForBackgroundTasks();
    // look at the run tool window to determine when the app has started so we can select the process in android tool window
    guiTest.ideFrame()
      .getRunToolWindow()
      .findContent("app")
      .waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), 120);
    List<String> layoutElements = guiTest.ideFrame()
      .getAndroidToolWindow()
      .selectDevicesTab()
      .selectProcess(PROCESS_NAME)
      .clickLayoutInspector()
      .getLayoutElements();
    checkLayout(layoutElements);
  }

  @Test
  @RunIn(TestGroup.UNRELIABLE)
  public void launchLayoutInspectorViaChooser() throws Exception {
    guiTest.ideFrame().runApp("app").selectDevice(AVD_NAME).clickOk();
    // wait for background tasks to finish before requesting run tool window. otherwise run tool window won't activate.
    guiTest.waitForBackgroundTasks();
    guiTest.ideFrame().invokeMenuPath("Tools", "Android", "Layout Inspector");
    // easier to select via index rather than by path string which changes depending on the api version
    AndroidProcessChooserDialogFixture.find(guiTest.robot()).selectProcess().clickOk();
    List<String> layoutElements = new LayoutInspectorFixture(guiTest.robot()).getLayoutElements();
    checkLayout(layoutElements);
  }

  private void checkLayout(List<String> layoutElements) {
    assertThat(layoutElements).contains("android.widget.RelativeLayout");
    assertThat(layoutElements).contains("android.widget.TextView");
    assertThat(layoutElements).contains("android.widget.FrameLayout");
  }
}
