/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.intellij.openapi.project.Project
import javax.swing.JLabel

/**
 * The top level Logcat panel.
 */
internal class LogcatMainPanel(val project: Project, state: LogcatPanelConfig?) : JLabel(), SplittingTabsStateProvider {

  init {
    text = state?.text ?: "Child ${++count}"
  }

  override fun getState(): String = LogcatPanelConfig.toJson(LogcatPanelConfig(text))

  companion object {
    var count: Int = 0
  }
}