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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.android.tools.idea.appinspection.inspectors.network.view.details.DataComponentFactory.ConnectionType.REQUEST
import com.android.tools.idea.appinspection.inspectors.network.view.details.DataComponentFactory.ConnectionType.RESPONSE
import com.android.tools.inspectors.common.ui.dataviewer.DataViewer
import javax.swing.JComponent

/**
 * A factory which wraps a target [ConnectionData] and can create useful, shared UI components for
 * displaying aspects of it.
 */
internal abstract class DataComponentFactory(protected val data: ConnectionData) {
  enum class ConnectionType {
    REQUEST,
    RESPONSE;

    val bodyComponentId: String
      get() = if (this == REQUEST) "REQUEST_PAYLOAD_COMPONENT" else "RESPONSE_PAYLOAD_COMPONENT"
  }

  /**
   * Creates a component which displays the current [ConnectionData]'s headers as a list of
   * key/value pairs.
   */
  fun createHeaderComponent(type: ConnectionType) = createHeaderComponent(getHeaders(type))

  /**
   * Creates a component which displays the current [ConnectionData]'s trailers as a list of
   * key/value pairs.
   *
   * Returns `null` if there are no trailers
   */
  open fun createTrailersComponent(): JComponent? = null

  abstract fun createDataViewer(type: ConnectionType, formatted: Boolean): DataViewer?

  private fun getHeaders(type: ConnectionType) =
    when (type) {
      REQUEST -> data.requestHeaders
      RESPONSE -> data.responseHeaders
    }

  abstract fun createBodyComponent(type: ConnectionType): JComponent?

  protected fun createHeaderComponent(map: Map<String, List<String>>): JComponent? {
    if (map.isEmpty()) {
      return null
    }
    return HeadersPanel(map)
  }
}
