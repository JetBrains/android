/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.flags.overrides

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import java.util.regex.Pattern

object IdeConfiguration {
  /**
   * The different IDE Configurations. These represents the flag configurations for the different
   * builds aof Studio we are doing.
   */
  enum class Configuration(val level: Int) {
    INTERNAL(1),
    NIGHTLY(2),
    PREVIEW(3),
    STABLE(4);
  }

  private fun versionNameContainsChannel(versionName: String?, channel: String): Boolean {
    return Pattern.compile("\\b$channel\\b", Pattern.CASE_INSENSITIVE).matcher(versionName ?: return false).find()
  }

  val configuration: Configuration by lazy {
    computeConfiguration()
  }

  /**
   * Returns the [Configuration] of the current build.
   *
   * This is based on the [ApplicationInfo] instance.
   */
  @VisibleForTesting
  fun computeConfiguration(): Configuration {
    val versionName = when {
      ApplicationManager.getApplication() == null || ApplicationInfo.getInstance() == null -> "dev"
      else -> ApplicationInfo.getInstance().fullVersion
    }
    return getConfigurationFromVersionName(versionName)
  }

  @VisibleForTesting
  fun getConfigurationFromVersionName(versionName: String) : Configuration = when {
    versionNameContainsChannel(versionName, "dev") -> Configuration.INTERNAL
    versionNameContainsChannel(versionName, "nightly") -> Configuration.NIGHTLY
    versionNameContainsChannel(versionName, "canary") -> Configuration.PREVIEW
    else -> Configuration.STABLE
  }
}