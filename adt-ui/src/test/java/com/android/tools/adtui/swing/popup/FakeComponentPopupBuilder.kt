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
package com.android.tools.adtui.swing.popup

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ActiveIcon
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.MaskProvider
import com.intellij.openapi.ui.popup.MouseChecker
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Pair
import com.intellij.ui.ActiveComponent
import com.intellij.util.BooleanFunction
import com.intellij.util.Processor
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * A fake [ComponentPopupBuilder] for tests.
 */
open class FakeComponentPopupBuilder(
  private val factory: FakeJBPopupFactory,
  private val content: JComponent,
  private val preferableFocusComponent: JComponent?
) : ComponentPopupBuilder {
  private var isFocusable = false
  private var isRequestFocus = false

  // Section for implemented overrides
  override fun createPopup(): JBPopup = FakeComponentPopup(content, preferableFocusComponent, isFocusable, isRequestFocus).also(
    factory::addPopup)

  override fun setFocusable(focusable: Boolean) = this.also { isFocusable = focusable }

  override fun setRequestFocus(requestFocus: Boolean) = this.also { isRequestFocus = requestFocus }

  // Section for unimplemented overrides
  override fun setTitleIcon(icon: ActiveIcon) = this

  override fun setProject(project: Project?) = this

  override fun setCancelButton(cancelButton: IconButton) = this

  override fun setCancelOnOtherWindowOpen(cancelOnWindow: Boolean) = this

  override fun addListener(listener: JBPopupListener) = this

  override fun setCancelOnClickOutside(cancel: Boolean) = this

  override fun setLocateWithinScreenBounds(within: Boolean) = this

  override fun setCancelOnWindowDeactivation(cancelOnWindowDeactivation: Boolean) = this

  override fun setResizable(forceResizable: Boolean) = this

  override fun addUserData(`object`: Any?) = this

  override fun setTitle(title: String?) = this

  override fun setSettingButtons(button: Component) = this

  override fun setMayBeParent(mayBeParent: Boolean) = this

  override fun setAlpha(alpha: Float) = this

  override fun setAdText(text: String?) = this

  override fun setAdText(text: String?, textAlignment: Int) = this

  override fun setAdvertiser(advertiser: JComponent?) = this

  override fun setRequestFocusCondition(project: Project, condition: Condition<in Project>) = this

  override fun setLocateByContent(byContent: Boolean) = this

  override fun setModalContext(modal: Boolean) = this

  override fun setOkHandler(okHandler: Runnable?) = this

  override fun setBelongsToGlobalPopupStack(isInStack: Boolean) = this

  override fun setShowShadow(show: Boolean) = this

  override fun setCouldPin(callback: Processor<in JBPopup>?) = this

  override fun setMaskProvider(maskProvider: MaskProvider?) = this

  override fun setNormalWindowLevel(b: Boolean) = this

  override fun setMovable(forceMovable: Boolean) = this

  override fun setCancelCallback(shouldProceed: Computable<Boolean>) = this

  override fun setShowBorder(show: Boolean) = this

  override fun setMinSize(minSize: Dimension?) = this

  override fun setFocusOwners(focusOwners: Array<out Component>) = this

  override fun setCommandButton(commandButton: ActiveComponent) = this

  override fun setKeyboardActions(keyboardActions: MutableList<out Pair<ActionListener, KeyStroke>>) = this

  override fun setKeyEventHandler(handler: BooleanFunction<in KeyEvent>) = this

  override fun setDimensionServiceKey(project: Project?, key: String?, useForXYLocation: Boolean) = this

  override fun setCancelOnMouseOutCallback(shouldCancel: MouseChecker) = this

  override fun setCancelKeyEnabled(enabled: Boolean) = this
}