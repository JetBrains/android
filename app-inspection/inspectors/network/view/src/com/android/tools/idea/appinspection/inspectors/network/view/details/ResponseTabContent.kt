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
import com.android.tools.idea.appinspection.inspectors.network.view.details.HttpDataComponentFactory.ConnectionType.RESPONSE
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tab which shows a response's headers and payload.
 */
class ResponseTabContent : TabContent() {
  private lateinit var panel: JPanel
  override val title: String
    get() = "Response"

  override fun createComponent(): JComponent {
    panel = createVerticalPanel(TAB_SECTION_VGAP).apply {
      border = JBUI.Borders.empty(0, HORIZONTAL_PADDING)
    }
    return createVerticalScrollPane(panel)
  }

  override fun populateFor(data: HttpData?, httpDataComponentFactory: HttpDataComponentFactory) {
    panel.removeAll()
    if (data == null) {
      return
    }
    val headersComponent = httpDataComponentFactory.createHeaderComponent(RESPONSE)
    panel.add(createHideablePanel(SECTION_TITLE_HEADERS, headersComponent, null))
    panel.add(httpDataComponentFactory.createBodyComponent(RESPONSE))
  }

  @VisibleForTesting
  fun findPayloadBody(): JComponent? {
    return findComponentWithUniqueName(panel, HttpDataComponentFactory.ConnectionType.RESPONSE.bodyComponentId)
  }
}
