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
package com.android.tools.idea.gradle.plugin

import com.android.Version
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.diagnostic.Logger

object AgpVersions {
  private val LOG: Logger
    get() = Logger.getInstance("#com.android.tools.idea.gradle.plugin.AgpVersions")

  @JvmStatic
  val studioFlagOverride: AgpVersion?
    get() {
      val override = StudioFlags.AGP_VERSION_TO_USE.get()
      if (override.isEmpty()) return null
      if (override.equals("stable", true)) {
        LOG.info(
          "Android Gradle Plugin version overridden to latest stable version ${Version.LAST_STABLE_ANDROID_GRADLE_PLUGIN_VERSION} by Studio flag ${StudioFlags.AGP_VERSION_TO_USE.id}=stable")
        return AgpVersion.parse(Version.LAST_STABLE_ANDROID_GRADLE_PLUGIN_VERSION)
      }
      val version = AgpVersion.tryParse(override) ?: throw IllegalStateException(
        "Invalid value '$override' for Studio flag ${StudioFlags.AGP_VERSION_TO_USE.id}. Expected Android Gradle plugin version (e.g. '8.0.2') or 'stable'")
      LOG.info(
        "Android Gradle Plugin version overridden to custom version $version by Studio flag ${StudioFlags.AGP_VERSION_TO_USE.id}=$override")
      return version
    }
}