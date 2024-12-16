/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.adtui.status.InformationPopup
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.util.Alarm
import java.awt.event.MouseEvent
import javax.swing.JComponent

fun ProjectRule.createFakeActionEvent(): AnActionEvent {
  val dataContext = SimpleDataContext.getProjectContext(project)
  val mouseEvent = createFakeMouseEvent()
  return createEvent(dataContext, Presentation(), "", ActionUiKind.NONE, mouseEvent)
}

fun createFakeMouseEvent(): MouseEvent =
  MouseEvent(object : JComponent() {}, 0, 0, 0, 0, 0, 1, true, MouseEvent.BUTTON1)

fun createFakeAlarm(onAddRequest: (Int) -> Unit = {}, onCancelAllRequest: () -> Unit = {}) =
  object : Alarm() {
    override fun cancelAllRequests(): Int {
      onCancelAllRequest()
      return super.cancelAllRequests()
    }

    override fun addRequest(request: Runnable, delayMillis: Int) {
      request.run()
      onAddRequest(delayMillis)
    }
  }

fun createFakePopup(
  onHidePopup: () -> Unit = {},
  onShowPopup: () -> Unit = {},
  onMouseEnterCallback: () -> Unit = {},
  isPopupVisible: Boolean = false,
): InformationPopup =
  object : InformationPopup {
    override val popupComponent: JComponent = object : JComponent() {}
    override var onMouseEnteredCallback: () -> Unit = onMouseEnterCallback

    override fun hidePopup() = onHidePopup()

    override fun showPopup(disposableParent: Disposable, owner: JComponent) = onShowPopup()

    override fun isVisible(): Boolean = isPopupVisible

    override fun dispose() {}
  }
