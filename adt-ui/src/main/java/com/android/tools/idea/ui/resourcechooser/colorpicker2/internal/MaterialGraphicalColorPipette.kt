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
package com.android.tools.idea.ui.resourcechooser.colorpicker2.internal

import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPipette
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPipetteProvider
import com.android.tools.idea.ui.resourcechooser.colorpicker2.GraphicalColorPipette
import icons.StudioIcons
import javax.swing.Icon
import javax.swing.JComponent

class MaterialGraphicalColorPipette(base: GraphicalColorPipette) : ColorPipette by base {
  override val icon: Icon = StudioIcons.LayoutEditor.Extras.PIPETTE_LARGE

  override val rolloverIcon: Icon = StudioIcons.LayoutEditor.Extras.PIPETTE_LARGE

  override val pressedIcon: Icon = StudioIcons.LayoutEditor.Extras.PIPETTE_LARGE
}

class MaterialGraphicalColorPipetteProvider : ColorPipetteProvider {
  override fun createPipette(owner: JComponent): ColorPipette = MaterialGraphicalColorPipette(GraphicalColorPipette(owner))
}
