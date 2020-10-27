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
package com.android.tools.idea.tests.gui.sdkmanager;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;

import com.android.SdkConstants;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.sdk.SdkProblemDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.sdk.SelectSdkDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.sdk.SyncAndroidSdkDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.ui.components.JBLabel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(GuiTestRemoteRunner.class)
public class IntegratedSdkManagerTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void androidSdkManagerShowsFromWelcomeScreen() {
    setInvalidSdk(guiTest.welcomeFrame())
      .createNewProjectWhenSdkIsInvalid()
      .openSDKManager()
      .clickOK();
  }

  @Test
  public void androidSdkManagerShowsFromOpenProject() throws Exception {
    setInvalidSdk(guiTest.importSimpleApplication())
      .openFromMenu(SdkProblemDialogFixture::find, "File", "New", "New Project...")
      .openSDKManager()
      .clickOK();
  }

  @Test
  public void androidSdkManagerShowsFromToolbar() throws Exception {
    guiTest
      .importSimpleApplication()
      .invokeSdkManager()
      .clickOK();
  }

  private static WelcomeFrameFixture setInvalidSdk(WelcomeFrameFixture fixture) {
    setInvalidSdkPath();
    return fixture;
  }

  private static IdeFrameFixture setInvalidSdk(IdeFrameFixture fixture) {
    setInvalidSdkPath();
    // Changing the SDK path triggers a gradle sync, and that will show a couple of dialogs to select/sync the SDK.
    waitUntilShowing(fixture.robot(), Matchers.byText(JBLabel.class, "Please provide the path to the Android SDK."));
    SelectSdkDialogFixture.find(fixture.robot())
      .clickCancel();
    SyncAndroidSdkDialogFixture.find(fixture.robot())
      .clickNo();
    return fixture;
  }

  /**
   * Its OK to call this method, and not set the Android SDK back on tear down. The value is reset every time a test starts by
   * a call to {@link GuiTests#setUpSdks()}
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static void setInvalidSdkPath() {
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(
      () -> {
        File invalidAndroidSdkPath = GuiTests.getProjectCreationDirPath(null);
        File androidSdkPlatformPath = new File(invalidAndroidSdkPath, SdkConstants.FD_PLATFORMS);
        androidSdkPlatformPath.mkdirs();
        IdeSdks.getInstance().setAndroidSdkPath(invalidAndroidSdkPath, null);
        androidSdkPlatformPath.delete(); // Simulate user removing the Android SDK
      }));

    AndroidSdks.getInstance().setSdkData(null);
  }
}
