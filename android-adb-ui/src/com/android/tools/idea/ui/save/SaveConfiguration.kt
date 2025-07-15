/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.save

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.XmlSerializerUtil

internal class SaveConfiguration : PersistentStateComponent<SaveConfiguration> {

  var saveLocation: String = SaveConfigurationResolver.DEFAULT_SAVE_LOCATION
  var filenameTemplate: String = ""
  var postSaveAction: PostSaveAction = PostSaveAction.OPEN

  override fun getState(): SaveConfiguration = this

  override fun loadState(state: SaveConfiguration) {
    XmlSerializerUtil.copyBean(state, this)
  }
}