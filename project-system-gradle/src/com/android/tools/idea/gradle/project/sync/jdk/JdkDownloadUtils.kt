/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.jdk

import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadUtil
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.ui.Messages
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.internal.jvm.inspection.JvmVendor
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.plugins.gradle.util.toJvmVendor

/**
 * Collection of utils to downloads JBR from Intellij [hosted server](https://download.jetbrains.com/jdk/feed/v1/jdks.json.xz) by
 * using existing platform implementation utils from [com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadUtil]
 */
object JdkDownloadUtils {

  /**
   * Downloads a JDK of the specified [version] from Jetbrains using JBR as vendor.
   *
   * @param project The project context for the download.
   * @param version The major version of the JDK to download (e.g., 17 for Java 17).
   * @return The downloaded [Sdk] object, or `null` if the download fails or is cancelled.
   */
  suspend fun downloadJdkWithVersion(project: Project, version: Int): Sdk? {
    val downloadedJdk = performDownloadJdkWithVersion(project, version)
    if (downloadedJdk == null) {
      withContext(Dispatchers.EDT) {
        val title = AndroidBundle.message("android.jdk.download.error.title")
        val message = AndroidBundle.message("android.jdk.download.error.message", version, "Jetbrains")
        Messages.showErrorDialog(project, message, title)
      }
    }
    return downloadedJdk
  }

  private suspend fun performDownloadJdkWithVersion(project: Project, version: Int): Sdk? {
    val (jdkItem, jdkHome) = pickJdkItemAndPathForVersion(project, version) ?: return null
    val downloadTask = JdkDownloadUtil.createDownloadTask(project, jdkItem, jdkHome) ?: return null
    val sdk = JdkDownloadUtil.createDownloadSdk(ExternalSystemJdkUtil.getJavaSdkType(), downloadTask)
    if (JdkDownloadUtil.downloadSdk(sdk)) {
      return sdk
    }
    return null
  }

  private suspend fun pickJdkItemAndPathForVersion(project: Project, version: Int): Pair<JdkItem, Path>? =
    JdkDownloadUtil.pickJdkItemAndPath(project) { jdkItem ->
      jdkItem.jdkMajorVersion == version && jdkItem.product.toJvmVendor()?.knownVendor == JvmVendor.KnownJvmVendor.JETBRAINS
    }
}