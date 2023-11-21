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

import com.android.tools.idea.appinspection.inspectors.network.model.connections.GrpcData
import javax.swing.JComponent

internal class GrpcDataComponentFactory(data: GrpcData) : DataComponentFactory(data) {
  private val grpcData: GrpcData
    get() = data as GrpcData

  override fun createDataViewer(type: ConnectionType, formatted: Boolean) = null

  override fun createBodyComponent(type: ConnectionType) = null

  override fun createTrailersComponent(): JComponent? {
    return when {
      grpcData.responseTrailers.isEmpty() -> null
      else -> createStyledMapComponent(grpcData.responseTrailers)
    }
  }
}
