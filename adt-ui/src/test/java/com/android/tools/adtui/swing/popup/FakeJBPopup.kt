/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/** A fake [JBPopup] for tests. */
open class FakeJBPopup<T>(
    val items: List<T>,
    val isMovable: Boolean? = false,
    val isRequestFocus: Boolean? = false,
    val title: String? = null,
    val renderer: ListCellRenderer<in T>? = null,
    private val callback: Consumer<in T>? = null,
) : AbstractPopup() {

  enum class ShowStyle {
    SHOW_UNDERNEATH_OF,
    SHOW,
    SHOW_IN_SCREEN_COORDINATES,
    SHOW_IN_BEST_POSITION_FOR,
    SHOW_IN_CENTER_OF,
    SHOW_IN_FOCUS_CENTER,
    SHOW_CENTERED_IN_CURRENT_WINDOW,
  }

  var showStyle: ShowStyle? = null
  var showArgs: List<Any>? = null
  val actions: List<AnAction> by lazy {
      if (items.isEmpty()) {
        return@lazy emptyList()
      }
      val result = mutableListOf<AnAction>()
      for (item in items) {
        item as? ActionItem ?: throw AssertionError("The popup is not an action popup")
        if (item.isPrependWithSeparator) {
          result.add(Separator.create(item.separatorText))
        }
        result.add(item.action)
      }
      return@lazy result
    }

  private var minSize: Dimension? = null
  private val registeredListeners = mutableListOf<JBPopupListener>()
  private var isVisible = false

  fun selectItem(item: T) {
    if (!items.contains(item)) {
      throw IllegalArgumentException(
          "No such item: $item. Available items: ${items.joinToString(",")}}")
    }
    callback?.consume(item)
  }

  override fun dispose() {}

  override fun showUnderneathOf(componentUnder: Component) {
    showStyle = ShowStyle.SHOW_UNDERNEATH_OF
    showArgs = listOf(componentUnder)
    isVisible = true
  }

  override fun show(point: RelativePoint) {
    showStyle = ShowStyle.SHOW
    showArgs = listOf(point)
    isVisible = true
  }

  override fun show(owner: Component) {
    showStyle = ShowStyle.SHOW
    showArgs = listOf(owner)
    isVisible = true
  }

  override fun showInScreenCoordinates(owner: Component, point: Point) {
    showStyle = ShowStyle.SHOW_IN_SCREEN_COORDINATES
    showArgs = listOf(owner, point)
    isVisible = true
  }

  override fun showInBestPositionFor(dataContext: DataContext) {
    showStyle = ShowStyle.SHOW_IN_BEST_POSITION_FOR
    showArgs = listOf(dataContext)
    isVisible = true
  }

  override fun showInBestPositionFor(editor: Editor) {
    showStyle = ShowStyle.SHOW_IN_BEST_POSITION_FOR
    showArgs = listOf(editor)
    isVisible = true
  }

  override fun showInCenterOf(component: Component) {
    showStyle = ShowStyle.SHOW_IN_CENTER_OF
    showArgs = listOf(component)
    isVisible = true
  }

  override fun showInFocusCenter() {
    showStyle = ShowStyle.SHOW_IN_FOCUS_CENTER
    showArgs = listOf()
    isVisible = true
  }

  override fun showCenteredInCurrentWindow(project: Project) {
    showStyle = ShowStyle.SHOW_CENTERED_IN_CURRENT_WINDOW
    showArgs = listOf(project)
    isVisible = true
  }

  override fun setMinimumSize(size: Dimension?) {
    minSize = size
  }

  override fun getMinimumSize(): Dimension? = minSize

  override fun isFocused(): Boolean {
    return true
  }

  override fun setSize(size: Dimension) {
  }

  override fun addListener(listener: JBPopupListener) {
    registeredListeners.add(listener)
  }

  override fun cancel(e: InputEvent?) {
    registeredListeners.forEach{ it.onClosed(LightweightWindowEvent(this))}
    isVisible = false
  }

  override fun getComponent(): JComponent? {
    return super.getComponent() ?: JPanel()
  }

  override fun getBestPositionFor(dataContext: DataContext): Point {
    return Point()
  }

  override fun setLocation(screenPoint: Point) {
  }

  override fun setRequestFocus(b: Boolean) {
    TODO("Not yet implemented")
  }

  override fun canShow(): Boolean = true

  override fun canClose(): Boolean {
    TODO("Not yet implemented")
  }

  override fun isVisible(): Boolean {
    return isVisible
  }

  override fun getContent(): JComponent {
    TODO("Not yet implemented")
  }

  override fun getSize(): Dimension {
    TODO("Not yet implemented")
  }

  override fun setCaption(title: String) {
    TODO("Not yet implemented")
  }

  override fun setCaptionIcon(icon: Icon?) {
    TODO("Not yet implemented")
  }

  override fun setCaptionIconPosition(left: Boolean) {
    TODO("Not yet implemented")
  }

  override fun isPersistent(): Boolean {
    TODO("Not yet implemented")
  }

  override fun isModalContext(): Boolean {
    TODO("Not yet implemented")
  }

  override fun isNativePopup(): Boolean {
    TODO("Not yet implemented")
  }

  override fun setUiVisible(visible: Boolean) {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> getUserData(userDataClass: Class<T>): T? {
    TODO("Not yet implemented")
  }

  override fun isCancelKeyEnabled(): Boolean {
    TODO("Not yet implemented")
  }

  override fun removeListener(listener: JBPopupListener) {
    TODO("Not yet implemented")
  }

  override fun isDisposed(): Boolean {
    TODO("Not yet implemented")
  }

  override fun getOwner(): Component {
    TODO("Not yet implemented")
  }

  override fun setFinalRunnable(runnable: Runnable?) {
    TODO("Not yet implemented")
  }

  override fun moveToFitScreen() {
    TODO("Not yet implemented")
  }

  override fun getLocationOnScreen(): Point {
    TODO("Not yet implemented")
  }

  override fun pack(width: Boolean, height: Boolean) {
    TODO("Not yet implemented")
  }

  override fun setDataProvider(dataProvider: DataProvider) {
    TODO("Not yet implemented")
  }

  override fun dispatchKeyEvent(e: KeyEvent): Boolean {
    TODO("Not yet implemented")
  }
}
