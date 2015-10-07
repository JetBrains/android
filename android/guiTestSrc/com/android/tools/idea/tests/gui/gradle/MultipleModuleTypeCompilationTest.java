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

import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessage;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.intellij.openapi.compiler.CompilerMessageCategory.ERROR;
import static com.intellij.openapi.compiler.CompilerMessageCategory.INFORMATION;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests fix for issue <a href="https://code.google.com/p/android/issues/detail?id=73640">73640</a>.
 */
@BelongsToTestGroups({PROJECT_SUPPORT})
public class MultipleModuleTypeCompilationTest extends GuiTestCase {
  private static final Pattern JPS_EXECUTING_TASKS_MSG_PATTERN = Pattern.compile("Gradle: Executing tasks: \\[(.*)\\]");

  @Test @IdeGuiTest
  public void testAssembleTaskIsNotInvokedForLocalAarModule() throws IOException {
    myProjectFrame = importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");
    GradleInvocationResult result = myProjectFrame.invokeProjectMake();
    assertTrue(result.isBuildSuccessful());
    List<String> invokedTasks = result.getTasks();
    assertThat(invokedTasks).containsOnly(":app:compileDebugSources", ":app:compileDebugAndroidTestSources", ":javaLib:compileJava");
  }

  @Test @IdeGuiTest
  public void testAssembleTaskIsNotInvokedForLocalAarModuleOnJps() throws IOException {
    myProjectFrame = importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");
    CompileContext context = myProjectFrame.invokeProjectMakeUsingJps();

    String[] invokedTasks = null;
    for (CompilerMessage msg : context.getMessages(INFORMATION)) {
      String text = msg.getMessage();
      Matcher matcher = JPS_EXECUTING_TASKS_MSG_PATTERN.matcher(text);
      if (matcher.matches()) {
        String allTasks = matcher.group(1);
        invokedTasks = allTasks.split(", ");
        break;
      }
    }
    // In JPS we cannot call "compileJava" because in JPS "Make" means "assemble".
    assertThat(invokedTasks).containsOnly(":app:assembleDebug", ":javaLib:assemble");

    int errorCount = context.getMessageCount(ERROR);
    assertEquals(0, errorCount);
  }
}
