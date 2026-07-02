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

package com.android.tools.idea.util

import com.android.tools.idea.downloads.AndroidProfilerDownloader

object LocalInstallerPathManager {
  @JvmStatic
  fun getLocalInstaller(): String {
    if (StudioPathManager.isRunningFromSources()) {
      // Development mode
      return StudioPathManager.resolvePathFromSourcesRoot("bazel-bin/tools/base/deploy/installer/android-installer").toString()
    }
    // JetBrains patch: we don't bundle the installer binary like AS does, it comes from the downloaded
    // android-plugin-resources component, so grab it via the downloader instead of a static home path
    val downloader = AndroidProfilerDownloader.getInstance()
    downloader.makeSureComponentIsInPlace()
    return downloader.getHostDir("plugins/android/resources/installer").path
  }
}
