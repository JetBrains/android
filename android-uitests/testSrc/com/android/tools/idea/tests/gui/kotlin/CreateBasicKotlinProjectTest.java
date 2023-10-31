/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.kotlin.ProjectWithKotlinTestUtil.createKotlinProj;
import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class CreateBasicKotlinProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private static final String KOTLIN_FILE = "MainActivity.kt";

  /**
   * Verifies that can creating a new project with Kotlin.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 4d4c36b0-23a7-4f16-9293-061e2fb1310f
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a basic Kotlin project following the default steps.
   *   2. Select the "include Kotlin" support checkbox [verify 1 & 2].
   *   3. Build and run project, and verify 3.
   *   Verify:
   *   1. Check if build is successful.
   *   2. Mainactivity should have .kt as extension and ensure the class has Kotlin code.
   *   </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void createBasicKotlinProject() throws IOException {
    createKotlinProj(false, guiTest);

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();
    assertThat(KOTLIN_FILE).isEqualTo(ideFrameFixture.getEditor().getCurrentFileName());

    assertThat(ideFrameFixture.invokeProjectMake(Wait.seconds(180)).isBuildSuccessful()).isTrue();
  }
}
