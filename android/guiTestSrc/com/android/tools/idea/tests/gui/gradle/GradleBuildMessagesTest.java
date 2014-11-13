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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture;
import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import org.fest.swing.core.GenericTypeMatcher;
import org.junit.Test;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.HyperlinkFixture;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;

public class GradleBuildMessagesTest extends GuiTestCase {

  @Test
  public void hyperlinks() throws Exception {
    IdeFrameFixture projectFrame = openSimpleApplication();
    projectFrame.invokeProjectMakeWithGradleOutput(
      ":app:preBuild FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':app:preBuild'.\n" +
      "> failed to find target android-L : /Users/deniszhdanov/dev/android-sdk/android-sdk-macosx\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 4.764 secs");
    projectFrame.waitUntilFakeGradleOutputIsApplied();

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessagesToolWindowFixture.AbstractContentFixture content = messages.getGradleBuildContent();
    String text = "Install missing platform(s) and sync project";
    MessageFixture message = content.findMessageContainingText(ErrorTreeElementKind.ERROR, text);
    HyperlinkFixture hyperlink = message.findHyperlink(text);
    hyperlink.click(false);
    // We expect 'install android platform' dialog to appear, however, it doesn't have specific title/message, so, we just check that
    // a dialog appear as a result of link click.
    GuiTests.waitUntilFound(myRobot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        return dialog.isShowing();
      }
    });
  }
}
