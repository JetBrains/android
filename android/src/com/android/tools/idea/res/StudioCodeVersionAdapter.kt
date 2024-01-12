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
package com.android.tools.idea.res

import com.android.tools.res.CodeVersionAdapter
import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.openapi.extensions.PluginId

/**
 * Android Studio implementation of [CodeVersionAdapter]. Returns the version of the Android IDE
 * plugin as the code version.
 */
internal class StudioCodeVersionAdapter private constructor() : CodeVersionAdapter() {
  override fun doGetCodeVersion(): String {
    val descriptor = getPlugin(PluginId.getId(ANDROID_PLUGIN_ID))
    return descriptor!!.version
  }

  companion object {
    private const val ANDROID_PLUGIN_ID = "org.jetbrains.android"

    @JvmStatic
    fun initialize() {
      setInstance(StudioCodeVersionAdapter())
    }
  }
}
