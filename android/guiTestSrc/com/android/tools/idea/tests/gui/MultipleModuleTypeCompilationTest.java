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

import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static junit.framework.Assert.assertTrue;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests fix for issue <a href="https://code.google.com/p/android/issues/detail?id=73640">73640</a>.
 */
public class MultipleModuleTypeCompilationTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testAssembleTaskIsNotInvokedForLocalAarModule() throws IOException {
    IdeFrameFixture ideFrame = importProject("MultipleModuleTypes");
    GradleInvocationResult result = ideFrame.invokeProjectMake();
    assertTrue(result.isBuildSuccessful());
    List<String> invokedTasks = result.getTasks();
    assertThat(invokedTasks).containsOnly(":app:compileDebugJava", ":javaLib:compileJava");
  }

  @Test @IdeGuiTest
  public void testAssembleTaskIsNotInvokedForLocalAarModuleOnJps() throws IOException {
    IdeFrameFixture ideFrame = importProject("MultipleModuleTypes");

    CompileContext context = ideFrame.invokeProjectMakeUsingJps();
    int errorCount = context.getMessageCount(CompilerMessageCategory.ERROR);
    assertThat(errorCount).isGreaterThan(0);
  }
}
