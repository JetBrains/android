/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.instantrun;

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdEditWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.MockAvdManagerConnection;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.google.common.io.Files.write;
import static com.google.common.truth.Truth.assertThat;

@Ignore("https://android-jenkins.corp.google.com/builders/studio-sanity_master-dev/builds/1548")
@RunWith(GuiTestRunner.class)
public class InstantRunTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String APP_NAME = "app";
  private static final String AVD_NAME = "device under test";
  private static final String TARGET_GRADLE_PLUGIN_VERSION = "2.3.0-dev";
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*adb shell am start .*google\\.simpleapplication.*Connected to process.*", Pattern.DOTALL);
  private static final Pattern HOT_SWAP_OUTPUT =
    Pattern.compile(".*Hot swapped changes, activity restarted.*", Pattern.DOTALL);
  private static final Pattern COLD_SWAP_OUTPUT =
    Pattern.compile(".*Cold swapped changes.*", Pattern.DOTALL);
  private static final String INSTANT_RUN_NOTIFICATION_REGEX =
    ".*Instant Run re-installed and restarted the app.*";

  @Before
  public void setUp() throws Exception {
    MockAvdManagerConnection.inject();
    getEmulatorConnection().deleteAvd(AVD_NAME.replace(' ', '_'));
  }

  @After
  public void tearDown() throws Exception {
    getEmulatorConnection().stopRunningAvd();
    getEmulatorConnection().deleteAvd(AVD_NAME);
  }

  /**
   * Verifies that instant run hot swap works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581583
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit a java file.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run hot swap is applied in Run tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void hotSwap() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    updateGradleVersion(guiTest.getProjectPath());
    createAVD();

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    ideFrameFixture
      .getRunToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .enterText(Strings.repeat("\n", 10));

    ideFrameFixture.waitForGradleProjectSyncToFinish().findRunApplicationButton().click();

    ideFrameFixture
      .getRunToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(HOT_SWAP_OUTPUT), 120);
  }

  /**
   * Verifies that instant run cold swap works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581584
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit a resource xml file.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run cold swap is applied in Run tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void coldSwap() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    updateGradleVersion(guiTest.getProjectPath());
    createAVD();

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    ideFrameFixture
      .getRunToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false)
      .dragComponentToSurface("Widgets", "TextView")
      .waitForRenderToFinish();

    ideFrameFixture.waitForGradleProjectSyncToFinish().findRunApplicationButton().click();

    ideFrameFixture
      .getRunToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(COLD_SWAP_OUTPUT), 120);
  }

  /**
   * Verifies that instant run works as expected when AndroidManifest is changed.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581585
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit AndroidManifest.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run is applied in EventLog tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void changeManifest() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    updateGradleVersion(guiTest.getProjectPath());
    createAVD();

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    ideFrameFixture
      .getRunToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
      .moveBetween("", "<application")
      .enterText("<uses-permission android:name=\"android.permission.INTERNET\" /\n");

    ideFrameFixture.waitForGradleProjectSyncToFinish().findRunApplicationButton().click();

    ideFrameFixture
      .getRunToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    Wait.seconds(1).expecting("The notification is showing").until(() -> {
      try {
        Notification notification = Iterables.getLast(EventLog.getLogModel(ideFrameFixture.getProject()).getNotifications());
        assertThat(notification.getContent()).matches(INSTANT_RUN_NOTIFICATION_REGEX);
        return true;
      } catch (Exception e) {
        System.out.println(e.getClass().toString());
        return false;
      }
    });
  }

  private void updateGradleVersion(@NotNull File file) throws IOException {
    if (GRADLE_PLUGIN_RECOMMENDED_VERSION.equals(TARGET_GRADLE_PLUGIN_VERSION)) {
      return;
    }

    File projectGradleFile = new File(file, "build.gradle");
    String contents = Files.toString(projectGradleFile, Charsets.UTF_8);

    Pattern pattern = Pattern.compile("classpath ['\"]com.android.tools.build:gradle:(.+)['\"]");
    Matcher matcher = pattern.matcher(contents);
    if (matcher.find()) {
      contents = contents.substring(0, matcher.start(1)) + TARGET_GRADLE_PLUGIN_VERSION +
                 contents.substring(matcher.end(1));

      if (TARGET_GRADLE_PLUGIN_VERSION.endsWith("dev")) {
        contents = contents.replace("MAVEN_URL", "LOCAL_MAVEN_URL");
      }

      write(contents, projectGradleFile, Charsets.UTF_8);
    }
  }

  private void createAVD() {
    AvdManagerDialogFixture avdManagerDialog = guiTest.ideFrame().invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    avdEditWizard.selectHardware()
      .selectHardwareProfile("Nexus 5");
    avdEditWizard.clickNext();

    avdEditWizard.getChooseSystemImageStep()
      .selectTab("x86 Images")
      .selectSystemImage("Marshmallow", "23", "x86", "Android 6.0");
    avdEditWizard.clickNext();

    avdEditWizard.getConfigureAvdOptionsStep()
      .setAvdName(AVD_NAME);
    avdEditWizard.clickFinish();
    avdManagerDialog.close();
  }

  @NotNull
  private static MockAvdManagerConnection getEmulatorConnection() {
    return (MockAvdManagerConnection)AvdManagerConnection.getDefaultAvdManagerConnection();
  }
}
