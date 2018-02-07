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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ChooseOptionsForNewFileStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.intellij.ide.util.PropertiesComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static org.junit.Assert.assertEquals;

/**
 * Tests that the defaults are populated correctly in Instant App Activities
 */
@RunIn(TestGroup.PROJECT_WIZARD)
@RunWith(GuiTestRunner.class)
public class InstantAppActivityDefaultsTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String TEST_DOMAIN = "aia.example.com";
  private static final String SAVED_COMPANY_DOMAIN = "SAVED_COMPANY_DOMAIN";

  @Before
  public void before() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    propertiesComponent.setValue(SAVED_COMPANY_DOMAIN, TEST_DOMAIN);
    SdkReplacer.replaceSdkLocationAndActivate(null, true);
  }

  @After
  public void after() {
    SdkReplacer.putBack();
  }


  @Test
  public void testDefaultUrlParamsPopulated() {
    ChooseOptionsForNewFileStepFixture<NewProjectWizardFixture> templateSettingsFixture = guiTest.welcomeFrame()
      .createNewProject()
      .getConfigureAndroidProjectStep()
      .enterApplicationName("TestApp").enterCompanyDomain(TEST_DOMAIN).enterPackageName("my.test")
      .wizard()
      .clickNext()
      .getConfigureFormFactorStep()
      .selectMinimumSdkApi(MOBILE, "23")
      .selectInstantAppSupport(MOBILE)
      .wizard()
      .clickNext()
      .clickNext()
      .clickNext().getChooseOptionsForNewFileStep();

    assertEquals(TEST_DOMAIN, templateSettingsFixture.getInstantAppsHost());
    assertEquals("/.*", templateSettingsFixture.getInstantAppsRoute());
    assertEquals("Path Pattern", templateSettingsFixture.getInstantAppsRouteType());

    templateSettingsFixture.wizard().clickCancel();
  }
}
