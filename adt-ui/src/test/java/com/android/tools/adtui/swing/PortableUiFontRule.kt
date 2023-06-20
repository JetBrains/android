/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.swing

import com.android.tools.adtui.imagediff.ImageDiffTestUtil
import org.junit.rules.ExternalResource
import java.awt.RenderingHints.KEY_TEXT_ANTIALIASING
import java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
import java.util.Enumeration
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource

/**
 * Sets all default fonts to Droid Sans that is included in the bundled JDK. This makes fonts the same across all platforms.
 *
 * To improve error detection it may be helpful to scale the font used up (to improve matches across platforms and detect text changes)
 * or down (to decrease the importance of text in generated images).
 */
class PortableUiFontRule(val scale: Float = 1.0f) : ExternalResource() {

  private val originalValues = mutableMapOf<Any, Any?>()

  override fun before() {
    val keys: Enumeration<*> = UIManager.getLookAndFeelDefaults().keys()
    val default = ImageDiffTestUtil.getDefaultFont()
    while (keys.hasMoreElements()) {
      val key = keys.nextElement()
      val value = UIManager.get(key)
      if (value is FontUIResource) {
        originalValues[key] = value
        val font = default.deriveFont(value.style).deriveFont(value.size.toFloat() * scale)
        UIManager.put(key, FontUIResource(font))
      }
    }
    originalValues[KEY_TEXT_ANTIALIASING] = UIManager.get(KEY_TEXT_ANTIALIASING)
    UIManager.put(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_LCD_HRGB)
  }

  override fun after() {
    originalValues.forEach { (key, value) -> UIManager.put(key, value) }
  }
}