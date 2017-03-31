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
package com.android.tools.idea.instantapp.provision;

import com.android.tools.idea.instantapp.InstantAppSdks;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProvisionBeforeRunTaskProvider}.
 */
public class ProvisionBeforeRunTaskProviderTest extends AndroidTestCase {
  @Mock AndroidRunConfigurationBase myRunConfiguration;
  private InstantAppSdks myInstantAppSdks;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myInstantAppSdks = IdeComponents.replaceServiceWithMock(InstantAppSdks.class);
    initMocks(this);
  }

  public void testTaskNotCreatedIfSdkNotDefined() {
    when(myInstantAppSdks.isInstantAppSdkEnabled()).thenReturn(false);
    assertNull(new ProvisionBeforeRunTaskProvider().createTask(myRunConfiguration));
  }

  public void testTaskCreatedIfModuleNull() {
    when(myInstantAppSdks.isInstantAppSdkEnabled()).thenReturn(true);
    JavaRunConfigurationModule runConfigurationModule = mock(JavaRunConfigurationModule.class);
    when(runConfigurationModule.getModule()).thenReturn(null);
    when(myRunConfiguration.getConfigurationModule()).thenReturn(runConfigurationModule);
    assertNotNull(new ProvisionBeforeRunTaskProvider().createTask(myRunConfiguration));
  }

  public void testProvisionSkippedWhenNotInstantApp() {
    assertTrue(new ProvisionBeforeRunTaskProvider() {
      @Override
      boolean isInstantAppContext(AndroidRunConfigurationBase runConfiguration) {
        return false;
      }
    }.executeTask(null, myRunConfiguration, null, null));
  }
}
