/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.compose.meshgradient

import com.android.tools.adtui.compose.StudioComposePanel
import java.awt.BorderLayout
import javax.swing.JPanel
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing

class MeshGradientGeneratorPanel : JPanel(BorderLayout()) {
  init {
    @OptIn(ExperimentalJewelApi::class) enableNewSwingCompositing()

    val composePanel = StudioComposePanel { MeshGradientGeneratorScreen() }
    add(composePanel, BorderLayout.CENTER)
  }
}
