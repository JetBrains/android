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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRunner.class)
public final class NlPreviewFormTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Test
  public void setActiveModelDoesntThrowNullPointerException() {
    NewProjectWizardFixture wizard = myGuiTest.welcomeFrame().createNewProject();

    myGuiTest.setProjectPath(wizard.getConfigureAndroidProjectStep().getLocationInFileSystem());
    wizard.clickNext();

    wizard.clickNext();

    wizard.chooseActivity("Basic Activity");
    wizard.clickNext();

    wizard.clickFinish();

    myGuiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }
}
