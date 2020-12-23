/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.tree

import com.intellij.ide.util.PropertiesComponent

const val KEY_HIDE_SYSTEM_NODES = "live.layout.inspector.tree.hide.system"
const val DEFAULT_HIDE_SYSTEM_NODES = true

/**
 * Global Tree settings.
 */
object TreeSettings {

  /**
   * The units to be used for all attributes with dimension values.
   */
  var hideSystemNodes: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(KEY_HIDE_SYSTEM_NODES, DEFAULT_HIDE_SYSTEM_NODES)
    set(value) {
      PropertiesComponent.getInstance().setValue(KEY_HIDE_SYSTEM_NODES, value, DEFAULT_HIDE_SYSTEM_NODES)
    }
}
