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
package com.android.tools.idea.testing.ui

import com.android.testutils.MockitoKt
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.TestActionEvent
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPopupMenu

class FakeActionPopupMenu(private val group: ActionGroup) : ActionPopupMenu {
  private val popup: JPopupMenu = MockitoKt.mock()

  override fun getComponent(): JPopupMenu = popup
  override fun getActionGroup(): ActionGroup = group
  override fun getPlace(): String = error("Not implemented")
  override fun setTargetComponent(component: JComponent) = error("Not implemented")
  override fun setDataContext(dataProvider: Supplier<out DataContext>) = error("Not implemented")

  fun getActions(): List<AnAction> {
    return group.getChildren(TestActionEvent()).toList()
  }
}
