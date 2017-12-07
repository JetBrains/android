// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.connection.assistant;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.intellij.openapi.wm.ToolWindowManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class ConnectionAssistantTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void setUp() throws Exception {
    guiTest.importSimpleLocalApplication();
  }

  /**
   * Verify connection assistant is invoked through Deploy Target Dialog
   *
   * <p>TT ID: TODO
   *
   * <pre>
   *   Test steps:
   *   1. Create a new project.
   *   2. Click Run to bring up the Select Deployment Target Dialog
   *   3. Click Help
   *   Verify:
   *   1. Verify connection assistant is shown in the tool window
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void launchConnectionAssistantViaDeployTargetDialog() throws Exception {
    assertThat(ToolWindowManager.getInstance(guiTest.ideFrame().getProject()).getToolWindow("Assistant")).isNull();
    guiTest.ideFrame().runApp("app").clickHelp();
    guiTest.waitForBackgroundTasks();
    assertThat(ToolWindowManager.getInstance(guiTest.ideFrame().getProject()).getToolWindow("Assistant").isVisible()).isTrue();
  }

  /**
   * Verify connection assistant is invoked through Deploy Target Dialog
   *
   * <p>TT ID: TODO
   *
   * <pre>
   *   Test steps:
   *   1. Create a new project.
   *   2. Click Tools -> Connection Assistant
   *   Verify:
   *   1. Verify connection assistant is shown in the tool window
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void launchConnectionAssistantViaMenu() throws Exception {
    assertThat(ToolWindowManager.getInstance(guiTest.ideFrame().getProject()).getToolWindow("Assistant")).isNull();
    guiTest.ideFrame().invokeMenuPath("Tools", "Connection Assistant");
    assertThat(ToolWindowManager.getInstance(guiTest.ideFrame().getProject()).getToolWindow("Assistant").isVisible()).isTrue();
  }
}
