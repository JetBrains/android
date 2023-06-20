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

const val KEY_RECOMPOSITIONS = "live.layout.inspector.tree.recompositions"
const val DEFAULT_RECOMPOSITIONS = false

/**
 * Miscellaneous tree settings.
 */
interface TreeSettings {

  var hideSystemNodes: Boolean
  var composeAsCallstack: Boolean
  var highlightSemantics: Boolean
  var supportLines: Boolean
  var showRecompositions: Boolean

  fun isInComponentTree(node: ViewNode): Boolean =
    !(hideSystemNodes && node.isSystemNode)
}

/**
 * [TreeSettings] with persistence.
 */
class InspectorTreeSettings(private val activeClient: () -> InspectorClient) : TreeSettings {
  override var hideSystemNodes: Boolean
    get() = hasCapability(Capability.SUPPORTS_SYSTEM_NODES) &&
            get(KEY_HIDE_SYSTEM_NODES, DEFAULT_HIDE_SYSTEM_NODES)
    set(value) = set(KEY_HIDE_SYSTEM_NODES, value, DEFAULT_HIDE_SYSTEM_NODES)

  override var composeAsCallstack: Boolean
    get() = get(KEY_COMPOSE_AS_CALLSTACK, DEFAULT_COMPOSE_AS_CALLSTACK)
    set(value) = set(KEY_COMPOSE_AS_CALLSTACK, value, DEFAULT_COMPOSE_AS_CALLSTACK)

  override var highlightSemantics = DEFAULT_HIGHLIGHT_SEMANTICS

  override var supportLines: Boolean
    get() = get(KEY_SUPPORT_LINES, DEFAULT_SUPPORT_LINES)
    set(value) = set(KEY_SUPPORT_LINES, value, DEFAULT_SUPPORT_LINES)

  override var showRecompositions: Boolean
    get() = hasCapability(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS) &&
            get(KEY_RECOMPOSITIONS, DEFAULT_RECOMPOSITIONS) &&
            StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_COUNTS.get()
    set(value) = set(KEY_RECOMPOSITIONS, value, DEFAULT_RECOMPOSITIONS)

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

/**
 * [TreeSettings] for [com.intellij.openapi.fileEditor.FileEditor]s, where persistence is handled by the
 * [com.intellij.openapi.fileEditor.FileEditorState] mechanism.
 */
class EditorTreeSettings(capabilities: Set<Capability>) : TreeSettings {
  override var hideSystemNodes: Boolean = DEFAULT_HIDE_SYSTEM_NODES && capabilities.contains(Capability.SUPPORTS_SYSTEM_NODES)
  override var composeAsCallstack: Boolean = DEFAULT_COMPOSE_AS_CALLSTACK
  override var highlightSemantics: Boolean = DEFAULT_HIGHLIGHT_SEMANTICS
  override var supportLines: Boolean = DEFAULT_SUPPORT_LINES
  override var showRecompositions: Boolean = DEFAULT_RECOMPOSITIONS
}
