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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.util.PropertiesComponent

const val KEY_HIDE_SYSTEM_NODES = "live.layout.inspector.tree.hide.system"
const val DEFAULT_HIDE_SYSTEM_NODES = true

private const val KEY_COMPOSE_AS_CALLSTACK = "live.layout.inspector.tree.compose.callstack"
private const val DEFAULT_COMPOSE_AS_CALLSTACK = true

private const val KEY_INCLUDE_DRAWABLES_IN_CALLSTACK = "live.layout.inspector.tree.compose.drawables"
private const val DEFAULT_INCLUDE_DRAWABLES_IN_CALLSTACK = true

/**
 * Global Tree settings.
 */
object TreeSettings {

  /**
   * The units to be used for all attributes with dimension values.
   */
  var hideSystemNodes: Boolean
    get() = get(KEY_HIDE_SYSTEM_NODES, DEFAULT_HIDE_SYSTEM_NODES)
    set(value) = set(KEY_HIDE_SYSTEM_NODES, value, DEFAULT_HIDE_SYSTEM_NODES)

  var composeAsCallstack: Boolean
    get() = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPONENT_TREE_OPTIONS.get() &&
            get(KEY_COMPOSE_AS_CALLSTACK, DEFAULT_COMPOSE_AS_CALLSTACK)
    set(value) = set(KEY_COMPOSE_AS_CALLSTACK, value, DEFAULT_COMPOSE_AS_CALLSTACK)

  var composeDrawablesInCallstack: Boolean
    get() = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPONENT_TREE_OPTIONS.get() &&
            get (KEY_INCLUDE_DRAWABLES_IN_CALLSTACK, DEFAULT_INCLUDE_DRAWABLES_IN_CALLSTACK)
    set(value) = set(KEY_INCLUDE_DRAWABLES_IN_CALLSTACK, value, DEFAULT_INCLUDE_DRAWABLES_IN_CALLSTACK)

  private fun get(key: String, defaultValue: Boolean): Boolean =
    PropertiesComponent.getInstance().getBoolean(key, defaultValue)

  private fun set(key: String, value: Boolean, defaultValue: Boolean) =
    PropertiesComponent.getInstance().setValue(key, value, defaultValue)
}
