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
package com.android.tools.idea.tests.gui.kotlin;

import static com.android.tools.idea.wizard.template.Language.Java;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.emulator.DeleteAvdsRule;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.regex.Pattern;
import org.fest.swing.timing.Wait;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import java.util.concurrent.TimeUnit;


@RunWith(GuiTestRemoteRunner.class)
public class NewJavaProjectToKotlinProjectConversionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  private final EmulatorTestRule emulator = new EmulatorTestRule();

  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(new DeleteAvdsRule())
    .around(emulator);

  private static final Logger logger = Logger.getInstance(NewJavaProjectToKotlinProjectConversionTest.class);

  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty Activity";
  protected static final String BASIC_ACTIVITY_TEMPLATE = "Basic Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = 29;

  /**
   * Verifies it can convert Java Project to Kotlin Project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 7adb5104-9244-4cac-a1df-7d04991c8f14
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a new EmptyActivity Project
   *   2. Open up MainActivity.java and then Invoke Code > Convert Java to Kotlin [Verify 1,2]
   *   3. Click on Yellow configure ribbon to configure kotlin
   *   4. Select all modules on app | OK (Verify 3,4)
   *   Verify:
   *   1. Ensure the code java class is converted to Kotlin.
   *   2. Check if the Activities are getting converted to Kotlin.
   *   3. Gradle sync works file
   *   4. App is built successfully.
   *   </pre>
   * <p>
   */

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testNewEmptyActivityJavaProjectToKotlinProjectConversion() throws Exception {

    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Java);

    ConversionTestUtil.convertJavaToKotlin(guiTest);

    guiTest.waitForBackgroundTasks();

    guiTest.robot().waitForIdle();

    guiTest.ideFrame().requestProjectSyncAndWaitForSyncToFinish();

    guiTest.waitForBackgroundTasks();

    guiTest.robot().waitForIdle();

    assertThat(guiTest.ideFrame().invokeProjectMake(Wait.seconds(240)).isBuildSuccessful()).isTrue();
  }

  @Ignore
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testNewBasicActivityJavaProjectToKotlinProjectConversion() throws Exception {

    WizardUtils.createNewProject(guiTest, BASIC_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Java);

    ConversionTestUtil.convertJavaToKotlin(guiTest);

    ConversionTestUtil.changeKotlinVersion(guiTest);

    guiTest.ideFrame().requestProjectSyncAndWaitForSyncToFinish();

    assertThat(guiTest.ideFrame().invokeProjectMake(Wait.seconds(120)).isBuildSuccessful()).isTrue();
  }
}
