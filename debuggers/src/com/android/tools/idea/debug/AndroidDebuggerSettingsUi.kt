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
package com.android.tools.idea.debug

import com.intellij.openapi.options.ConfigurableUi
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JCheckBox

internal class AndroidDebuggerSettingsUi : ConfigurableUi<AndroidDebuggerSettings> {
  private val filterAndroidRuntimeClasses = JCheckBox("Do not step into Android internal classes")
  private val panel = BorderLayoutPanel().apply {
    addToLeft(filterAndroidRuntimeClasses)
  }

  override fun reset(settings: AndroidDebuggerSettings) {
    filterAndroidRuntimeClasses.isSelected = settings.filterAndroidRuntimeClasses
  }

  override fun isModified(settings: AndroidDebuggerSettings) =
    settings.filterAndroidRuntimeClasses != filterAndroidRuntimeClasses.isSelected

  override fun apply(settings: AndroidDebuggerSettings) {
    settings.filterAndroidRuntimeClasses = filterAndroidRuntimeClasses.isSelected
  }

  override fun getComponent() = panel
}
