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
package com.android.tools.idea.tests.gui.npw;

import static com.android.tools.idea.flags.StudioFlags.NPW_TEMPLATES_AUTOMOTIVE;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that newly created Automotive projects do not have errors in them
 */
@RunWith(GuiTestRemoteRunner.class)
public class NewAutomotiveProjectTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void before() {
    NPW_TEMPLATES_AUTOMOTIVE.override(true);
  }

  @After
  public void after() {
    NPW_TEMPLATES_AUTOMOTIVE.clearOverride();
  }

  @Test
  public void testBuildMediaService() {
    createAutomotiveProject("Media service", Language.JAVA, true);

    guiTest.ideFrame().getEditor()
      .open("mobile/build.gradle") // Did we create a mobile "companion" module?
      .open("automotive/build.gradle") // Check "automotive" module was created and its dependencies
      .moveBetween("implementation project(':shared')", "")
      .open("shared/build.gradle") // Check "shared" module was created and its dependencies
      .moveBetween("androidx.media:media:", "")
      .open("shared/src/main/res/xml/automotive_app_desc.xml")
      .open("shared/src/main/AndroidManifest.xml")
      .moveBetween("android:name=\"com.google.android.gms.car.application\"", "")
      .moveBetween("android:resource=\"@xml/automotive_app_desc\"", "");

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  private void createAutomotiveProject(@NotNull String activityName, @NotNull Language language, boolean useAndroidx) {
    guiTest.welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .selectAutomotiveTab()
      .chooseActivity(activityName)
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .setSourceLanguage(language.getName())
      .selectMinimumSdkApi(FormFactor.AUTOMOTIVE, "28")
      .setUseAndroidX(useAndroidx)
      .wizard()
      .clickFinish();

    guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish();
  }
}
