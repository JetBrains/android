/*
 * Copyright (C) 2016 The Android Open Source Project
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

@file:JvmName("FormFactorUtils")

package com.android.tools.idea.npw.platform

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.FormFactor
import com.intellij.util.ui.UIUtil
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JComponent

import java.awt.image.BufferedImage

/**
 * Utility methods for dealing with Form Factors in Wizards.
 */
/**
 * Create an image showing icons for each of the available form factors. The icons are drawn from left to right, using the form factors
 * large icon. This is an example of this method usage:
 * <pre>
 * `
 * myJLabel.setIcon(getFormFactorsImage(myJLabel, true));
` *
</pre> *
 * @param component Icon will be drawn in the context of the given `component`
 * @param requireEmulator If true, only include icons for form factors that have an emulator available.
 * @return `null` if it can't create a graphics Object to render the image (for example not enough memory)
 */
fun getFormFactorsImage(component: JComponent, requireEmulator: Boolean): Icon? {
  val filteredFormFactors = FormFactor.values().filter {
    (it !== FormFactor.AUTOMOTIVE || StudioFlags.NPW_TEMPLATES_AUTOMOTIVE.get()) &&
    (it !== FormFactor.CAR || !StudioFlags.NPW_TEMPLATES_AUTOMOTIVE.get()) &&
    (it.hasEmulator() || !requireEmulator)
  }

  val width = filteredFormFactors.map { it.largeIcon.iconWidth }.sum()
  val height = filteredFormFactors.map { it.largeIcon.iconHeight }.max() ?: 0

  val image = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB)
  with(image.createGraphics()) {
    var x = 0
    filteredFormFactors.forEach {
      val icon = it.largeIcon
      icon.paintIcon(component, this, x, 0)
      x += icon.iconWidth
    }
    dispose()
  }
  return ImageIcon(image)
}
