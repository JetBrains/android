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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;

@RunWith(GuiTestRemoteRunner.class)
public class CreateNewAppModuleWithDefaultsTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies addition of new application module to application.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: fd583b0a-bedd-4ec8-9207-70e4994ed761
   * <pre>
   *   Test Steps
   *   1. File -> new module
   *   2. Select Phone & Tablet module
   *   3. Choose no activity
   *   3. Wait for build to complete
   *   Verification
   *   a new folder matching the module name should have been created.
   * </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void createNewAppModuleWithDefaults() throws Exception {
    guiTest.importSimpleApplication()
           .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
           .clickNextPhoneAndTabletModule()
           .enterModuleName("application_module")
           .wizard()
           .clickNext()
           .chooseActivity("No Activity")
           .clickFinish()
           .waitForGradleProjectSyncToFinish(Wait.seconds(30));
    assertAbout(file()).that(guiTest.getProjectPath("application_module")).isDirectory();
  }
}
