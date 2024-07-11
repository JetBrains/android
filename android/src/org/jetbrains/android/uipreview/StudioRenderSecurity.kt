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
package org.jetbrains.android.uipreview

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.rendering.StudioRenderSecurityManager
import com.android.tools.rendering.security.AllowAllRenderSandbox
import com.android.tools.rendering.security.PreCheckRenderSandboxDelegate
import com.android.tools.rendering.security.RenderSandbox
import com.android.tools.rendering.security.RenderSecurity
import com.android.tools.rendering.security.RenderSecurityManager
import com.intellij.openapi.diagnostic.Logger

/**
 * Android Studio specific implementation of [RenderSecurity] that supports both [StudioRenderSecurityManager] and [StudioRenderSandbox].
 */
@Suppress("VisibleForTests")
class StudioRenderSecurity(val sdkPath: String?, val projectPath: String?, val appTempDir: String?) : RenderSecurity {

  private val securityManager: StudioRenderSecurityManager?
  private val sandbox: RenderSandbox?
  private val useSandbox = StudioFlags.RENDER_SANDBOX.get()
  private var previousSandbox: RenderSandbox? = null

  init {
    securityManager = StudioRenderSecurityManager(sdkPath, projectPath, false)
    securityManager.setLogger(
      LogWrapper(Logger.getInstance(StudioRenderSecurityManager::class.java)).alwaysLogAsDebug(true).allowVerbose(false)
    )
    securityManager.setAppTempDir(appTempDir)

    val baseSandbox = StudioRenderSandbox(sdkPath, projectPath, appTempDir)
    sandbox = PreCheckRenderSandboxDelegate(baseSandbox, { RenderSecurityManager.sEnabled })
  }

  override fun activate(credential: Any) {
    if (useSandbox) {
      if (sandbox != null) {
        previousSandbox = RenderSandbox.setRenderSandbox(sandbox)
      }
    } else {
      securityManager?.setActive(true, credential)
    }
  }

  override fun deactivate(credential: Any) {
    if (useSandbox) {
      if (sandbox != null) {
        RenderSandbox.setRenderSandbox(previousSandbox ?: AllowAllRenderSandbox)
      }
    } else {
      securityManager?.dispose(credential)
    }
  }
}
