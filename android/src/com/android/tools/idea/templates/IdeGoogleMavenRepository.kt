/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GoogleMavenRepository.Companion.MAVEN_GOOGLE_CACHE_DIR_KEY
import com.android.tools.idea.ui.GuiTestingService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.PathUtil
import com.intellij.util.io.HttpRequests
import java.io.File
import java.net.URL

/** A [GoogleMavenRepository] that uses IDE mechanisms (including proxy config) to download data. */
object IdeGoogleMavenRepository : GoogleMavenRepository(getCacheDir()) {
  @Slow
  override fun readUrlData(url: String, timeout: Int) = HttpRequests
    .request(URL(url).toExternalForm())
    .connectTimeout(timeout)
    .readTimeout(timeout)
    .readBytes(null)

  override fun error(throwable: Throwable, message: String?) {
    Logger.getInstance(IdeGoogleMavenRepository::class.java).warn(message, throwable)
  }
}

/** A [GoogleMavenRepository] for only cached data. */
object OfflineIdeGoogleMavenRepository : GoogleMavenRepository(getCacheDir(), useNetwork = false) {
  override fun readUrlData(url: String, timeout: Int): ByteArray? = throw UnsupportedOperationException()

  override fun error(throwable: Throwable, message: String?) {
    Logger.getInstance(OfflineIdeGoogleMavenRepository::class.java).warn(message, throwable)
  }
}

private fun getCacheDir(): File? =
    if (ApplicationManager.getApplication().isUnitTestMode || GuiTestingService.getInstance().isGuiTestingMode)
      null
    else
      File(PathUtil.getCanonicalPath(PathManager.getSystemPath()), MAVEN_GOOGLE_CACHE_DIR_KEY)
