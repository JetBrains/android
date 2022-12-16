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
package com.android.tools.idea.logcat.settings

import com.android.tools.idea.logcat.filters.LogcatFilter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

private const val DEFAULT_BUFFER_SIZE = 1024 * 1024
// Append a space at the end so user can press Ctrl+Space to get completions
private const val DEFAULT_FILTER = "${LogcatFilter.MY_PACKAGE} "

@State(name = "AndroidLogcatSettings", storages = [Storage("androidLogcatSettings.xml")])
internal data class AndroidLogcatSettings(
  var bufferSize: Int = DEFAULT_BUFFER_SIZE,
  var defaultFilter: String = DEFAULT_FILTER,
  var mostRecentlyUsedFilterIsDefault: Boolean = false,
  var filterHistoryAutocomplete: Boolean = false,
  var namedFiltersEnabled: Boolean = false,
  var ignoredTags: Set<String> = emptySet(),
) : PersistentStateComponent<AndroidLogcatSettings> {

  companion object {
    fun getInstance(): AndroidLogcatSettings = ApplicationManager.getApplication().getService(AndroidLogcatSettings::class.java)
  }

  override fun getState(): AndroidLogcatSettings = this

  override fun loadState(state: AndroidLogcatSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
