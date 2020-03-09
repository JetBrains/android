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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.device.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class NewProjectThingsTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  /**
   * - Make sure we can build a default android things project
   * - Make sure there is nothing broken when using "lintOptions" - b/118374756
   */
  @Test
  public void defaultAndroidThingsEmptyActivity() {
    guiTest
      .welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .selectTab(FormFactor.THINGS)
      .chooseActivity("Android Things Empty Activity")
      .wizard()
      .clickNext()
      .clickNext()
      .clickFinish();


    guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .invokeMenuPath("Build", "Rebuild Project").waitForBuildToFinish(BuildMode.REBUILD);

    assertThat(guiTest.getProjectFileText("app/build.gradle")).contains("lintOptions");
  }
}
