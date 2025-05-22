/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui.rendering

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.layoutinspector.common.SelectViewAction
import com.android.tools.idea.layoutinspector.tree.GotoDeclarationAction
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPopupMenu
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FakeActionPopupMenu(private val group: ActionGroup) : ActionPopupMenu {
  val popup: JPopupMenu = mock()

  override fun getComponent(): JPopupMenu = popup

  override fun getActionGroup(): ActionGroup = group

  override fun getPlace(): String = error("Not implemented")

  override fun setTargetComponent(component: JComponent) = error("Not implemented")

  override fun setDataContext(dataProvider: Supplier<out DataContext>) = error("Not implemented")

  fun assertSelectViewActionAndGotoDeclaration(vararg expected: Long) {
    val event: AnActionEvent = mock()
    whenever(event.actionManager).thenReturn(ActionManager.getInstance())
    val actions = group.getChildren(event)
    assertThat(actions.size).isEqualTo(2)
    assertThat(actions[0]).isInstanceOf(DropDownAction::class.java)
    val selectActions = (actions[0] as DropDownAction).getChildren(event)
    val selectedViewsIds =
      selectActions.toList().filterIsInstance(SelectViewAction::class.java).map { it.view.drawId }
    assertThat(selectedViewsIds).containsExactlyElementsIn(expected.toList())
    assertThat(actions[1]).isEqualTo(GotoDeclarationAction)
  }
}
