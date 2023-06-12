/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.common.surface.layout

import com.android.tools.idea.uibuilder.surface.layout.PositionableContent
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager
import java.awt.Dimension
import java.awt.Point

/**
 * A [SurfaceLayoutManager] for testing which returns empty [Dimension] and always return zoom-to-fit scale as 100%.
 * It also does nothing when [measure] is called.
 */
class EmptySurfaceLayoutManager: SurfaceLayoutManager {
  override fun getPreferredSize(content: Collection<PositionableContent>,
                                availableWidth: Int,
                                availableHeight: Int,
                                dimension: Dimension?): Dimension = Dimension()
  override fun getRequiredSize(content: Collection<PositionableContent>,
                               availableWidth: Int,
                               availableHeight: Int,
                               dimension: Dimension?): Dimension = Dimension()

  override fun getFitIntoScale(content: Collection<PositionableContent>, availableWidth: Int, availableHeight: Int): Double = 1.0

  override fun measure(content: Collection<PositionableContent>,
                       availableWidth: Int,
                       availableHeight: Int,
                       keepPreviousPadding: Boolean): Map<PositionableContent, Point> = emptyMap()
}
