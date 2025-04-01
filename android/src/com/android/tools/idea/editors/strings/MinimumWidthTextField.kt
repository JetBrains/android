/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.editors.strings

import com.intellij.ui.components.JBTextField
import java.awt.Dimension

private const val MIN_WIDTH = 50

internal open class MinimumWidthTextField : JBTextField() {

  /**
   * The default version of [getMinimumSize] computes the minimum span of the text in the control.
   * For some languages e.g. Bengla this is not supported and the returned width is the width of
   * the entire string. If the string is wider than the width of the string translator this will
   * block the horizontal scroll capability of the text field.
   * Override the width with an arbitrary value to avoid this.
   */
  override fun getMinimumSize(): Dimension {
    val size = super.getMinimumSize()
    size.width = MIN_WIDTH
    return size
  }
}
