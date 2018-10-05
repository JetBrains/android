/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.android.tools.idea.testing.AndroidProjectRule;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public final class DeployTargetProviderTest {
  @Rule
  public final TestRule myRule = AndroidProjectRule.inMemory();

  private List<DeployTargetProvider> myProviders;

  @Before
  public void initProviders() {
    myProviders = Arrays.asList(DeployTargetProvider.EP_NAME.getExtensions());
  }

  @Test
  public void filterOutDeviceAndSnapshotComboBoxProviderSelectDeviceSnapshotComboBoxIsVisible() {
    assertSame(myProviders, DeployTargetProvider.filterOutDeviceAndSnapshotComboBoxProvider(myProviders, true));
  }

  @Test
  public void filterOutDeviceAndSnapshotComboBoxProviderIsntVisible() {
    Object providers = Arrays.asList(
      DeployTargetProvider.EP_NAME.findExtension(ShowChooserTargetProvider.class),
      DeployTargetProvider.EP_NAME.findExtension(UsbDeviceTargetProvider.class),
      DeployTargetProvider.EP_NAME.findExtension(EmulatorTargetProvider.class)
    );

    assertEquals(providers, DeployTargetProvider.filterOutDeviceAndSnapshotComboBoxProvider(myProviders, false));
  }
}
