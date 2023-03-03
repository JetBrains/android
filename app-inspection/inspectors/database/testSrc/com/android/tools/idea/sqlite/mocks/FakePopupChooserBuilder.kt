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

import com.android.tools.idea.sqlite.model.SqliteDatabaseId
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
import org.mockito.Mockito.mock

open class FakePopupChooserBuilder : IPopupChooserBuilder<SqliteDatabaseId> {

  val mockPopUp: JBPopup = mock(JBPopup::class.java)
  var callback: Consumer<in SqliteDatabaseId>? = null

  override fun setRenderer(
    renderer: ListCellRenderer<in SqliteDatabaseId>?
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setItemChosenCallback(
    callback: Consumer<in SqliteDatabaseId>
  ): IPopupChooserBuilder<SqliteDatabaseId> {
    this.callback = callback
    return this
  }

  override fun setItemsChosenCallback(
    callback: Consumer<in MutableSet<out SqliteDatabaseId>>
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setCancelOnClickOutside(
    cancelOnClickOutside: Boolean
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setTitle(title: String): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setCouldPin(
    callback: Processor<in JBPopup>?
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setRequestFocus(requestFocus: Boolean): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setResizable(forceResizable: Boolean): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setMovable(forceMovable: Boolean): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setDimensionServiceKey(key: String?): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setUseDimensionServiceForXYLocation(
    use: Boolean
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setCancelCallback(
    callback: Computable<Boolean>?
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setAlpha(alpha: Float): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setAutoselectOnMouseMove(
    doAutoSelect: Boolean
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setNamerForFiltering(
    namer: Function<in SqliteDatabaseId, String>?
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setFilterAlwaysVisible(state: Boolean): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setAutoPackHeightOnFiltering(
    autoPackHeightOnFiltering: Boolean
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setModalContext(modalContext: Boolean): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun createPopup(): JBPopup = mockPopUp

  override fun setMinSize(dimension: Dimension?): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun registerKeyboardAction(
    keyStroke: KeyStroke?,
    actionListener: ActionListener?
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setAutoSelectIfEmpty(autoselect: Boolean): IPopupChooserBuilder<SqliteDatabaseId> =
    this

  override fun setCancelKeyEnabled(enabled: Boolean): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun addListener(listener: JBPopupListener?): IPopupChooserBuilder<SqliteDatabaseId> =
    this

  override fun setSettingButton(button: Component?): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setMayBeParent(mayBeParent: Boolean): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setCloseOnEnter(closeOnEnter: Boolean): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setAdText(ad: String?): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setAdText(ad: String?, alignment: Int): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setAdvertiser(advertiser: JComponent?): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setCancelOnWindowDeactivation(
    cancelOnWindowDeactivation: Boolean
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setSelectionMode(selection: Int): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setSelectedValue(
    preselection: SqliteDatabaseId?,
    shouldScroll: Boolean
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setAccessibleName(title: String?): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setItemSelectedCallback(
    c: Consumer<in SqliteDatabaseId>?
  ): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun withHintUpdateSupply(): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setFont(f: Font?): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun setVisibleRowCount(visibleRowCount: Int): IPopupChooserBuilder<SqliteDatabaseId> =
    this

  override fun withFixedRendererSize(dimension: Dimension): IPopupChooserBuilder<SqliteDatabaseId> = this

  override fun getBackgroundUpdater(): ListComponentUpdater = mock(ListComponentUpdater::class.java)
}
