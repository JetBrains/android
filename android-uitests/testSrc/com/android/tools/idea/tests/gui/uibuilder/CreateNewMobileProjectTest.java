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
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class CreateNewMobileProjectTest {
  // We need to increase timeout to 7 minutes, since 5 minutes will have a flake rate of ~0.5%, timing out on indexing.
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  /**
   * Verify creating a new project from default template.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 5f6e38f4-718f-435b-85fe-8d739cb42271
   * <p>
   * <pre>
   *   Steps:
   *   1. From the welcome screen, click on "Start a new Android Studio project".
   *   2. Enter a unique project name.
   *   3. Accept all other defaults.
   *   Verify:
   *   1. Check that the project contains 2 module.
   *   2. Check that MainActivity is in AndroidManifest.
   *   </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void createNewMobileProject() {
    IdeFrameFixture ideFrame = newProject("Test Application").withDefaultComposeActivity().create(guiTest);
    assertThat(ideFrame.getModuleNames()).containsExactly("Test_Application", "Test_Application.app", "Test_Application.app.main",
                                                          "Test_Application.app.unitTest", "Test_Application.app.androidTest");

    // Make sure that the activity registration uses the relative syntax
    // (regression test for https://code.google.com/p/android/issues/detail?id=76716)
    String androidManifestContents = ideFrame.getEditor()
                                             .open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
                                             .getCurrentFileContents();
    assertThat(androidManifestContents).contains("\".MainActivity\"");
  }

  @NotNull
  private static NewProjectDescriptor newProject(@NotNull String name) {
    return new NewProjectDescriptor(name);
  }
}
