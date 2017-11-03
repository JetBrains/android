/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.npw.FormFactor.MOBILE;

/**
 * Tests that the min sdk for instant apps is validated correctly
 */
@RunIn(TestGroup.PROJECT_WIZARD)
@RunWith(GuiTestRunner.class)
public class MinSdkForInstantAppTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testErrorWhenInvalidMinSdkSelected() {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject()
      .clickNext();

    newProjectWizard
      .getConfigureFormFactorStep()
      .selectMinimumSdkApi(MOBILE, "20")
      .selectInstantAppSupport(MOBILE)
      .waitForErrorMessageToContain("Phone and Tablet must have a Minimum SDK >= 23 for Instant App Support.");

    newProjectWizard
      .clickCancel();
  }
}
