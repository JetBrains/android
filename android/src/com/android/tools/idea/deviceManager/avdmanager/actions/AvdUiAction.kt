/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager.actions

import com.android.sdklib.internal.avd.AvdInfo
import com.intellij.openapi.project.Project
import java.beans.PropertyChangeListener
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/**
 * Abstract base class for AVD editing actions
 */
abstract class AvdUiAction(
  @JvmField
  protected val avdInfoProvider: AvdInfoProvider,
  val text: String,
  val description: String,
  val icon: Icon
) : Action, HyperlinkListener {
  private val data: MutableMap<String, Any> = hashMapOf(
    Action.LARGE_ICON_KEY to icon,
    Action.NAME to text
  )
  protected val avdInfo: AvdInfo? get() = avdInfoProvider.avdInfo
  protected val project: Project? get() = avdInfoProvider.project

  interface AvdInfoProvider {
    val avdInfo: AvdInfo?
    fun refreshAvds()
    fun refreshAvdsAndSelect(avdToSelect: AvdInfo?)
    val project: Project?
    val avdProviderComponent: JComponent
  }

  override fun getValue(key: String): Any? = data[key]

  final override fun putValue(key: String, value: Any) {
    data[key] = value
  }

  override fun setEnabled(enabled: Boolean) {}
  abstract override fun isEnabled(): Boolean
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}


  protected fun refreshAvds() {
    avdInfoProvider.refreshAvds()
  }

  protected fun refreshAvdsAndSelect(avdToSelect: AvdInfo?) {
    avdInfoProvider.refreshAvdsAndSelect(avdToSelect)
  }

  override fun hyperlinkUpdate(e: HyperlinkEvent) {
    if (isEnabled) {
      actionPerformed(null)
    }
  }
}