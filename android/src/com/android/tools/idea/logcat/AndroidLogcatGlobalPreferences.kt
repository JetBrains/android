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

import com.android.tools.idea.logcat.converters.DimensionConverter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import java.awt.Dimension

@State(name = "AndroidLogcatGlobalPreferences", storages = [Storage("androidLogcatGlobalPreferences.xml")])
class AndroidLogcatGlobalPreferences  private constructor(): PersistentStateComponent<AndroidLogcatGlobalPreferences> {

  var suppressedLogTags = HashSet<String>()

  @OptionTag(converter = DimensionConverter::class)
  var suppressedLogTagsDialogDimension = Dimension(300, 300)

  override fun getState(): AndroidLogcatGlobalPreferences = this

  override fun loadState(state: AndroidLogcatGlobalPreferences) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(): AndroidLogcatGlobalPreferences {
      return ApplicationManager.getApplication().getService(AndroidLogcatGlobalPreferences::class.java)
    }
  }
}