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
package com.android.tools.idea.tests.gui.npw;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.device.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test following scenarios for NPW => Phone and Tablet tab
 * 1. Verify following For all Navigation templates
 * 1.a. Verify Gradle sync is successful
 * 2.b. Build -> Make Project is successful
 */
@RunWith(GuiTestRemoteRunner.class)
public class VerifyNpwPhoneAndTabletNavigationTemplatesTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(20, TimeUnit.MINUTES);

  private List<String> expectedTemplates = List.of("Bottom Navigation Views Activity", "Navigation Drawer Views Activity");

  FormFactor selectMobileTab = FormFactor.MOBILE;



  @Test
  public void  testBottomNavigationViewsActivityTemplate() {
    boolean buildProjectStatus = NewProjectTestUtil.createNewProject(guiTest, selectMobileTab, expectedTemplates.get(0));
    assertThat(buildProjectStatus).isTrue();
  }

  @Test
  public void  testNavigationDrawerViewsActivityTemplate() {
    boolean buildProjectStatus = NewProjectTestUtil.createNewProject(guiTest, selectMobileTab, expectedTemplates.get(1));
    assertThat(buildProjectStatus).isTrue();
  }

}
