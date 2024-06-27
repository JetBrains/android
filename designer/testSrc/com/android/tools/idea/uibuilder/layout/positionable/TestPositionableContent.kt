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
package com.android.tools.idea.uibuilder.layout.positionable

import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import java.awt.Dimension
import java.awt.Insets

open class TestPositionableContent(
  override val organizationGroup: OrganizationGroup?,
  private val size: Dimension = Dimension(0, 0),
) : PositionableContent {
  override val scale = 0.0
  override val x = 0
  override val y = 0
  override val isFocusedContent = false

  override fun getContentSize(dimension: Dimension?) = size

  override fun setLocation(x: Int, y: Int) {}

  override fun getMargin(scale: Double): Insets = Insets(0, 0, 0, 0)
}

class HeaderTestPositionableContent(override val organizationGroup: OrganizationGroup?) :
  TestPositionableContent(organizationGroup), HeaderPositionableContent
