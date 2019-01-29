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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.flags.StudioFlags.NPW_DYNAMIC_APPS;
import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class NameWithSpaceTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  @After
  public void tearDown() {
    NPW_DYNAMIC_APPS.clearOverride();
  }

  /**
   * Verify able to create a new project with name containing a space.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: beb45d64-d93d-4e04-a7b3-6d21f130949f
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project with min sdk 23.
   *   2. Enter a project name with at least one space.
   *   3. Accept all other defaults.
   *   4. Wait for build to finish.
   *   5. Project is created successfully.
   *   Verify:
   *   Successfully created new project with name containing a space.
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void createNewProjectNameWithSpace() {
    EditorFixture editor = new NewProjectDescriptor("Test Application").withMinSdk("23").create(guiTest)
      .getEditor()
      .open("app/src/main/res/values/strings.xml", EditorFixture.Tab.EDITOR);
    String text = editor.getCurrentFileContents();
    assertThat(text).contains("Test Application");
  }
}
