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
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.intellij.ide.util.PropertiesComponent

const val KEY_HIDE_SYSTEM_NODES = "live.layout.inspector.tree.hide.system"
const val DEFAULT_HIDE_SYSTEM_NODES = true

const val DEFAULT_HIGHLIGHT_SEMANTICS = false

const val KEY_COMPOSE_AS_CALLSTACK = "live.layout.inspector.tree.compose.callstack"
const val DEFAULT_COMPOSE_AS_CALLSTACK = true

const val KEY_SUPPORT_LINES = "live.layout.inspector.tree.lines"
const val DEFAULT_SUPPORT_LINES = true

/**
 * Miscellaneous tree settings.
 */
interface TreeSettings {

  var hideSystemNodes: Boolean
  var composeAsCallstack: Boolean
  var highlightSemantics: Boolean
  var supportLines: Boolean

  fun isInComponentTree(node: ViewNode): Boolean =
    !(hideSystemNodes && node.isSystemNode)

  companion object {
    const val skipSystemNodesInAgent = false
  }
}

/**
 * [TreeSettings] with persistence.
 */
class TreeSettingsImpl(private val activeClient: () -> InspectorClient) : TreeSettings {
  override var hideSystemNodes: Boolean
    get() = hasCapability(Capability.SUPPORTS_SYSTEM_NODES) &&
            get(KEY_HIDE_SYSTEM_NODES, DEFAULT_HIDE_SYSTEM_NODES)
    set(value) = set(KEY_HIDE_SYSTEM_NODES, value, DEFAULT_HIDE_SYSTEM_NODES)

  override var composeAsCallstack: Boolean
    get() = get(KEY_COMPOSE_AS_CALLSTACK, DEFAULT_COMPOSE_AS_CALLSTACK)
    set(value) = set(KEY_COMPOSE_AS_CALLSTACK, value, DEFAULT_COMPOSE_AS_CALLSTACK)

  override var highlightSemantics = DEFAULT_HIGHLIGHT_SEMANTICS
    get() = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS.get() && field

  override var supportLines: Boolean
    get() = get(KEY_SUPPORT_LINES, DEFAULT_SUPPORT_LINES)
    set(value) = set(KEY_SUPPORT_LINES, value, DEFAULT_SUPPORT_LINES)

  @Suppress("SameParameterValue")
  private fun hasCapability(capability: Capability): Boolean {
    val client = activeClient()
    if (!client.isConnected) {
      return true // Support settings access if we are currently disconnected
    }
    return client.capabilities.contains(capability)
  }

  private fun get(key: String, defaultValue: Boolean): Boolean =
    PropertiesComponent.getInstance().getBoolean(key, defaultValue)

  private fun set(key: String, value: Boolean, defaultValue: Boolean) =
    PropertiesComponent.getInstance().setValue(key, value, defaultValue)
}
