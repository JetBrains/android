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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.SdkConstants;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItemValue;
import com.android.tools.idea.uibuilder.property.NlProperty;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class NlFlagItemRenderer extends NlAttributeRenderer {
  private final JCheckBox myCheckbox;

  public NlFlagItemRenderer() {
    JPanel panel = getContentPanel();
    myCheckbox = new JCheckBox();
    panel.add(myCheckbox, BorderLayout.LINE_START);
  }

  @Override
  protected void customizeCellRenderer(@NotNull PTable table, @NotNull PTableItem value,
                                       boolean selected, boolean hasFocus, int row, int col) {
    if (!(value instanceof NlFlagPropertyItemValue)) {
      return;
    }

    NlFlagPropertyItemValue flag = (NlFlagPropertyItemValue)value;

    myCheckbox.setEnabled(true);

    String propValue = flag.getValue();
    myCheckbox.setSelected(flag.getMaskValue());
    myCheckbox.setEnabled(SdkConstants.VALUE_TRUE.equalsIgnoreCase(propValue));
  }

  @Override
  public boolean canRender(@NotNull NlProperty property, @NotNull Set<AttributeFormat> formats) {
    return property instanceof NlFlagPropertyItemValue;
  }
}
