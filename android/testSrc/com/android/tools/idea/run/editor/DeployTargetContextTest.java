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

import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider;
import com.android.tools.idea.testing.AndroidProjectRule;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public final class DeployTargetContextTest {
  @Rule
  public final TestRule myRule = AndroidProjectRule.inMemory();

  private List<DeployTargetProvider> myProviders;

  @Before
  public void initProviders() {
    myProviders = Arrays.asList(DeployTargetProvider.EP_NAME.getExtensions());
  }

  @Test
  public void getCurrentDeployTargetProviderSelectDeviceSnapshotComboBoxIsVisible() {
    DeployTargetContext context = new DeployTargetContext(() -> true, myProviders);

    Object provider = context.getCurrentDeployTargetProvider();

    assertEquals(DeployTargetProvider.EP_NAME.findExtension(DeviceAndSnapshotComboBoxTargetProvider.class), provider);
  }

  @Test
  public void getCurrentDeployTargetProviderTargetSelectionModeEqualsDeviceAndSnapshotComboBox() {
    DeployTargetContext context = new DeployTargetContext(() -> false, myProviders);
    context.setTargetSelectionMode(TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX);

    Object provider = context.getCurrentDeployTargetProvider();

    assertEquals(DeployTargetProvider.EP_NAME.findExtension(ShowChooserTargetProvider.class), provider);
  }

  @Test
  public void getCurrentDeployTargetProviderProviderIsFound() {
    DeployTargetContext context = new DeployTargetContext(() -> false, myProviders);

    Object provider = context.getCurrentDeployTargetProvider();

    assertEquals(DeployTargetProvider.EP_NAME.findExtension(ShowChooserTargetProvider.class), provider);
  }

  @Test
  public void getCurrentDeployTargetProviderProviderIsNotFound() {
    DeployTargetContext context = new DeployTargetContext(() -> false, myProviders);
    context.setTargetSelectionMode(TargetSelectionMode.FIREBASE_DEVICE_MATRIX);

    Object provider = context.getCurrentDeployTargetProvider();

    assertEquals(DeployTargetProvider.EP_NAME.findExtension(ShowChooserTargetProvider.class), provider);
  }
}
