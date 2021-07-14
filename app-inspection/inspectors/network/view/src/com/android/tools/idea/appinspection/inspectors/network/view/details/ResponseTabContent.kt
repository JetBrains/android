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

import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.view.UiComponentsProvider
import com.android.tools.idea.flags.StudioFlags.ENABLE_NETWORK_INTERCEPTION
import com.android.tools.inspectors.common.ui.dataviewer.IntellijDataViewer
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBEmptyBorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Tab which shows a response's headers and payload.
 */
class ResponseTabContent(
  private val componentsProvider: UiComponentsProvider,
  private val inspectorServices: NetworkInspectorServices,
  private val scope: CoroutineScope
) : TabContent() {
  private lateinit var panel: JPanel
  override val title: String
    get() = "Response"

  override fun createComponent(): JComponent {
    panel = createVerticalPanel(TAB_SECTION_VGAP).apply {
      border = JBEmptyBorder(0, HORIZONTAL_PADDING, 0, HORIZONTAL_PADDING)
    }
    return createVerticalScrollPane(panel)
  }

  override fun populateFor(data: HttpData?) {
    panel.removeAll()
    if (data == null) {
      return
    }
    val httpDataComponentFactory = HttpDataComponentFactory(data)
    val headersComponent = httpDataComponentFactory.createHeaderComponent(HttpDataComponentFactory.ConnectionType.RESPONSE)
    panel.add(createHideablePanel(SECTION_TITLE_HEADERS, headersComponent, null))
    panel.add(httpDataComponentFactory.createBodyComponent(componentsProvider, HttpDataComponentFactory.ConnectionType.RESPONSE))
    if (ENABLE_NETWORK_INTERCEPTION.get()) {
      val button = JButton("Intercept")
      button.addActionListener {
        val dialog: DialogWrapper = ResponseInterceptDialog(data, inspectorServices, scope)
        dialog.show()
      }
      panel.add(button)
    }
  }

  @VisibleForTesting
  fun findPayloadBody(): JComponent? {
    return findComponentWithUniqueName(panel, HttpDataComponentFactory.ConnectionType.RESPONSE.bodyComponentId)
  }

  class ResponseInterceptDialog(val data: HttpData,
                                private val inspectorServices: NetworkInspectorServices,
                                private val scope: CoroutineScope) : DialogWrapper(false) {

    private var textArea: JTextArea? = null

    init {
      title = "Intercept Network Response"
      init()
    }

    override fun createNorthPanel(): JComponent? {
      val type = HttpDataComponentFactory.ConnectionType.RESPONSE
      val payload = type.getPayload(data)
      val viewer = IntellijDataViewer.createRawTextViewer(payload.toByteArray(), true)
      textArea = viewer.component as? JTextArea ?: return null
      return createVerticalScrollPane(textArea!!).apply {
        border = JBEmptyBorder(0, HORIZONTAL_PADDING, 0, HORIZONTAL_PADDING)
        preferredSize = Dimension(800, 300)
      }
    }

    override fun createCenterPanel(): JComponent? = null

    override fun doOKAction() {
      textArea?.also { textArea ->
        scope.launch { inspectorServices.client.interceptResponse(data.url, textArea.text) }
      }

      super.doOKAction()
    }
  }
}