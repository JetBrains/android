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

import static com.android.tools.idea.flags.StudioFlags.NPW_DYNAMIC_APPS;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateLoginActivityTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

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
   *   1. An activity template that provides input fields and a sample implementation of
   *      an AsyncTask that asks users to login or register with their credentials is added to the project
   * </pre>
   */
  @RunIn(TestGroup.FAT)
  @Test
  public void activityTemplate() {
    // Create a new project with Login Activity.
    if (NPW_DYNAMIC_APPS.get()) {
      guiTest.welcomeFrame().createNewProject()
             .getChooseAndroidProjectStep()
             .chooseActivity("Login Activity")
             .wizard()
             .clickNext()
             .getConfigureNewAndroidProjectStep()
             .enterName("LoginActApp")
             .enterPackageName("dev.tools")
             .wizard()
             .clickFinish();
    }
    else {
      guiTest.welcomeFrame().createNewProject()
             .getConfigureAndroidProjectStep()
             .setCppSupport(false)
             .enterApplicationName("LoginActApp")
             .enterCompanyDomain("android.devtools")
             .enterPackageName("dev.tools")
             .wizard()
             .clickNext()
             .clickNext() // Skip "Select minimum SDK Api" step.
             .chooseActivity("Login Activity")
             .clickNext() // Use default activity name.
             .clickFinish();
    }

    guiTest.testSystem().waitForProjectSyncToFinish(guiTest.ideFrame());

    // Verification.
    EditorFixture editorFixture = guiTest.ideFrame().getEditor();
    String content = editorFixture.getCurrentFileContents();

    String emailViewCode = "mEmailView = (AutoCompleteTextView) findViewById(R.id.email)";
    String pwViewCode = "mPasswordView = (EditText) findViewById(R.id.password)";
    String asyncTaskCode = "public class UserLoginTask extends AsyncTask<Void, Void, Boolean>";

    assertThat(content.contains(emailViewCode)).isTrue();
    assertThat(content.contains(pwViewCode)).isTrue();
    assertThat(content.contains(asyncTaskCode)).isTrue();
  }
}
