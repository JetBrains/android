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
package com.android.tools.adtui.swing.popup

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.intellij.ui.PlaceProvider
import java.awt.Component
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPopupMenu
import org.junit.rules.ExternalResource
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * A rule that overrides [ActionManager] application service to create fake [JPopupMenu]s.
 */
class ActionPopupMenuRule: ExternalResource() {

  private val disposable = Disposer.newDisposable()
  private var lastPopup: FakeActionPopupMenu? = null

  val lastPopupActions: List<AnAction>
    get() = lastPopup?.lastPopupActions ?: emptyList()

  val popup: JPopupMenu?
    get() = lastPopup?.popup

  override fun before() {
    val actionManager = spy(ActionManager.getInstance())

    doAnswer { invocation ->
      FakeActionPopupMenu(invocation.getArgument(0), invocation.getArgument(1)).also { lastPopup = it }
    }
    .whenever(actionManager)
    .createActionPopupMenu(ArgumentMatchers.anyString(), any())

    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, actionManager, disposable)
  }

  override fun after() {
    Disposer.dispose(disposable)
  }

  private class FakeActionPopupMenu(
    private val place: String,
    private val group: ActionGroup,
  ) : ActionPopupMenu {
    private var dataProvider: Supplier<out DataContext>? = null
    var popup: FakePopupMenu? = null

    val lastPopupActions: List<AnAction>
      get() = popup?.lastPopupActions ?: emptyList()

    override fun getComponent(): JPopupMenu {
      val result = this.popup ?: FakePopupMenu(place, group, dataProvider).also { this.popup = it }
      return result
    }

    override fun getPlace(): String = place

    override fun getActionGroup(): ActionGroup = group

    override fun setTargetComponent(component: JComponent) {
      setDataContext { DataManager.getInstance().getDataContext(component) }
    }

    override fun setDataContext(dataProvider: Supplier<out DataContext>) {
      this.dataProvider = dataProvider
    }
  }

  private class FakePopupMenu(
    private val place: String,
    private val group: ActionGroup,
    private val dataProvider: Supplier<out DataContext>?
  ) : JPopupMenu(), PlaceProvider {
    private var _visible = false

    override fun setVisible(visible: Boolean) {
      _visible = visible
    }

    override fun isVisible(): Boolean = _visible

    override fun getPlace(): String = place

    override fun show(component: Component, x: Int, y: Int) {
      _visible = true
      val dataContext = dataProvider?.get() ?: DataManager.getInstance().getDataContext(component)
      val event = createActionEvent(group, dataContext)
      val children = group.getChildren(event).toList()
      children.forEach {
        it.update(createActionEvent(it, dataContext))
      }
      lastPopupActions = children
    }

    var lastPopupActions: List<AnAction> = emptyList()
      private set

    private fun createActionEvent(action: AnAction, dataContext: DataContext): AnActionEvent {
      return AnActionEvent.createEvent(
        dataContext,
        action.templatePresentation.clone(),
        place,
        ActionUiKind.NONE,
        null,
      )
    }
  }
}
