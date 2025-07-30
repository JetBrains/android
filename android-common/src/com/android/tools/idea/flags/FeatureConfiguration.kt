/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.flags

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import java.util.regex.Pattern

/**
 * Feature Configuration that represent the flag configurations for the different
 * types of Studio builds.
 *
 * Configuration are ordered from less stable to more stable. A [com.android.flags.BooleanFlag]
 * targeting a particular [FeatureConfiguration] will also be enabled in all builds
 * that are using Configuration less stable than the target.
 *
 * e.g. if a Flag is targeting [PREVIEW], it will be enabled in:
 * - [INTERNAL]
 * - [NIGHTLY]
 * - [PREVIEW]
 */
enum class FeatureConfiguration(val stabilityLevel: Int) {
  /**
   * This configuration is never present in builds released outside Google.
   *
   * This is used when building Studio from the IDE, and will be used for dogfooding
   *
   * This configuration is the least stable and therefore any flag enabled in any configuration
   * is enabled in [INTERNAL]
   */
  INTERNAL(1),

  /**
   * This is the configuration for nightly builds.
   *
   * This should rarely be used, as we want most features to be directly enabled in the
   * main preview build.
   *
   * This can be used to enable some debugging features when we want developers to
   * try something via the nightly download.
   *
   * Flags targeting [PREVIEW] or [STABLE] are also enabled in this configuration
   */
  @Deprecated("Do Not Use, this is going to go away soon.")
  NIGHTLY(2),

  /**
   * This is the main preview configuration.
   *
   * Flags targeting [STABLE] are also enabled in this configuration
   *
   * It is published to the canary channel
   */
  PREVIEW(3),

  /**
   * This is the stable configuration.
   *
   * This is the most stable configuration, and therefore only Flags explicitly targeting
   * this Configuration will be enabled.
   *
   * It is published to the Beta/RC and Stable channels
   */
  STABLE(4);

  companion object {
    /**
     * The current flag configuration used by Studio.
     *
     * This is computed based on the name of the application
     */
    val current: FeatureConfiguration by lazy {
      computeConfiguration()
    }

    private fun versionNameContainsChannel(versionName: String?, channel: String): Boolean {
      return Pattern.compile("\\b$channel\\b", Pattern.CASE_INSENSITIVE).matcher(versionName ?: return false).find()
    }

    /**
     * Returns the [FeatureConfiguration] of the current build.
     *
     * This is based on the [com.intellij.openapi.application.ApplicationInfo] instance.
     */
    @VisibleForTesting
    fun computeConfiguration(): FeatureConfiguration {
      val versionName = when {
        ApplicationManager.getApplication() == null || ApplicationInfo.getInstance() == null -> "dev"
        else -> ApplicationInfo.getInstance().fullVersion
      }
      return getConfigurationFromVersionName(versionName)
    }

    @Suppress("DEPRECATION")
    @VisibleForTesting
    fun getConfigurationFromVersionName(versionName: String) : FeatureConfiguration = when {
      versionNameContainsChannel(versionName, "dev") -> INTERNAL
      versionNameContainsChannel(versionName, "nightly") -> NIGHTLY
      versionNameContainsChannel(versionName, "canary") -> PREVIEW
      else -> STABLE
    }
  }
}