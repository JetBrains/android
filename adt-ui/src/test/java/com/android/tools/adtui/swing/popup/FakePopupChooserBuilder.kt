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

import com.intellij.openapi.ui.ListComponentUpdater
import com.intellij.openapi.ui.popup.IPopupChooserBuilder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.util.Computable
import com.intellij.util.Consumer
import com.intellij.util.Function
import com.intellij.util.Processor
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer

/**
 * A Fake implementation of [IPopupChooserBuilder] for testing.
 *
 * This class is implemented ad hoc. All unused methods will throw a [NotImplementedError].
 *
 * Note to contributors: As methods are implemented, please move them towards the top of the file.
 */
internal class FakePopupChooserBuilder<T>(
    private val factory: FakeJBPopupFactory,
    private val list: MutableList<out T>
) : IPopupChooserBuilder<T> {

  private var isMovable: Boolean? = null
  private var isRequestFocus: Boolean? = null
  private var callback: Consumer<in T>? = null
  private var renderer: ListCellRenderer<in T>? = null

  override fun createPopup(): JBPopup =
      FakeJBPopup(list, isMovable, isRequestFocus, renderer, callback).also(factory::addPopup)

  override fun setMovable(forceMovable: Boolean): IPopupChooserBuilder<T> {
    isMovable = forceMovable
    return this
  }

  override fun setRequestFocus(requestFocus: Boolean): IPopupChooserBuilder<T> {
    isRequestFocus = requestFocus
    return this
  }

  override fun setItemChosenCallback(callback: Consumer<in T>): IPopupChooserBuilder<T> {
    this.callback = callback
    return this
  }

  override fun setRenderer(renderer: ListCellRenderer<in T>?): IPopupChooserBuilder<T> {
    this.renderer = renderer
    return this
  }

  // PLEASE KEEP UNIMPLEMENTED METHODS ONLY BELOW THIS COMMENT

  override fun setItemsChosenCallback(
      callback: Consumer<in MutableSet<out T>>
  ): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setCancelOnClickOutside(cancelOnClickOutside: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setTitle(title: String): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setCouldPin(callback: Processor<in JBPopup>?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setResizable(forceResizable: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setDimensionServiceKey(key: String?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setUseDimensionServiceForXYLocation(use: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setCancelCallback(callback: Computable<Boolean>?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setAlpha(alpha: Float): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setAutoselectOnMouseMove(doAutoSelect: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setNamerForFiltering(namer: Function<in T, String>?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setFilterAlwaysVisible(state: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setAutoPackHeightOnFiltering(
      autoPackHeightOnFiltering: Boolean
  ): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setModalContext(modalContext: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setMinSize(dimension: Dimension?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun registerKeyboardAction(
      keyStroke: KeyStroke?,
      actionListener: ActionListener?
  ): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setAutoSelectIfEmpty(autoSelect: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setCancelKeyEnabled(enabled: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun addListener(listener: JBPopupListener?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setSettingButton(button: Component?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setMayBeParent(mayBeParent: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setCloseOnEnter(closeOnEnter: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setAdText(ad: String?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setAdText(ad: String?, alignment: Int): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setAdvertiser(advertiser: JComponent?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setCancelOnWindowDeactivation(
      cancelOnWindowDeactivation: Boolean
  ): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setSelectionMode(selection: Int): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setSelectedValue(preselection: T, shouldScroll: Boolean): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setAccessibleName(title: String?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setItemSelectedCallback(c: Consumer<in T>?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun withHintUpdateSupply(): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setFont(f: Font?): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun setVisibleRowCount(visibleRowCount: Int): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun withFixedRendererSize(dimension: Dimension): IPopupChooserBuilder<T> {
    TODO("Not yet implemented")
  }

  override fun getBackgroundUpdater(): ListComponentUpdater {
    TODO("Not yet implemented")
  }
}
