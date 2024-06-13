/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.diagnostics

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Paths

/**
 * Looks for the existence of system32\\ucrtbase.dll to check whether Universal C Runtime for Windows is installed
 */
class WindowsCRuntimeChecker : ProjectActivity {
  init {
    if (!SystemInfo.isWindows || !StudioFlags.WINDOWS_UCRT_CHECK_ENABLED.get()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    withContext(Dispatchers.IO) {
      checkCRT()
    }
  }

  private fun checkCRT() {
    val dllPath = Paths.get(System.getenv("SystemRoot"), "system32", "ucrtbase.dll")
    if (!dllPath.toFile().exists()) {
      val systemHealthMonitor = AndroidStudioSystemHealthMonitor.getInstance()
      systemHealthMonitor?.showNotification(
        "windows.ucrt.warn.message", AndroidStudioSystemHealthMonitor.detailsAction(
          "https://support.microsoft.com/en-ca/help/2999226/update-for-universal-c-runtime-in-windows"
        )
      )
    }
  }
}
