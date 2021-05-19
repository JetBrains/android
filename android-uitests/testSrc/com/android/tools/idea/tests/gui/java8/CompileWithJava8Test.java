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
package com.android.tools.idea.tests.gui.java8;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CompileWithJava8Test {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies a project can be compiled with Java 8.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: d6dc23f3-33ff-4ffc-af80-6ab822388274
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import MinSdk24App project, in this project, build.gradle(Module:app) is enabled
   *      Java 8 for lambda expressions; and In MainActivity.java, the following statement
   *      is added to onCreate() method:
   *      new Thread(() ->{
   *          Log.d("TAG", "Hello World from Lambda Expression");
   *      }).start();
   *   2. Run Build -> Make Project
   *   Verify:
   *   1. Verify project compiled successfully.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  public void compileWithJava8() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("MinSdk24App", Wait.seconds(120));
    BuildStatus result = ideFrameFixture.invokeProjectMake();
    assertThat(result.isBuildSuccessful()).isTrue();
  }
}
