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
package com.android.tools.idea.tests.gui.welcome;

import com.android.flags.junit.RestoreFlagRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.welcome.FirstRunWizardFixture;
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.tools.idea.flags.StudioFlags.NPW_FIRST_RUN_WIZARD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunIn(TestGroup.UNRELIABLE) // Move later to PROJECT_WIZARD
@RunWith(GuiTestRunner.class)
public class FirstRunWizardCancelTest {

  @Rule public final RestoreFlagRule<Boolean> myRestoreFlagRule = new RestoreFlagRule<>(NPW_FIRST_RUN_WIZARD);
  @Rule public final GuiTestRule guiTest = new GuiTestRule();


  private static final int DUMMY_SDK_VERSION = 123;
  private int mySdkUpdateVersion;

  @Before
  public void setUp() throws IOException {
    mySdkUpdateVersion = getSdkUpdateVersion();
    setSdkUpdateVersion(DUMMY_SDK_VERSION);

    FirstRunWizardFixture.show();
  }

  @After
  public void tearDown() {
    setSdkUpdateVersion(mySdkUpdateVersion);
    FirstRunWizardFixture.close(guiTest.robot());
  }

  @Test
  public void cancelAndIgnore() throws Exception {
    NPW_FIRST_RUN_WIZARD.override(true);
    doCancelAndIgnore();
  }

  @Test
  public void cancelAndRerunOnNextStartup() throws Exception {
    NPW_FIRST_RUN_WIZARD.override(true);
    doCancelAndRerunOnNextStartup();
  }

  @Test
  public void cancelAndDontRerunOnNextStartup() throws Exception {
    NPW_FIRST_RUN_WIZARD.override(true);
    doCancelAndDontRerunOnNextStartup();
  }

  @Test
  public void cancelAndIgnore_legacy() throws Exception {
    NPW_FIRST_RUN_WIZARD.override(false);
    doCancelAndIgnore();
  }

  @Test
  public void cancelAndRerunOnNextStartup_legacy() throws Exception {
    NPW_FIRST_RUN_WIZARD.override(false);
    doCancelAndRerunOnNextStartup();
  }

  @Test
  public void cancelAndDontRerunOnNextStartup_legacy() throws Exception {
    NPW_FIRST_RUN_WIZARD.override(false);
    doCancelAndDontRerunOnNextStartup();
  }

  private void doCancelAndIgnore() throws Exception {
    FirstRunWizardFixture
      .find(guiTest.robot())
      .clickCancel()
      .findCancelPopup()
      .selectDoNotRerunOnNextStartup()
      .clickCancel();

    assertEquals(DUMMY_SDK_VERSION, getSdkUpdateVersion());
  }

  private void doCancelAndRerunOnNextStartup() throws Exception {
    FirstRunWizardFixture
      .find(guiTest.robot())
      .clickCancel()
      .findCancelPopup()
      .selectRerunOnNextStartup()
      .clickOK();

    assertEquals(DUMMY_SDK_VERSION, getSdkUpdateVersion());
  }

  public void doCancelAndDontRerunOnNextStartup() throws Exception {
    FirstRunWizardFixture
      .find(guiTest.robot())
      .clickCancel()
      .findCancelPopup()
      .selectDoNotRerunOnNextStartup()
      .clickOK();

    assertNotEquals(DUMMY_SDK_VERSION, getSdkUpdateVersion());
  }

  private static int getSdkUpdateVersion() {
    return AndroidFirstRunPersistentData.getInstance().getState().sdkUpdateVersion;
  }

  /**
   * The First Run Wizard updates the value of "sdkUpdateVersion" after it finishes sucessfully or is Cancel/Don't run on next Startup.
   * When it's Cancel/Ignore or Cancel/Run Next Time, the value does not change.
   * By setting a "random" dummy "sdkUpdateVersion" when the test starts, we can find if this fields was updated or not.
   */
  private static void setSdkUpdateVersion(int version) {
    AndroidFirstRunPersistentData.getInstance().getState().sdkUpdateVersion = version;
  }
}