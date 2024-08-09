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
package com.android.tools.idea.naveditor.surface

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.layout.manager.PositionableContentLayoutManager
import com.android.tools.idea.common.layout.positionable.getScaledContentSize
import java.awt.Dimension
import java.awt.Point

/**
 * A [PositionableContentLayoutManager] for a [DesignSurface] with only one [PositionableContent].
 */
class SinglePositionableContentLayoutManager : PositionableContentLayoutManager() {
  override fun layoutContainer(content: Collection<PositionableContent>, availableSize: Dimension) {
    content.singleOrNull()?.setLocation(0, 0)
  }

  override fun preferredLayoutSize(content: Collection<PositionableContent>, availableSize: Dimension): Dimension =
    content
      .singleOrNull()
      ?.getScaledContentSize(null)
    ?: availableSize

  override fun getMeasuredPositionableContentPosition(content: Collection<PositionableContent>,
                                                      availableWidth: Int,
                                                      availableHeight: Int): Map<PositionableContent, Point> {
    return content.singleOrNull()?.let { mapOf(it to Point(0, 0)) } ?: emptyMap()
  }
}