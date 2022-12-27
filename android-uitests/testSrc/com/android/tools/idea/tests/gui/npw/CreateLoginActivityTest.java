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
package com.android.tools.idea.tests.gui.npw;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateLoginActivityTest {

  private static final String LOGIN_ACTIVITY = "app/src/main/java/com/google/myapplication/ui/login/LoginActivity.java";
  //Using 'Empty Views Activity' instead of 'Empty Activity' because of b/263821848
  protected static final String EMPTY_VIEWS_ACTIVITY_TEMPLATE = "Empty Views Activity";

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  @Before
  public void setUp() throws Exception {
    WizardUtils.createNewProject(guiTest, EMPTY_VIEWS_ACTIVITY_TEMPLATE); // Default projects are created with androidx dependencies
    guiTest.robot().waitForIdle();
  }
  /***
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: fb8a268b-b089-409b-b6d9-fd02d3dbf2f9
   * <pre>
   *   Verifies verify that all login activity template can be added to a project.
   *   Test Steps
   *   1. On the Welcome Screen, select "Start a new Android Studio Project"
   *   2. Configure a project name,company domain and location and click Next
   *   3. Click Next
   *   4. Select "Login Activity" and click Next
   *   5. Configure the activity details and click Finish (Verify 1)
   *
   *   With Android Studio 3.3+
   *   1. On the Welcome Screen, select "Start a new Android Studio Project"
   *   2. Select "Login Activity" and click Next
   *   3. Configure a project name, package and location and click Next
   *
   *   Verification
   *   1. An activity template that provides input fields
   *      and asks users to login or register with their credentials is added to the project
   * </pre>
   */
  @RunIn(TestGroup.FAT_BAZEL)
  @Test
  public void activityTemplate() {
    // Create a new project with Login Activity.
    guiTest.ideFrame()
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Login Views Activity")
      .clickFinishAndWaitForSyncToFinish();

    // Verification.
    EditorFixture myEditor = guiTest.ideFrame().getEditor();
    myEditor.open(LOGIN_ACTIVITY);
    String content = myEditor.getCurrentFileContents();

    String username = "binding.username";
    String password = "binding.password";
    String login = "binding.login";
    String progressbar = "binding.loading";

    assertThat(content.contains(username)).isTrue();
    assertThat(content.contains(password)).isTrue();
    assertThat(content.contains(login)).isTrue();
    assertThat(content.contains(progressbar)).isTrue();
  }
}
