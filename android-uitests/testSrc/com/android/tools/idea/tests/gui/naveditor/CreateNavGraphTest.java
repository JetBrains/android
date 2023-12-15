/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.naveditor;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceFileDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateNavGraphTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  /**
   * Verifies Creating Navigation Graph
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 0f617a4b-64e4-457f-996c-efc61c11b499
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a project with any activity
   *   2. Right Click on app > New > Android Resource File
   *   3. Give resource a name "nav_g" and select resource type as "Navigation" > OK (Verify 1)
   *   4. Click OK to add navigation lib(Verify 2)
   *   5. Check build.gradle (Module: app) (Verify 3)
   *
   *   Verify:
   *   1. Pop up should show up to add navigation library dependency
   *   2. New navigation resource should create without any errors
   *   3. Gradle dependency should added to app .gradle file
   *      implementation 'android.arch.navigation:navigation-fragment:x.x.x'
   *   </pre>
   *
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  public void createNavGraph() throws Exception {
    IdeFrameFixture ideFrame =  guiTest.importSimpleApplication();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.getProjectView()
      .selectAndroidPane()
      .clickPath("app");

    ideFrame.invokeMenuPath("File", "New", "Android Resource File");

    CreateResourceFileDialogFixture.find(ideFrame)
      .setFilename("nav_g")
      .setType("Navigation")
      .clickOkAndWaitForDependencyDialog()
      .clickOk();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.requestFocusIfLost();

    String contents = ideFrame.getEditor()
      .open("app/build.gradle")
      .getCurrentFileContents();

    assertThat(contents).contains("androidx.navigation:navigation-fragment");
  }
}
