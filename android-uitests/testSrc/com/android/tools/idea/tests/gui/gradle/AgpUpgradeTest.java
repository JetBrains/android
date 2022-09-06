/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.AGPUpgradeAssistantToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AgpUpgradeTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  private File projectDir;

  @Before
  public void setUp() throws Exception {
    projectDir = guiTest.setUpProject("SimpleApplication", null, "7.1.0", null, null);
    guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testAgpUpgradeUsingGradleBuildFie() {

    IdeFrameFixture ideFrame = guiTest.ideFrame();

    String studioVersion = ideFrame.getAndroidStudioVersion();

    EditorFixture editor = ideFrame.getEditor();
    AGPUpgradeAssistantToolWindowFixture upgradeAssistant = ideFrame.getUgradeAssistantToolWindow();

    String agpVersion = upgradeAssistant.generateAGPVersion(studioVersion);

    List<String> agpVersionsList = upgradeAssistant.getAGPVersions();

    upgradeAssistant.hide();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertEquals(agpVersion, agpVersionsList.get(0));

    editor.open("build.gradle")
      .select("gradle\\:(\\d+\\.\\d+\\.\\d+)")
      .enterText(agpVersion);

    ideFrame.requestProjectSyncAndWaitForSyncToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertThat(editor.open("build.gradle").getCurrentFileContents()).contains(agpVersion);
  }
}

