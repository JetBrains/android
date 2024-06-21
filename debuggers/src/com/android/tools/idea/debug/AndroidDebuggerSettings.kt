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
package com.android.tools.idea.debug

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SimpleConfigurable
import com.intellij.openapi.util.Getter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.settings.DebuggerSettingsCategory
import com.intellij.xdebugger.settings.XDebuggerSettings

@State(name = "AndroidDebuggerSettings", storages = [Storage("android.debug.xml")])
internal class AndroidDebuggerSettings :
  XDebuggerSettings<AndroidDebuggerSettings>("android_debugger"), Getter<AndroidDebuggerSettings> {
  var filterAndroidRuntimeClasses: Boolean = true

  companion object {
    fun getInstance(): AndroidDebuggerSettings {
      return XDebuggerUtil.getInstance().getDebuggerSettings(AndroidDebuggerSettings::class.java)
    }
  }

  override fun createConfigurables(category: DebuggerSettingsCategory): Collection<Configurable> =
    if (category == DebuggerSettingsCategory.STEPPING) {
      listOf(
        SimpleConfigurable.create(
          "reference.idesettings.debugger.android",
          "Android",
          AndroidDebuggerSettingsUi::class.java,
          this,
        )
      )
    } else listOf()

  override fun get() = this

  override fun getState() = this

  override fun loadState(state: AndroidDebuggerSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
