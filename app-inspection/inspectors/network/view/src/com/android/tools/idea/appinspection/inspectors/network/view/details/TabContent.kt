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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JTabbedPane

/**
 * Base class for all tabs shown in the [ConnectionDetailsView]. To use, construct
 * subclass instances and add their title, icon, and component content to a target
 * [JTabbedPane] using [JTabbedPane.addTab]
 */
abstract class TabContent {
  /**
   * Return the component associated with this tab. Guaranteed to be the same value every time.
   */
  val component: JComponent by lazy {
    createComponent().apply {
      border = JBUI.Borders.empty()
    }
  }

  abstract val title: String

  /**
   * Populates the contents of this tab with information from the target `data`. This value
   * might possibly be `null`, if the user cleared the current selection.
   */
  abstract fun populateFor(data: HttpData?, httpDataComponentFactory: HttpDataComponentFactory)

  /**
   * The subclass should create a panel that will populate a tab.
   *
   * This method will only be called once and cached thereafter.
   */
  protected abstract fun createComponent(): JComponent
}