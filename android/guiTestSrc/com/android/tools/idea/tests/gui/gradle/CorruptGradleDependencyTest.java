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
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.HyperlinkFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.ERROR;
import static org.fest.assertions.Assertions.assertThat;
import static org.jetbrains.android.AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY;

public class CorruptGradleDependencyTest extends GuiTestCase {
  // See https://code.google.com/p/android/issues/detail?id=74842
  @Test @IdeGuiTest
  public void testPrematureEndOfContentLength() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    // Simulate this Gradle error.
    final String failure = "Premature end of Content-Length delimited message body (expected: 171012; received: 50250.";
    projectFrame.requestProjectSyncAndSimulateFailure(failure);

    final String prefix = "Gradle's dependency cache seems to be corrupt or out of sync";
    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();

    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith(prefix));
    HyperlinkFixture hyperlink = message.findHyperlink("Re-download dependencies and sync project (requires network)");
    hyperlink.click();

    projectFrame.waitForGradleProjectSyncToFinish();

    // This is the only way we can at least know that we pass the right command-line option.
    String[] commandLineOptions = ApplicationManager.getApplication().getUserData(GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY);
    assertThat(commandLineOptions).contains("--refresh-dependencies");
  }
}
