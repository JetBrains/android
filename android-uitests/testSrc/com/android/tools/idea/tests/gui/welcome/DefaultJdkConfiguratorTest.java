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
package com.android.tools.idea.tests.gui.welcome;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.impl.DefaultJdkConfigurator;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to verify {@link DefaultJdkConfiguratorTest} is not used. This isn't a UI test, but it uses the UI-test framework because the
 * critical logic is bypassed in headless environments.
 */
@RunWith(GuiTestRemoteRunner.class)
public class DefaultJdkConfiguratorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void defaultJdkConfiguratorNotCalled() {
    assertThat(ApplicationManager.getApplication().getComponent(DefaultJdkConfigurator.class)).isNull();
    assertThat(PropertiesComponent.getInstance().getBoolean("defaultJdkConfigured", false)).isFalse();
  }
}
