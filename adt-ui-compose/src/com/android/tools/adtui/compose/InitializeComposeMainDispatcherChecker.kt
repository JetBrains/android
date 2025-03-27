/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.compose

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager

/**
 * Initializes the MainDispatcherChecker in Compose; this must be called from the UI thread in
 * non-modal state. This is a workaround for a freeze in the UI thread that is triggered by using
 * Compose in a dialog before it has been used in a non-modal state.
 *
 * TODO(b/377386812): Delete this when the upstream bug is fixed.
 */
fun initializeComposeMainDispatcherChecker() {
  object : LifecycleOwner {
    override val lifecycle = LifecycleRegistry(this)

    init {
      // Setting this triggers initialization of MainDispatcherChecker.
      lifecycle.currentState = Lifecycle.State.STARTED
    }
  }
}

class OnStartup : AppLifecycleListener {
  override fun appFrameCreated(commandLineArgs: MutableList<String>) {
    ApplicationManager.getApplication().invokeLater {
      initializeComposeMainDispatcherChecker()
    }
  }
}
