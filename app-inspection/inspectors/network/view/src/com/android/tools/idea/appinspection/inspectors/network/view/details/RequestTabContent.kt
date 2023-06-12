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
import com.android.tools.idea.appinspection.inspectors.network.view.details.HttpDataComponentFactory.ConnectionType.REQUEST
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel

// Use Application Headers as title because the infrastructure added headers of HttpURLConnection
// may be missed if users do not set.
private const val HEADERS_TITLE = "Application Headers"

/** Tab which shows a request's headers and payload. */
class RequestTabContent : TabContent() {
  private lateinit var contentPanel: JPanel
  override val title = "Request"

  override fun createComponent(): JComponent {
    contentPanel = createVerticalPanel(TAB_SECTION_VGAP)
    contentPanel.border = JBUI.Borders.empty(0, HORIZONTAL_PADDING)
    return createVerticalScrollPane(contentPanel)
  }

  override fun populateFor(data: HttpData?, httpDataComponentFactory: HttpDataComponentFactory) {
    contentPanel.removeAll()
    if (data == null) {
      return
    }
    val headersComponent = httpDataComponentFactory.createHeaderComponent(REQUEST)
    contentPanel.add(createHideablePanel(HEADERS_TITLE, headersComponent, null))
    contentPanel.add(httpDataComponentFactory.createBodyComponent(REQUEST))
  }

  @VisibleForTesting
  fun findPayloadBody(): JComponent? {
    return findComponentWithUniqueName(
      contentPanel,
      HttpDataComponentFactory.ConnectionType.REQUEST.bodyComponentId
    )
  }
}
