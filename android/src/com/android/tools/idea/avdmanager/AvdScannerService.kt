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
package com.android.tools.idea.avdmanager

import com.android.repository.api.RepoManager
import com.android.sdklib.deviceprovisioner.AbstractAvdScanner
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.sdk.AndroidSdkData
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@Service(Service.Level.APP)
class AvdScannerService(coroutineScope: CoroutineScope) : AbstractAvdScanner(coroutineScope), Disposable {
  private val localChangeListener = RepoManager.RepoLoadedListener {
    thisLogger().debug("SDK packages changed, rescanning AVDs")
    rescanAsync()
  }
  @Volatile private var currentRepoManager: RepoManager? = null
  private val sdkPathFlow = MutableSharedFlow<Path>(1)

  companion object {
    @JvmStatic
    val instance: AvdScannerService
      get() = service()
  }

  init {
    coroutineScope.launch {
      // Setup listener for the current SDK
      val sdkPath = IdeSdks.getInstance().androidSdkPath?.toPath()
      if (sdkPath != null) {
        sdkPathFlow.emit(sdkPath)
      }
      sdkPathFlow.collect { sdkPath ->
        currentRepoManager?.removeLocalChangeListener(localChangeListener)
        currentRepoManager = null

        val sdkData = AndroidSdkData.getSdkData(sdkPath)
        if (sdkData != null) {
          val progress = StudioLoggerProgressIndicator(AvdScannerService::class.java)
          val repoManager = sdkData.sdkHandler.getRepoManager(progress)
          repoManager.addLocalChangeListener(localChangeListener)
          currentRepoManager = repoManager
        } else {
          thisLogger().warn("Could not get SdkData for $sdkPath, AVD scanner won't receive SDK updates")
        }
      }
    }
  }

  fun onSdkPathChanged(newSdkPath: Path) {
    sdkPathFlow.tryEmit(newSdkPath)
    rescanAsync()
  }

  override fun scanAvds(): List<AvdInfo> = AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true)

  override fun logError(message: String, exception: Throwable) {
    thisLogger().error(message, exception)
  }

  override fun dispose() {
    currentRepoManager?.removeLocalChangeListener(localChangeListener)
    currentRepoManager = null
  }
}

class AvdScannerSdkEventListener : IdeSdks.AndroidSdkEventListener {
  override fun afterSdkPathChange(sdkPath: File, project: Project) {
    AvdScannerService.instance.onSdkPathChanged(sdkPath.toPath())
  }
}
