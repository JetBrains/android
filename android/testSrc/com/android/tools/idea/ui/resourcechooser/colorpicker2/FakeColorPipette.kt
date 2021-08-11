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
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import com.intellij.icons.AllIcons
import java.awt.Color
import javax.swing.Icon
import javax.swing.JComponent

internal class FakeColorPipette: ColorPipette {
  override val icon: Icon = AllIcons.Ide.Pipette

  override val rolloverIcon: Icon = AllIcons.Ide.Pipette_rollover

  override val pressedIcon: Icon = AllIcons.Ide.Pipette_rollover

  lateinit var pickedColor: Color
  var shouldSucceed = true

  override fun pick(callback: ColorPipette.Callback) {
    if (shouldSucceed) {
      callback.picked(pickedColor)
    }
    else {
      callback.cancel()
    }
  }
}

internal class FakeColorPipetteProvider : ColorPipetteProvider {
  override fun createPipette(owner: JComponent): ColorPipette = object : ColorPipette {
    override val icon: Icon = AllIcons.Ide.Pipette
    override val rolloverIcon: Icon = AllIcons.Ide.Pipette_rollover
    override val pressedIcon: Icon = AllIcons.Ide.Pipette_rollover
    override fun pick(callback: ColorPipette.Callback) = Unit
  }
}
