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

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.fest.assertions.Assertions.assertThat;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class IdeSettingsTest extends GuiTestCase {
  private IdeSettingsDialogFixture mySettingsDialog;

  @Test @IdeGuiTest
  public void testSettingsRemovalForGradleProjects() throws IOException {
    myProjectFrame = importSimpleApplication();
    mySettingsDialog = myProjectFrame.openIdeSettings();
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
