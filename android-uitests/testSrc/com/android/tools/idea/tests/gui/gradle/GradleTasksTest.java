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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleToolWindowFixture;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.fest.swing.util.PatternTextMatcher;
import org.fest.swing.util.TextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.tests.gui.framework.fixture.FileFixture.getDocument;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static java.util.regex.Pattern.DOTALL;
import static org.fest.swing.util.Strings.match;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GradleTasksTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testNotFinishedImmediatelyAndOutputIsShown() throws IOException {
    // This test checks two things:
    //   1. There was a problem that every time a gradle task was started from IDE corresponding run/debug tool window
    //      was shown and after that it's state was changed as the task was already finished (though actual execution
    //      continued). That was because task execution was delegated to a background thread and IDE run configuration
    //      infrastructure considered the task to be finished because control flow had returned;
    //   2. Gradle tasks are executed via our custom gradle task extension. The problem was that corresponding
    //      run/debug tool window content was not populated by the task output;

    openProjectAndAddToGradleConfig("\n" +
                                    "\n" +
                                    "task('hello') << {\n" +
                                    "    10.times {\n" +
                                    "        logger.lifecycle('output entry ' + it)\n" +
                                    "        Thread.sleep(1000)\n" +
                                    "    }\n" +
                                    "}");
    // Run that long-running task
    String taskName = "hello";
    runTask(
      taskName, runContent -> {
        for (int i = 0; i < 7; i++) {
          runContent.waitForOutput(new PatternTextMatcher(Pattern.compile(".*output entry " + i + ".*", DOTALL)), 2);
          assertTrue(runContent.isExecutionInProgress());
        }
        Wait.minutes(2).expecting("task execution to finish").until(() -> !runContent.isExecutionInProgress());
      });
  }

  @Test
  public void testTaskCancellation() throws Exception {
    // Main success scenario:
    //   1. Execute regular 'build' task, ensure that it's successful
    //   2. Start 'build' task once again (assuming that it takes some time for it to finish)
    //   3. Stop the task
    //   4. Ensure that the task is really finished
    guiTest.importSimpleApplication();
    guiTest.ideFrame().requestProjectSync();
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    final Pattern buildSuccessfulPattern = Pattern.compile(".*BUILD SUCCESSFUL.*", DOTALL);
    runTask(
      "build", runContent -> {
        boolean askedToStop = runContent.stop();
        assertTrue(askedToStop);
        Wait.seconds(30).expecting("'build' task to stop").until(
          () -> !runContent.isExecutionInProgress() && runContent.outputMatches(new NotMatchingPatternMatcher(buildSuccessfulPattern)));
      });
  }

  private void openProjectAndAddToGradleConfig(@NotNull final String textToAdd) throws IOException {
    guiTest.importSimpleApplication();
    Module module = guiTest.ideFrame().getModule("app");

    // Add a long-running task and refresh the project.
    VirtualFile buildFile = getGradleBuildFile(module);
    assertNotNull(buildFile);
    final Document document = getDocument(buildFile);
    assertNotNull(document);
    runWriteCommandAction(
      guiTest.ideFrame().getProject(), () -> {
        document.insertString(document.getTextLength(), textToAdd);
      });

    guiTest.ideFrame().requestProjectSync();
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }

  private void runTask(@NotNull String taskName, @NotNull Consumer<ExecutionToolWindowFixture.ContentFixture> closure) {
    GradleToolWindowFixture gradleToolWindow = guiTest.ideFrame().getGradleToolWindow();
    gradleToolWindow.runTask(taskName);

    // Ensure that task output is shown and updated.
    String regex = ".*SimpleApplication \\[" + taskName + "\\].*";
    PatternTextMatcher matcher = new PatternTextMatcher(Pattern.compile(regex, DOTALL));
    closure.consume(guiTest.ideFrame().getRunToolWindow().findContent(matcher));
  }

  private static class NotMatchingPatternMatcher implements TextMatcher {
    @NotNull private final Pattern myPattern;

    NotMatchingPatternMatcher(@NotNull Pattern pattern) {
      myPattern = pattern;
    }

    @Override
    public boolean isMatching(String text) {
      return !match(myPattern, text);
    }

    @Override
    @NotNull
    public String description() {
      return "not matching pattern";
    }

    @Override
    @NotNull
    public String formattedValues() {
      return myPattern.pattern();
    }
  }
}
