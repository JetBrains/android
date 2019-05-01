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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.impl.DefaultJdkConfigurator;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to verify {@link DefaultJdkConfiguratorTest} is not used. This is done as an ui tests because the headless-implementation-class
 * is set to null.
 */
@RunWith(GuiTestRemoteRunner.class)
public class DefaultJdkConfiguratorTest {
  /**
   * Confirm {@link DefaultJdkConfigurator} is not a registered Application component and that the defaultJdkConfigured property is not set.
   */
  @Test
  public void testDefaultJdkConfiguratorNotCalled() {
    Application application = ApplicationManager.getApplication();
    assertThat(application.getComponent(DefaultJdkConfigurator.class)).isNull();
    assertThat(PropertiesComponent.getInstance().getBoolean("defaultJdkConfigured", false)).isFalse();
  }
}
