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
import com.android.tools.idea.testing.AndroidProjectRule;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public final class DeployTargetContextTest {
  @Rule
  public final TestRule myRule = AndroidProjectRule.inMemory();

  @Ignore("http://b/116011467")
  @Test
  public void getCurrentDeployTargetProviderSelectDeviceSnapshotComboBoxIsVisible() {
    DeployTargetContext context = new DeployTargetContext(() -> true);
    assertEquals(getDeployTargetProvider(TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX), context.getCurrentDeployTargetProvider());
  }

  @Test
  public void getCurrentDeployTargetProviderTargetSelectionModeEqualsDeviceAndSnapshotComboBox() {
    DeployTargetContext context = new DeployTargetContext(() -> false);
    context.setTargetSelectionMode(TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX);

    assertEquals(getDeployTargetProvider(TargetSelectionMode.SHOW_DIALOG), context.getCurrentDeployTargetProvider());
  }

  @Test
  public void getCurrentDeployTargetProviderProviderIsFound() {
    DeployTargetContext context = new DeployTargetContext(() -> false);
    assertEquals(getDeployTargetProvider(TargetSelectionMode.SHOW_DIALOG), context.getCurrentDeployTargetProvider());
  }

  @Test
  public void getCurrentDeployTargetProviderProviderIsNotFound() {
    DeployTargetContext context = new DeployTargetContext(() -> false);
    context.setTargetSelectionMode(TargetSelectionMode.FIREBASE_DEVICE_MATRIX);

    assertEquals(getDeployTargetProvider(TargetSelectionMode.SHOW_DIALOG), context.getCurrentDeployTargetProvider());
  }

  @NotNull
  private static Object getDeployTargetProvider(@NotNull TargetSelectionMode mode) {
    return DeployTargetContext.getDeployTargetProvider(DeployTargetProvider.getProviders(), mode.name()).orElseThrow(AssertionError::new);
  }
}
