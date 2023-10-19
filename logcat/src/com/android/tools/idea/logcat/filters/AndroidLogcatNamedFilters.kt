/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.VisibleForTesting

/** A [PersistentStateComponent] that stores named filters. */
@State(name = "AndroidLogcatNamedFilters", storages = [Storage("androidLogcatNamedFilters.xml")])
internal class AndroidLogcatNamedFilters @VisibleForTesting constructor() :
  PersistentStateComponent<AndroidLogcatNamedFilters> {
  var namedFilters = mutableMapOf<String, String>()

  override fun getState(): AndroidLogcatNamedFilters = this

  override fun loadState(state: AndroidLogcatNamedFilters) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(): AndroidLogcatNamedFilters =
      ApplicationManager.getApplication().getService(AndroidLogcatNamedFilters::class.java)
  }
}
