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

import com.android.tools.idea.run.AndroidRunConfigurationBase;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mock;

import static com.android.tools.idea.instantapp.InstantApps.setInstantAppSdkLocation;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProvisionBeforeRunTaskProvider}.
 */
public class ProvisionBeforeRunTaskProviderTest extends AndroidTestCase {
  @Mock AndroidRunConfigurationBase myRunConfiguration;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
  }

  public void testTaskNotCreatedIfSdkNotDefined() {
    setInstantAppSdkLocation(null);
    assertNull(new ProvisionBeforeRunTaskProvider().createTask(myRunConfiguration));
  }
}
