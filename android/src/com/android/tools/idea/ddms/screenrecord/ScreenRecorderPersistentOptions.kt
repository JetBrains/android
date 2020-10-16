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
package com.android.tools.idea.ddms.screenrecord

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "ScreenRecorderOptions", storages = [Storage("screenRecorderOptions.xml")])
class ScreenRecorderPersistentOptions : PersistentStateComponent<ScreenRecorderPersistentOptions> {
  private val DEFAULT_BIT_RATE_MBPS = 4

  var bitRateMbps = DEFAULT_BIT_RATE_MBPS
  var resolutionWidth: Int = 0
  var resolutionHeight: Int = 0
  var showTaps = false

  companion object {
    @JvmStatic
    fun getInstance(): ScreenRecorderPersistentOptions {
      return ApplicationManager.getApplication().getService(ScreenRecorderPersistentOptions::class.java)
    }
  }

  override fun getState(): ScreenRecorderPersistentOptions {
    return this
  }

  override fun loadState(state: ScreenRecorderPersistentOptions) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
