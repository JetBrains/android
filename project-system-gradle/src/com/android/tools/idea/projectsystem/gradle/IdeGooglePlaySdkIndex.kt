/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.android.tools.lint.checks.GooglePlaySdkIndex.Companion.GOOGLE_PLAY_SDK_INDEX_KEY
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.util.io.HttpRequests
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

object IdeGooglePlaySdkIndex : GooglePlaySdkIndex(getCacheDir()) {
  override fun readUrlData(url: String, timeout: Int): ByteArray? = HttpRequests
    .request(URL(url).toExternalForm())
    .connectTimeout(timeout)
    .readTimeout(timeout)
    .readBytes(null)
}

private fun getCacheDir(): Path? {
  if (ApplicationManager.getApplication().isUnitTestMode || GuiTestingService.getInstance().isGuiTestingMode) {
    return null
  }
  return Paths.get(PathManager.getSystemPath()).normalize().resolve(GOOGLE_PLAY_SDK_INDEX_KEY)
}
