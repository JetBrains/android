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
package com.android.tools.idea.refactoring.modularize

import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes

abstract class DependencyTreeNode
  @JvmOverloads
  constructor(
  userObject: Any?,
  val referenceCount: Int = 0,
) : CheckedTreeNode(userObject) {

  abstract fun render(renderer: ColoredTreeCellRenderer)

  val textAttributes: SimpleTextAttributes
    get() = if (isChecked()) SimpleTextAttributes.REGULAR_ATTRIBUTES else STRIKEOUT_ATTRIBUTES

  fun renderReferenceCount(renderer: ColoredTreeCellRenderer, inheritedAttributes: SimpleTextAttributes) {
    if (referenceCount > 1) {
      val derivedAttributes = SimpleTextAttributes(
        inheritedAttributes.style or SimpleTextAttributes.STYLE_ITALIC or SimpleTextAttributes.STYLE_SMALLER,
        inheritedAttributes.fgColor,
      )
      renderer.append(" ($referenceCount usages)", derivedAttributes)
    }
  }

  companion object {
    private val STRIKEOUT_ATTRIBUTES = SimpleTextAttributes(
      SimpleTextAttributes.STYLE_STRIKEOUT or SimpleTextAttributes.STYLE_ITALIC,
      null,
    )
  }
}