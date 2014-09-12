/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.sdklib.devices.Storage;
import com.android.tools.idea.wizard.DynamicWizardPath;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Guides the user through setting up an emulator on first Android Studio run.
 */
public class SetupEmulatorPath extends DynamicWizardPath {
  // In UI we cannot use longs, so we need to pick a unit other then byte
  public static final Storage.Unit UI_UNITS = Storage.Unit.MiB;
  public static final String HAXM_URL = "http://www.intel.com/software/android/";

  private static final ScopedStateStore.Key<Integer> KEY_EMULATOR_MEMORY =
    ScopedStateStore.createKey("emulator.memory", ScopedStateStore.Scope.PATH, Integer.class);

  private ScopedStateStore.Key<Boolean> myIsCustomInstall;

  public SetupEmulatorPath(ScopedStateStore.Key<Boolean> isCustomInstall) {
    myIsCustomInstall = isCustomInstall;
  }

  @Override
  protected void init() {
    addStep(new MacEmulatorSettingsStep(KEY_EMULATOR_MEMORY));
    addStep(new LinuxEmulatorSettingsStep());
  }

  @Override
  public boolean isPathVisible() {
    return Objects.equal(Boolean.TRUE, getState().get(myIsCustomInstall));
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Setup Android emulator";
  }

  @Override
  public boolean performFinishingActions() {
    return false;
  }
}
