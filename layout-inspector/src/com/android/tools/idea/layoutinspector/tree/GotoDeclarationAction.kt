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

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.pom.Navigatable

/**
 * Action for navigating to the currently selected node in the layout inspector.
 */
object GotoDeclarationAction : AnAction("Go To Declaration") {
  override fun actionPerformed(event: AnActionEvent) {
    findNavigatable(event)?.navigate(true)
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = findNavigatable(event) != null
  }

  private fun findNavigatable(event: AnActionEvent): Navigatable? {
    return LayoutInspector.get(event)?.layoutInspectorModel?.let { findNavigatable(it) }
  }

  fun findNavigatable(model: InspectorModel): Navigatable? {
    val resourceLookup = model.resourceLookup
    val node = model.selection ?: return null
    return if (node is ComposeViewNode) {
      resourceLookup.findComposableNavigatable(node)
    }
    else {
      resourceLookup.findFileLocation(node)?.navigatable
    }
  }
}
