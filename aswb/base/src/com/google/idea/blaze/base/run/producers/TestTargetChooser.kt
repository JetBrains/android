/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.producers

import com.google.common.annotations.VisibleForTesting
import com.google.idea.blaze.base.dependencies.TargetInfo
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.getBestPopupPosition
import javax.swing.JList
import javax.swing.ListSelectionModel
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Interactive popup chooser on EDT for disambiguating multiple candidate test targets when creating a run configuration. */
object TestTargetChooser {

  @VisibleForTesting var testSelectionHook: (suspend (List<TargetInfo>) -> TargetInfo?)? = null

  /**
   * Prompts the user to choose from a list of candidate test targets.
   *
   * Fast-paths when there are 0 or 1 candidates without showing any UI. If the user closes or cancels the popup, returns null.
   */
  suspend fun chooseTarget(context: ConfigurationContext?, targets: List<TargetInfo>): TargetInfo? {
    if (targets.isEmpty()) {
      return null
    }
    if (targets.size == 1) {
      return targets[0]
    }
    val hook = testSelectionHook
    if (hook != null) {
      return hook(targets)
    }

    return withContext(Dispatchers.EDT) {
      suspendCancellableCoroutine { continuation ->
        val sortedTargets = targets.sortedBy { it.label().toString() }
        val renderer =
          object : ColoredListCellRenderer<TargetInfo>() {
            override fun customizeCellRenderer(
              list: JList<out TargetInfo>,
              value: TargetInfo?,
              index: Int,
              selected: Boolean,
              hasFocus: Boolean,
            ) {
              if (value != null) {
                append(value.label().toString())
              }
            }
          }

        val popup =
          JBPopupFactory.getInstance()
            .createPopupChooserBuilder(sortedTargets)
            .setTitle("Choose test target to run")
            .setMovable(true)
            .setResizable(false)
            .setRequestFocus(true)
            .setCancelOnWindowDeactivation(false)
            .setRenderer(renderer)
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setItemChosenCallback { target ->
              if (continuation.isActive) {
                continuation.resume(target)
              }
            }
            .createPopup()

        popup.addListener(
          object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
              if (!event.isOk && continuation.isActive) {
                continuation.resume(null)
              }
            }
          }
        )

        continuation.invokeOnCancellation { popup.cancel() }

        if (context != null) {
          popup.show(getBestPopupPosition(context.dataContext))
        } else {
          popup.showInFocusCenter()
        }
      }
    }
  }
}
