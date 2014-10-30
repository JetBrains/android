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

import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.GradleToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RunToolWindowFixture;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.fest.swing.timing.Timeout;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GradleTasksTest extends GuiTestCase {

  @Test @IdeGuiTest
  public void testNotFinishedImmediatelyAndOutputIsShown() throws IOException {
    // This test checks two things:
    //   1. There was a problem that every time a gradle task was started from IDE corresponding run/debug tool window
    //      was shown and after that it's state was changed as the task was already finished (though actual execution
    //      continued). That was because task execution was delegated to a background thread and IDE run configuration
    //      infrastructure considered the task to be finished because control flow had returned;
    //   2. Gradle tasks are executed via our custom gradle task extension. The problem was that corresponding
    //      run/debug tool window content was not populated by the task output;
    IdeFrameFixture projectFrame = openSimpleApplication();
    Module module = projectFrame.getModule("app");

    // Add a long-running task and refresh the project.
    VirtualFile vFile = GradleUtil.getGradleBuildFile(module);
    assertNotNull(vFile);
    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(projectFrame.getProject(), new Runnable() {
      @Override
      public void run() {
        document.insertString(document.getTextLength(),
                              "\n" +
                              "\n" +
                              "task('hello') << {\n" +
                              "    10.times {\n" +
                              "        logger.lifecycle('output entry ' + it)\n" +
                              "        Thread.sleep(1000)\n" +
                              "    }\n" +
                              "}");
      }
    });

    projectFrame.requestProjectSync();
    projectFrame.waitForGradleProjectSyncToFinish();

    // Run that long-running task
    GradleToolWindowFixture gradleToolWindow = projectFrame.getGradleToolWindow();
    String taskName = "hello";
    gradleToolWindow.runTask(taskName);

    // Ensure that task output is shown and updated.
    final RunToolWindowFixture runToolWindow = projectFrame.getRunToolWindow();
    final ExecutionToolWindowFixture.ContentFixture runContent = runToolWindow.findContent(String.format("SimpleApplication:app [%s]", taskName));
    Timeout timeout = Timeout.timeout(2, TimeUnit.SECONDS);
    for (int i = 0; i < 7; i++) {
      runContent.waitForOutput(new PatternTextMatcher(Pattern.compile(".*output entry " + i + ".*", Pattern.DOTALL)), timeout);
      assertTrue(runContent.isExecutionInProgress());
    }
    Pause.pause(new Condition("task execution is successfully finished") {
      @Override
      public boolean test() {
        return !runContent.isExecutionInProgress();
      }
    }, SHORT_TIMEOUT);
  }
}
