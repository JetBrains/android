/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.mocks

import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.ui.content.ContentManager
import org.mockito.Mockito.mock
import java.awt.Rectangle
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.JComponent

class MockToolWindow : ToolWindow {
  override fun isActive(): Boolean = false
  override fun activate(runnable: Runnable?) {}
  override fun activate(runnable: Runnable?, autoFocusContents: Boolean) {}
  override fun activate(runnable: Runnable?, autoFocusContents: Boolean, forced: Boolean) {}
  override fun isVisible() = true
  override fun show(runnable: Runnable?) { runnable?.run() }
  override fun hide(runnable: Runnable?) {}
  override fun getAnchor(): ToolWindowAnchor = mock(ToolWindowAnchor::class.java)
  override fun setAnchor(anchor: ToolWindowAnchor, runnable: Runnable?) {}
  override fun isSplitMode() = false
  override fun setSplitMode(split: Boolean, runnable: Runnable?) {}
  override fun isAutoHide() = false
  override fun setAutoHide(state: Boolean) {}
  override fun getType(): ToolWindowType = mock(ToolWindowType::class.java)
  override fun setType(type: ToolWindowType, runnable: Runnable?) {}
  override fun getIcon(): Icon = mock(Icon::class.java)
  override fun setIcon(icon: Icon) {}
  override fun getTitle() = ""
  override fun setTitle(title: String?) {}
  override fun getStripeTitle() = ""
  override fun setStripeTitle(title: String) {}
  override fun isAvailable() = true
  override fun setAvailable(available: Boolean, runnable: Runnable?) {}
  override fun setContentUiType(type: ToolWindowContentUiType, runnable: Runnable?) {}
  override fun setDefaultContentUiType(type: ToolWindowContentUiType) {}
  override fun getContentUiType(): ToolWindowContentUiType = mock(ToolWindowContentUiType::class.java)
  override fun installWatcher(contentManager: ContentManager?) {}
  override fun getComponent(): JComponent = mock(JComponent::class.java)
  override fun getContentManager(): ContentManager = mock(ContentManager::class.java)
  override fun setDefaultState(anchor: ToolWindowAnchor?, type: ToolWindowType?, floatingBounds: Rectangle?) {}
  override fun setToHideOnEmptyContent(hideOnEmpty: Boolean) {}
  override fun isToHideOnEmptyContent() = false
  override fun setShowStripeButton(show: Boolean) {}
  override fun showContentPopup(inputEvent: InputEvent?) {}
  override fun getReady(requestor: Any): ActionCallback = mock(ActionCallback::class.java)
  override fun isShowStripeButton() = false
  override fun isDisposed() = false
}