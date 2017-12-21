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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.io.IOException;

@RunWith(GuiTestRunner.class)
public class NativeOptimizedWarningTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String DEBUG_CONFIG_NAME = "app";

  private final GenericTypeMatcher<JLabel> myWarningMatcher = new GenericTypeMatcher<JLabel>(JLabel.class) {
    @Override
    protected boolean isMatching(@NotNull JLabel component) {
      String text = component.getText();
      if (text == null)
        return false;
      return text.contains("WARNING: This function was compiled");
    }
  };

  /**
   * <p>TT ID: TODO this test case needs a TT ID.
   *
   */
  @Ignore("b/37140919")
  @RunIn(TestGroup.QA_UNRELIABLE)
  @Test
  public void test() throws IOException, ClassNotFoundException, InterruptedException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("NativeOptimizedWarningForUI");
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    // Setup breakpoints
    openAndToggleBreakPoints(projectFrame, "app/src/main/cpp/hello-jni.c", "return 1+c;");

    projectFrame.debugApp(DEBUG_CONFIG_NAME).selectDevice(emulator.getDefaultAvdName()).clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    waitForSessionStart(debugToolWindowFixture);

    Wait.seconds(5).expecting("Frame list populated").until(
      () -> debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME).getFramesListFixture().selection().length > 0);
    JListFixture listFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME).getFramesListFixture();

    EditorsSplitters editorTabs = guiTest.robot().finder().findByType(projectFrame.target(), EditorsSplitters.class, true);
    listFixture.requireSelection(0).requireSelection(".*func2.*");
    GuiTests.waitUntilGone(guiTest.robot(), editorTabs, myWarningMatcher);

    Wait.seconds(5).expecting("Frame list long enough").until(() -> listFixture.contents().length > 2);

    listFixture.selectItem(1).requireSelection(".*func1.*");
    GuiTests.waitUntilShowing(guiTest.robot(), editorTabs, myWarningMatcher);

    listFixture.selectItem(2).requireSelection(".*stringFromJNI.*");
    GuiTests.waitUntilGone(guiTest.robot(), editorTabs, myWarningMatcher);

    {
      // We cannot reuse the context fixture we got above, as its windows could have been repurposed for other things.
      final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
      contentFixture.stop();
      contentFixture.waitForExecutionToFinish();
    }
  }
}
