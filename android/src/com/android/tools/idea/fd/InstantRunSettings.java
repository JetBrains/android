/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class InstantRunSettings {
  public static final boolean SHOW_EXPERT_OPTIONS = Boolean.getBoolean("instant.run.expert.settings");

  /**
   * Returns whether instant run is enabled in the given project.
   * Note: Even if instant run is enabled for the project, instant run related information should not be accessed
   * unless {@link InstantRunGradleUtils#variantSupportsInstantRun} returns true.
   */
  public static boolean isInstantRunEnabled(@NotNull Project project) {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    return buildConfiguration.INSTANT_RUN;
  }

  /** Is showing toasts enabled in the given project */
  public static boolean isShowToastEnabled(@NotNull Project project) {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    return buildConfiguration.SHOW_TOAST;
  }

  /** Assuming instant run is enabled, does code patching require an activity restart in the given project? */
  public static boolean isRestartActivity(@NotNull Project project) {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    return buildConfiguration.RESTART_ACTIVITY;
  }

  /** If instant run is enabled, is cold swap mode enabled? */
  public static boolean isColdSwapEnabled(@NotNull Project project) {
    if (!SHOW_EXPERT_OPTIONS) {
      // Don't keep using stored settings on disk if the settings are invisible.
      // If users temporarily tried the flags, we don't want to keep those even
      // after they've gone back.
      return true;
    }
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    return buildConfiguration.COLD_SWAP;
  }

  /** If instant run is enabled, is cold swap mode enabled? */
  @NotNull
  public static ColdSwapMode getColdSwapMode(@NotNull Project project) {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    String value = buildConfiguration.COLD_SWAP_MODE;
    if (value == null) {
      return ColdSwapMode.DEFAULT;
    }
    return ColdSwapMode.fromValue(value, ColdSwapMode.DEFAULT);
  }

  /** Setting passed to Gradle indicating the scheme to be used for coldswap handling */
  @SuppressWarnings("SpellCheckingInspection")
  public enum ColdSwapMode {
    DEFAULT("Default", "default"),
    MULTI_APK("APK Splits", "multiapk"),
    MULTI_DEX("Multidex", "multidex"),
    NATIVE("Native", "native");

    ColdSwapMode(String display, String value) {
      this.display = display;
      this.value = value;
    }

    @NotNull
    public static ColdSwapMode fromValue(@NotNull String v, @NotNull ColdSwapMode def) {
      for (ColdSwapMode mode : values()) {
        if (mode.value.equals(v)) {
          return mode;
        }
      }

      return def;
    }

    public final String display;
    public final String value;

    @Override
    public String toString() {
      return display;
    }
  }
}
