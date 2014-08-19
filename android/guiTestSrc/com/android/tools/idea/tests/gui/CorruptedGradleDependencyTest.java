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
package com.android.tools.idea.tests.gui;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.Test;

import java.io.IOException;

import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.ERROR;
import static org.jetbrains.android.AndroidPlugin.EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY;

public class CorruptedGradleDependencyTest extends GuiTestCase {
  // See https://code.google.com/p/android/issues/detail?id=74842
  @Test @IdeGuiTest
  public void testPrematureEndOfContentLength() throws IOException {
    IdeFrameFixture projectFrame = openProject("SimpleApplication");

    final String failure = "Premature end of Content-Length delimited message body (expected: 171012; received: 50250.";
    Runnable failTask = new Runnable() {
      @Override
      public void run() {
        throw new RuntimeException(failure);
      }
    };
    ApplicationManager.getApplication().putUserData(EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY, failTask);

    projectFrame.requestProjectSync();

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    messages.getGradleSyncContent().requireMessage(ERROR, failure);
  }
}
