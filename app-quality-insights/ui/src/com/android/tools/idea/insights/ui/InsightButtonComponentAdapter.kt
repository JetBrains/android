/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import java.awt.Component
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

class InsightButtonComponentAdapter(
  private val toolbarComponent: Component,
  private val maxSizeFetcher: () -> Dimension,
) : ComponentAdapter() {
  override fun componentResized(e: ComponentEvent?) {
    toolbarComponent.maximumSize = maxSizeFetcher()
    // toolbarComponent sometimes has a y coordinate value that pushes it above/below the bounds
    // of the visible region. Set it to 0 to make sure the button is always visible
    toolbarComponent.setLocation(toolbarComponent.location.x, 0)
  }
}
