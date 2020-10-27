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
package com.android.tools.adtui.chart.linechart

import java.awt.Component
import java.awt.Graphics2D
import java.awt.event.MouseEvent

/**
 * An interface for custom rendering of duration data
 */
interface AbstractDurationDataRenderer: LineChartCustomRenderer {
  /**
   * Render overlay on top of given component
   */
  fun renderOverlay(host: Component, g2d: Graphics2D)

  /**
   * Handle mouse event
   * @return whether other handlers should also handle this event
   */
  fun handleMouseEvent(overlayComponent: Component, selectionComponent: Component, event: MouseEvent): Boolean
}