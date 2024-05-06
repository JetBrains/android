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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Container
import javax.swing.JComponent

/**
 * Class grouping components from a Running Devices tab. Used to inject Layout Inspector in the tab.
 * These components are disposed as soon as the tab is not visible or is not the main selected tab.
 * For this reason they should not be kept around if they don't belong to the selected tab.
 *
 * @param tabContentPanel The component containing the main content of the tab (the display).
 * @param tabContentPanelContainer The container of [tabContentPanel].
 * @param displayView The [AbstractDisplayView] from running devices. Component on which the device
 *   display is rendered.
 */
class TabComponents(
  disposable: Disposable,
  val tabContentPanel: JComponent,
  val tabContentPanelContainer: Container,
  val displayView: AbstractDisplayView
) : Disposable {
  init {
    Disposer.register(disposable, this)
  }

  override fun dispose() {}
}
