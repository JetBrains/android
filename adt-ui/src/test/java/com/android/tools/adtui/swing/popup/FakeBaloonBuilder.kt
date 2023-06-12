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
package com.android.tools.adtui.swing.popup

import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.BalloonBuilder
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.PositionTracker
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLayeredPane

/**
 * A fake [BalloonBuilder] for tests.
 */
class FakeBalloonBuilder(private val factory: FakeJBPopupFactory, private val component: JComponent): BalloonBuilder {
  private var requestFocus: Boolean = false

  override fun setBorderColor(color: Color) = this

  override fun setBorderInsets(insets: Insets?) = this

  override fun setFillColor(color: Color) = this

  override fun setHideOnClickOutside(hide: Boolean) = this

  override fun setHideOnKeyOutside(hide: Boolean) = this

  override fun setShowCallout(show: Boolean) = this

  override fun setCloseButtonEnabled(enabled: Boolean) = this

  override fun setFadeoutTime(fadeoutTime: Long) = this

  override fun setAnimationCycle(time: Int) = this

  override fun setHideOnFrameResize(hide: Boolean) = this

  override fun setHideOnLinkClick(hide: Boolean) = this

  override fun setClickHandler(listener: ActionListener?, closeOnClick: Boolean) = this

  override fun setCalloutShift(length: Int) = this

  override fun setPositionChangeXShift(positionChangeXShift: Int) = this

  override fun setPositionChangeYShift(positionChangeYShift: Int) = this

  override fun setHideOnAction(hideOnAction: Boolean) = this

  override fun setDialogMode(dialogMode: Boolean) = this

  override fun setTitle(title: String?) = this

  override fun setContentInsets(insets: Insets?) = this

  override fun setShadow(shadow: Boolean) = this

  override fun setSmallVariant(smallVariant: Boolean) = this

  override fun setLayer(layer: Balloon.Layer?) = this

  override fun setBlockClicksThroughBalloon(block: Boolean) = this

  override fun setRequestFocus(requestFocus: Boolean): FakeBalloonBuilder {
    this.requestFocus = requestFocus
    return this
  }

  override fun setPointerSize(size: Dimension?) = this

  override fun setCornerToPointerDistance(distance: Int) = this

  override fun setHideOnCloseClick(hideOnCloseClick: Boolean) = this

  override fun setDisposable(anchor: Disposable) = this

  override fun createBalloon() = FakeBalloon(component, requestFocus).also { factory.addBalloon(it) }
}

class FakeBalloon(
  val component: JComponent,
  private val requestFocus: Boolean
): Balloon {
  var target: Any? = null
    private set
  var preferredPosition: Balloon.Position? = null
    private set
  var ui: FakeUi? = null
    private set
  private var originalFocusOwner: Component? = null
  private var isDisposed = false
  private val listeners = mutableListOf<JBPopupListener>()

  override fun dispose() {
    if (!isDisposed) {
      Disposer.dispose(this)
      isDisposed = true
    }
  }

  override fun revalidate() {
    error("Not yet implemented")
  }

  override fun revalidate(tracker: PositionTracker<Balloon>) {
    error("Not yet implemented")
  }

  override fun show(tracker: PositionTracker<Balloon>?, preferredPosition: Balloon.Position?) {
    this.target = target
    this.preferredPosition = preferredPosition
    show()
  }

  override fun show(target: RelativePoint?, preferredPosition: Balloon.Position?) {
    this.target = target
    this.preferredPosition = preferredPosition
    show()
  }

  override fun show(pane: JLayeredPane?) {
    error("Not yet implemented")
  }

  override fun showInCenterOf(component: JComponent?) {
    error("Not yet implemented")
  }

  private fun show() {
    listeners.forEach { it.beforeShown(mock()) }
    component.setBounds(0, 0, 500, 1000)
    ui = FakeUi(component, createFakeWindow = true)
    if (requestFocus) {
      val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager() as? FakeKeyboardFocusManager
      originalFocusOwner = focusManager?.focusOwner
      focusManager?.focusOwner = component
    }
  }

  override fun getPreferredSize(): Dimension {
    error("Not yet implemented")
  }

  override fun setBounds(bounds: Rectangle?) {
    error("Not yet implemented")
  }

  override fun addListener(listener: JBPopupListener) {
    listeners.add(listener)
  }

  override fun hide() {
    hide(true)
  }

  override fun hide(ok: Boolean) {
    if (requestFocus) {
      val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager() as? FakeKeyboardFocusManager
      focusManager?.focusOwner = originalFocusOwner
    }

    component.isVisible = false
    dispose()
    listeners.forEach { it.onClosed(mock()) }
  }

  override fun setAnimationEnabled(enabled: Boolean) {
    error("Not yet implemented")
  }

  override fun wasFadedIn(): Boolean {
    error("Not yet implemented")
  }

  override fun wasFadedOut(): Boolean {
    error("Not yet implemented")
  }

  override fun isDisposed(): Boolean = isDisposed

  override fun setTitle(title: String?) {
    error("Not yet implemented")
  }
}
