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

import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.android.tools.idea.appinspection.inspectors.network.view.details.DataComponentFactory.ConnectionType.REQUEST
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

// Use Application Headers as title because the infrastructure added headers of HttpURLConnection
// may be missed if users do not set.
private const val HEADERS_TITLE = "Application Headers"

/** Tab which shows a request's headers and payload. */
internal class RequestTabContent : TabContent() {
  private val contentPanel = createVerticalPanel(TAB_SECTION_VGAP)

  override val title = "Request"

  override fun createComponent(): JComponent {
    contentPanel.border = JBUI.Borders.empty(0, HORIZONTAL_PADDING)
    return createVerticalScrollPane(contentPanel)
  }

  override fun populateFor(data: ConnectionData?, dataComponentFactory: DataComponentFactory) {
    contentPanel.removeAll()
    if (data == null) {
      return
    }
    val headersComponent = dataComponentFactory.createHeaderComponent(REQUEST)
    if (headersComponent != null) {
      contentPanel.add(createHideablePanel(HEADERS_TITLE, headersComponent, null))
    }
    val bodyComponent = dataComponentFactory.createBodyComponent(REQUEST)
    if (bodyComponent != null) {
      contentPanel.add(bodyComponent)
    }
  }
}
