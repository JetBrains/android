/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class IdeSettingsTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private IdeSettingsDialogFixture mySettingsDialog;

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Test
  public void testSettingsRemovalForGradleProjects() throws IOException {
    guiTest.importSimpleApplication();
    mySettingsDialog = guiTest.ideFrame().openIdeSettings();
    List<String> settingsNames = mySettingsDialog.getProjectSettingsNames();
    assertThat(settingsNames).excludes("Gant", "GUI Designer");
  }

  @After
  public void closeDialog() {
    if (mySettingsDialog != null) {
      mySettingsDialog.close();
    }
  }
}
