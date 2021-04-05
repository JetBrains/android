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

private const val KEY_MERGED_SEMANTICS_TREE = "live.layout.inspector.tree.merged.semantics"
private const val DEFAULT_MERGED_SEMANTICS_TREE = false

private const val KEY_UNMERGED_SEMANTICS_TREE = "live.layout.inspector.tree.unmerged.semantics"
private const val DEFAULT_UNMERGED_SEMANTICS_TREE = false

private const val KEY_COMPOSE_AS_CALLSTACK = "live.layout.inspector.tree.compose.callstack"
private const val DEFAULT_COMPOSE_AS_CALLSTACK = true

private const val KEY_SUPPORT_LINES = "live.layout.inspector.tree.lines"
private const val DEFAULT_SUPPORT_LINES = true

/**
 * Global Tree settings.
 */
object TreeSettings {

  const val skipSystemNodesInAgent = false

  var hideSystemNodes: Boolean
    get() = get(KEY_HIDE_SYSTEM_NODES, DEFAULT_HIDE_SYSTEM_NODES)
    set(value) = set(KEY_HIDE_SYSTEM_NODES, value, DEFAULT_HIDE_SYSTEM_NODES)

  var composeAsCallstack: Boolean
    get() = get(KEY_COMPOSE_AS_CALLSTACK, DEFAULT_COMPOSE_AS_CALLSTACK)
    set(value) = set(KEY_COMPOSE_AS_CALLSTACK, value, DEFAULT_COMPOSE_AS_CALLSTACK)

  var mergedSemanticsTree: Boolean
    get() = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS.get() &&
            get(KEY_MERGED_SEMANTICS_TREE, DEFAULT_MERGED_SEMANTICS_TREE)
    set(value) = set(KEY_MERGED_SEMANTICS_TREE, value, DEFAULT_MERGED_SEMANTICS_TREE)

  var unmergedSemanticsTree: Boolean
    get() = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS.get() &&
            get(KEY_UNMERGED_SEMANTICS_TREE, DEFAULT_UNMERGED_SEMANTICS_TREE)
    set(value) = set(KEY_UNMERGED_SEMANTICS_TREE, value, DEFAULT_UNMERGED_SEMANTICS_TREE)

  var supportLines: Boolean
    get() = get(KEY_SUPPORT_LINES, DEFAULT_SUPPORT_LINES)
    set(value) = set(KEY_SUPPORT_LINES, value, DEFAULT_SUPPORT_LINES)

  private fun get(key: String, defaultValue: Boolean): Boolean =
    PropertiesComponent.getInstance().getBoolean(key, defaultValue)

  private fun set(key: String, value: Boolean, defaultValue: Boolean) =
    PropertiesComponent.getInstance().setValue(key, value, defaultValue)
}
