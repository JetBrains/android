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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.android.tools.profilers.memory.adapters.ValueObject;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.STRING;

public class ValueColumnRenderer extends ColoredTreeCellRenderer {
  public static final SimpleTextAttributes STRING_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, new Color(0, 0x80, 0));

  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    if (!(value instanceof MemoryObjectTreeNode)) {
      append(value.toString());
      return;
    }

    MemoryObject adapter = ((MemoryObjectTreeNode)value).getAdapter();
    if (!(adapter instanceof ValueObject)) {
      append(adapter.getName());
      return;
    }

    ValueObject valueObject = (ValueObject)adapter;
    setIcon(MemoryProfilerStageView.getValueObjectIcon(valueObject));

    setTextAlign(SwingConstants.LEFT);

    String name = valueObject.getName();
    append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
    append(name.isEmpty() ? "" : " = ");

    String valueText = valueObject.getValueText();
    append(valueText, SimpleTextAttributes.REGULAR_ATTRIBUTES, valueText);
    append(valueText.isEmpty() ? "" : " ");

    String toStringText = valueObject.getToStringText();
    if (valueObject.getValueType() == STRING) {
      // TODO import IntelliJ colors for accessibility
      append(toStringText, STRING_ATTRIBUTES, toStringText);
    }
    else {
      append(toStringText, SimpleTextAttributes.REGULAR_ATTRIBUTES, toStringText);
    }
  }
}
