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

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.pom.Navigatable

private const val VIEW_NOT_FOUND = "view.not.found"

/**
 * Action for navigating to the currently selected node in the layout inspector.
 */
object GotoDeclarationAction : AnAction("Go To Declaration") {
  @get:VisibleForTesting
  var lastAction: ListenableFuture<Unit>? = null

  override fun actionPerformed(event: AnActionEvent) {
    val inspector = LayoutInspector.get(event) ?: return
    lastAction = executeOnPooledThread {
      runReadAction {
        inspector.currentClient.stats.gotoSourceFromTreeActionMenu(event)
        val navigatable = findNavigatable(event) ?: return@runReadAction
        invokeLater { navigatable.navigate(true) }
        lastAction = null
      }
    }
  }

  private fun findNavigatable(event: AnActionEvent): Navigatable? =
    LayoutInspector.get(event)?.layoutInspectorModel?.let { findNavigatable(it) }

  @Slow
  fun findNavigatable(model: InspectorModel): Navigatable? {
    val resourceLookup = model.resourceLookup
    val node = model.selection ?: return null
    return if (node is ComposeViewNode) {
      resourceLookup.findComposableNavigatable(node)
    }
    else {
      val navigatable = resourceLookup.findFileLocation(node)?.navigatable
      val layout = node.layout?.name
      if (navigatable == null && node.viewId == null && layout != null && !node.isSystemNode) {
        val banner = InspectorBannerService.getInstance(model.project)
        banner?.setNotification(LayoutInspectorBundle.message(VIEW_NOT_FOUND, node.unqualifiedName, layout))
      }
      navigatable
    }
  }
}
