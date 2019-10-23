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
package com.android.tools.adtui.common

import java.awt.FlowLayout
import java.awt.LayoutManager
import javax.swing.JPanel

/*
 * Base Panels that use the [primaryPanelBackground] and [secondaryPanelBackground]
 */

/**
 * Panel that will have a primary role in the UI.
 *
 * For example, the central panel in the Layout Editor
 */
open class AdtPrimaryPanel(layout: LayoutManager? = FlowLayout()) : JPanel(layout) {

  init {
    background = primaryPanelBackground
    isOpaque = true
  }
}

/**
 * Panel that will have a secondary role in the UI.
 *
 * For example, the component tree in the Layout Editor
 */
open class AdtSecondaryPanel(layout: LayoutManager? = FlowLayout()) : JPanel(layout) {

  init {
    background = secondaryPanelBackground
    isOpaque = true
  }
}
