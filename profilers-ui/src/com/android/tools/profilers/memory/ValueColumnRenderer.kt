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
package com.android.tools.profilers.memory

import com.android.tools.adtui.common.ColoredIconGenerator.generateWhiteIcon
import com.android.tools.profilers.memory.adapters.ValueObject
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import java.awt.Color
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.SwingConstants

open class ValueColumnRenderer : ColoredTreeCellRenderer() {
  override fun customizeCellRenderer(tree: JTree,
                                     value: Any,
                                     selected: Boolean,
                                     expanded: Boolean,
                                     leaf: Boolean,
                                     row: Int,
                                     hasFocus: Boolean) {
    when {
      value !is MemoryObjectTreeNode<*> -> append(value.toString())
      value.adapter !is ValueObject -> append(value.adapter.name)
      else -> {
        val valueObject = value.adapter as ValueObject
        setIconColorized(MemoryProfilerStageView.getValueObjectIcon(valueObject))
        setTextAlign(SwingConstants.LEFT)

        val name = valueObject.name
        append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name)
        append(if (name.isEmpty()) "" else " = ")

        val valueText = valueObject.valueText
        append(valueText, SimpleTextAttributes.REGULAR_ATTRIBUTES, valueText)
        append(if (valueText.isEmpty()) "" else " ")

        val toStringText = valueObject.toStringText
        append(toStringText,
               // TODO import IntelliJ colors for accessibility
               if (valueObject.valueType == ValueObject.ValueType.STRING) STRING_ATTRIBUTES
               else SimpleTextAttributes.REGULAR_ATTRIBUTES,
               toStringText)
      }
    }
  }

  private fun setIconColorized(icon: Icon) =
    setIcon(if (mySelected && isFocused) generateWhiteIcon(icon) else icon)

  companion object {
    val STRING_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color(0, 0x80, 0))
  }
}