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

import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.intellij.openapi.ui.ListComponentUpdater
import com.intellij.openapi.ui.popup.IPopupChooserBuilder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.util.Computable
import com.intellij.util.Consumer
import com.intellij.util.Function
import com.intellij.util.Processor
import org.mockito.Mockito.mock
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionListener
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer

open class MockPopupChooserBuilder: IPopupChooserBuilder<SqliteDatabase> {

  val mockPopUp: JBPopup = mock(JBPopup::class.java)

  override fun setRenderer(renderer: ListCellRenderer<*>?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setItemChosenCallback(callback: Consumer<in SqliteDatabase>): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setItemsChosenCallback(callback: Consumer<in MutableSet<SqliteDatabase>>): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setCancelOnClickOutside(cancelOnClickOutside: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setTitle(title: String): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setCouldPin(callback: Processor<in JBPopup>?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setRequestFocus(requestFocus: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setResizable(forceResizable: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setMovable(forceMovable: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setDimensionServiceKey(key: String?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setUseDimensionServiceForXYLocation(use: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setCancelCallback(callback: Computable<Boolean>?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setAlpha(alpha: Float): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setAutoselectOnMouseMove(doAutoSelect: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setNamerForFiltering(namer: Function<in SqliteDatabase, String>?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setAutoPackHeightOnFiltering(autoPackHeightOnFiltering: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setModalContext(modalContext: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun createPopup(): JBPopup = mockPopUp

  override fun setMinSize(dimension: Dimension?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun registerKeyboardAction(keyStroke: KeyStroke?, actionListener: ActionListener?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setAutoSelectIfEmpty(autoselect: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setCancelKeyEnabled(enabled: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun addListener(listener: JBPopupListener?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setSettingButton(button: Component?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setMayBeParent(mayBeParent: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setCloseOnEnter(closeOnEnter: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setAdText(ad: String?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setAdText(ad: String?, alignment: Int): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setCancelOnWindowDeactivation(cancelOnWindowDeactivation: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setSelectionMode(selection: Int): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setSelectedValue(preselection: SqliteDatabase?, shouldScroll: Boolean): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setAccessibleName(title: String?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setItemSelectedCallback(c: Consumer<in SqliteDatabase>?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun withHintUpdateSupply(): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setFont(f: Font?): IPopupChooserBuilder<SqliteDatabase> = this

  override fun setVisibleRowCount(visibleRowCount: Int): IPopupChooserBuilder<SqliteDatabase> = this

  override fun getBackgroundUpdater(): ListComponentUpdater = mock(ListComponentUpdater::class.java)
}