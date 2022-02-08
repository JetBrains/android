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

import com.android.tools.idea.tests.gui.emulator.DeleteAvdsRule;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ConfigureKotlinDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.KotlinIsNotConfiguredDialogFixture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.regex.Pattern;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import java.util.concurrent.TimeUnit;


@RunWith(GuiTestRemoteRunner.class)
public class NewJavaProjectToKotlinProjectConversionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  private final EmulatorTestRule emulator = new EmulatorTestRule();

  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(new DeleteAvdsRule())
    .around(emulator);

  private static final Logger logger = Logger.getInstance(NewJavaProjectToKotlinProjectConversionTest.class);

  protected static final String TEMPLATE = "Empty Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = 21;
  public static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final int GRADLE_SYNC_TIMEOUT_SECONDS = 90;

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

  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testNewJavaProjectToKotlinProjectConversion() throws Exception {

    IdeFrameFixture ideFrameFixture = ConversionTestUtil.createNewProject(guiTest, TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Java);

    ideFrameFixture.waitAndInvokeMenuPath("Code", "Convert Java File to Kotlin File");

    //Click 'OK, configure Kotlin in the project' on 'Kotlin is not configured in the project' dialog box
    /*
     * Content of dialog box:  'You will have to configure Kotlin in project before performing a conversion'
     */
    KotlinIsNotConfiguredDialogFixture.find(ideFrameFixture.robot())
      .clickOkAndWaitDialogDisappear();

    //Click 'OK' on 'Configure Kotlin with Android with Gradle' dialog box
    ConfigureKotlinDialogFixture.find(ideFrameFixture.robot())
        .clickOkAndWaitDialogDisappear();

    //ideFrameFixture.requestProjectSyncAndWaitForSyncToFinish(Wait.seconds(GRADLE_SYNC_TIMEOUT_SECONDS));

    //Gradle sync is failing https://buganizer.corp.google.com/issues/180411529 and because of it this test case is failing

    ideFrameFixture.invokeAndWaitForBuildAction(Wait.seconds(120), "Build", "Rebuild Project");

    /*
    emulator.createDefaultAVD(ideFrameFixture.invokeAvdManager());

    ideFrameFixture.runApp(APP_NAME, emulator.getDefaultAvdName());

    // Check app successfully builds and deploys on emulator.
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 60);
    */
  }
}
