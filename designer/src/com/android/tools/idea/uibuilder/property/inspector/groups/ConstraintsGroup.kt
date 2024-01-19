/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.inspector.groups

import com.android.SdkConstants
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.FilteredPTableModel
import com.android.tools.property.panel.api.GroupSpec
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.ptable.PTableItem

const val CONSTRAINT_GROUP_NAME = "layout_constraints"

class ConstraintGroup(properties: PropertiesTable<NlPropertyItem>) : GroupSpec<NlPropertyItem> {
  private val hasConstraints =
    properties.getOrNull(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_CONSTRAINTSET) != null
  private val others =
    setOf(
      SdkConstants.ATTR_LAYOUT_CONSTRAINTSET,
      SdkConstants.ATTR_MIN_WIDTH,
      SdkConstants.ATTR_MIN_HEIGHT,
      SdkConstants.ATTR_MAX_WIDTH,
      SdkConstants.ATTR_MAX_HEIGHT,
      SdkConstants.ATTR_LAYOUT_OPTIMIZATION_LEVEL,
      SdkConstants.ATTR_BARRIER_DIRECTION,
      SdkConstants.ATTR_BARRIER_ALLOWS_GONE_WIDGETS,
      SdkConstants.CONSTRAINT_REFERENCED_IDS,
      SdkConstants.ATTR_LAYOUT_CHAIN_HELPER_USE_RTL,
      SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
      SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
      SdkConstants.ATTR_FLOW_WRAP_MODE,
      SdkConstants.ATTR_FLOW_MAX_ELEMENTS_WRAP,
      SdkConstants.ATTR_FLOW_FIRST_HORIZONTAL_BIAS,
      SdkConstants.ATTR_FLOW_FIRST_HORIZONTAL_STYLE,
      SdkConstants.ATTR_FLOW_HORIZONTAL_BIAS,
      SdkConstants.ATTR_FLOW_HORIZONTAL_STYLE,
      SdkConstants.ATTR_FLOW_HORIZONTAL_ALIGN,
      SdkConstants.ATTR_FLOW_HORIZONTAL_GAP,
      SdkConstants.ATTR_FLOW_LAST_HORIZONTAL_BIAS,
      SdkConstants.ATTR_FLOW_LAST_HORIZONTAL_STYLE,
      SdkConstants.ATTR_FLOW_FIRST_VERTICAL_BIAS,
      SdkConstants.ATTR_FLOW_FIRST_VERTICAL_STYLE,
      SdkConstants.ATTR_FLOW_VERTICAL_BIAS,
      SdkConstants.ATTR_FLOW_VERTICAL_STYLE,
      SdkConstants.ATTR_FLOW_VERTICAL_ALIGN,
      SdkConstants.ATTR_FLOW_VERTICAL_GAP,
      SdkConstants.ATTR_FLOW_LAST_VERTICAL_BIAS,
      SdkConstants.ATTR_FLOW_LAST_VERTICAL_STYLE,
    )

  override val name: String
    get() = CONSTRAINT_GROUP_NAME

  override val value: String?
    get() = null

  override val itemFilter: (NlPropertyItem) -> Boolean
    get() = {
      hasConstraints &&
        (it.name.startsWith("layout_constraint") ||
          it.name.startsWith("layout_constrained") ||
          it.name.startsWith("layout_goneMargin") ||
          others.contains(it.name))
    }

  override val comparator: Comparator<PTableItem>
    get() = FilteredPTableModel.alphabeticalSortOrder

  override fun hashCode(): Int {
    return name.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    return name == (other as? ConstraintGroup)?.name
  }
}
