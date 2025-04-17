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

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class NameWithSpaceAndDollarTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  private static final String TEMPLATE = "Empty Views Activity";
  private static final String APP_NAME = "\'Test\' Application$";
  private static final String PACKAGE_NAME = "android.com.app";
  private static final int MIN_SDK_API = SdkVersionInfo.RECOMMENDED_MIN_SDK_VERSION;;

  /**
   * Verify able to create a new project with name containing a space and a dollar.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: beb45d64-d93d-4e04-a7b3-6d21f130949f
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project with min sdk 23.
   *   2. Enter a project name with at least one space and at least one dollar sign ($).
   *   3. Accept all other defaults.
   *   4. Wait for build to finish.
   *   5. Project is created successfully.
   *   Verify:
   *   Successfully created new project with name containing a space, a "'" and a dollar sign.
   *   </pre>
   */
  @Test
  public void createNewProjectNameWithSpaceAndDollar() {
    WizardUtils.createNewProject(guiTest, TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Language.Kotlin);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Note@ "'" should be escaped in xml, but not in settings.gradle
    assertThat(guiTest.getProjectFileText("app/src/main/res/values/strings.xml"))
      .contains("\\'Test\\' Application$");
    assertThat(guiTest.getProjectFileText(FN_SETTINGS_GRADLE_KTS))
      .contains("'Test' Application\\$");
  }
}
