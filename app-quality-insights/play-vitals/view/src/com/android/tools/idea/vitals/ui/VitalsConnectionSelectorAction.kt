/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.vitals.ui

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopup
import java.awt.Component
import java.awt.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.TestOnly

class VitalsConnectionSelectorAction(
  private val flow: StateFlow<Selection<VitalsConnection>>,
  private val scope: CoroutineScope,
  private val onSelected: (VitalsConnection) -> Unit,
  @TestOnly private val getLocationOnScreen: Component.() -> Point = Component::getLocationOnScreen,
) : DropDownAction(null, null, null) {
  private lateinit var popup: JBPopup

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun displayTextInToolbar() = true

  override fun update(e: AnActionEvent) {
    e.presentation.setText(
      flow.value.selected?.let { "${it.displayName} [${it.appId}]" } ?: "No apps available",
      false,
    )
  }

  override fun actionPerformed(eve: AnActionEvent) {
    popup =
      VitalsConnectionSelectorPopup(flow.value, scope) {
          popup.closeOk(null)
          onSelected(it)
        }
        .asPopup()
    val owner = eve.inputEvent!!.component
    val location = getLocationOnScreen(owner)
    location.translate(0, owner.height)
    popup.showInScreenCoordinates(owner, location)
  }
}
