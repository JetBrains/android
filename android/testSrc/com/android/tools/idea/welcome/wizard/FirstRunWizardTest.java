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
package com.android.tools.idea.welcome.wizard;

import static org.mockito.Mockito.mock;

import com.android.testutils.TestUtils;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.config.GlobalInstallerData;
import com.android.tools.idea.welcome.config.InstallerData;
import com.android.tools.idea.welcome.install.SdkComponentInstaller;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import java.io.File;
import java.nio.file.Path;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FirstRunWizardTest extends AndroidTestBase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    IdeSdks.removeJdksOn(myFixture.getProjectDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  public void testStepsVisibility() {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    File wrongPath = new File("/$@@  \"\'should/not/exist");
    File androidHome = TestUtils.getSdk().toFile();

    assertPagesVisible(null, true, false);

    InstallerData correctData = new InstallerData(androidHome, true, "timestamp", "1234");
    assertPagesVisible(correctData, false, true);

    InstallerData noAndroidSdkData = new InstallerData(null, true, "timestamp", "1234");
    assertPagesVisible(noAndroidSdkData, true, false);

    InstallerData noJdkData = new InstallerData(androidHome, true, "timestamp", "1234");
    assertPagesVisible(noJdkData, false, true);

    InstallerData noInstallAndroidData = new InstallerData(androidHome, true, "timestamp", "1234");
    assertPagesVisible(noInstallAndroidData, false, true);

    InstallerData bogusPathsData = new InstallerData(wrongPath, true, "timestamp", "1234");
    assertPagesVisible(bogusPathsData, true, false);
  }

  private void assertPagesVisible(@Nullable InstallerData data, boolean isComponentsStepVisible, boolean hasAndroidSdkPath) {
    GlobalInstallerData.set(data);
    FirstRunWizardMode mode = data == null ? FirstRunWizardMode.NEW_INSTALL : FirstRunWizardMode.INSTALL_HANDOFF;
    Path sdkLocation = TestUtils.getSdk();

    FirstRunWizardModel firstRunWizardModel = new FirstRunWizardModel(
      mode,
      sdkLocation,
      true,
      new SdkComponentInstaller(),
      mock()
    );

    SdkComponentsStep sdkComponentsStep = new SdkComponentsStep(
      firstRunWizardModel,
      getProject(),
      mode,
      mock(),
      mock()
    );
    Disposer.register(getTestRootDisposable(), sdkComponentsStep);

    assertVisible(
      sdkComponentsStep, data, isComponentsStepVisible
    );

    if (data != null) {
      assertEquals(data.toString(), hasAndroidSdkPath, data.hasValidSdkLocation());
    }
  }

  private void assertVisible(SdkComponentsStep step, @Nullable InstallerData data, boolean expected) {
    assertEquals(String.format("Step: %s, data: %s", step.getClass(), data), expected, isStepVisible(step));
  }

  public boolean isStepVisible(@NotNull SdkComponentsStep step) {
    // The model wizard will throw an exception if there are no visible steps
    // to show
    try {
      ModelWizard modelWizard = new ModelWizard.Builder(step).build();
      Disposer.register(getTestRootDisposable(), modelWizard);
    }
    catch (IllegalStateException e) {
      return false;
    }

    return true;
  }
}