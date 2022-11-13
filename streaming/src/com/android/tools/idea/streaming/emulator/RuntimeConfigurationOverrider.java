/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator;

import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a mechanism for tests to override the default runtime configuration used by the Emulator tool window code.
 */
public class RuntimeConfigurationOverrider {
  private static final RuntimeConfiguration ourDefaultConfiguration = new RuntimeConfiguration();
  @Nullable private static RuntimeConfiguration ourOverridingConfiguration;

  @NotNull
  public static RuntimeConfiguration getRuntimeConfiguration() {
    RuntimeConfiguration override = ourOverridingConfiguration;
    return override == null ? ourDefaultConfiguration : override;
  }

  /**
   * Temporarily replaces the default runtime configuration used by the Emulator tool window code.
   *
   * @param override the overriding configuration
   */
  @VisibleForTesting
  static void overrideConfiguration(@NotNull RuntimeConfiguration override) {
    ourOverridingConfiguration = override;
  }

  /**
   * Restores the original default configuration.
   */
  @VisibleForTesting
  static void clearOverride() {
    ourOverridingConfiguration = null;
  }
}
