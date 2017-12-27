/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.grid.draw

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent

class GridLayoutV7Decorator : GridLayoutDecorator() {
  override fun retrieveCellData(nlComponent: NlComponent): CellInfo {
    // By default, the (row, column, rowSpan, columnSpan) is (0, 0, 1, 1)
    return CellInfo(nlComponent.getLiveAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_ROW)?.toIntOrNull() ?: 0,
        nlComponent.getLiveAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_COLUMN)?.toIntOrNull() ?: 0,
        nlComponent.getLiveAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_ROW_SPAN)?.toIntOrNull() ?: 1,
        nlComponent.getLiveAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_COLUMN_SPAN)?.toIntOrNull() ?: 1)
  }
}
