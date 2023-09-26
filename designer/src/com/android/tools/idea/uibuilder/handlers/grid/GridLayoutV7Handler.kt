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
package com.android.tools.idea.uibuilder.handlers.grid

import com.android.SdkConstants
import com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_GRID_LAYOUT_V7
import com.android.ide.common.repository.GoogleMavenArtifactId.GRID_LAYOUT_V7

/** Handler for the `<android.support.v7.widget.GridLayout>` layout from AppCompat */
class GridLayoutV7Handler : GridLayoutHandler() {

  override val namespace = SdkConstants.AUTO_URI

  override fun getGradleCoordinateId(viewTag: String) =
    when {
      viewTag.startsWith(SdkConstants.ANDROIDX_PKG_PREFIX) -> ANDROIDX_GRID_LAYOUT_V7
      else -> GRID_LAYOUT_V7
    }
}
