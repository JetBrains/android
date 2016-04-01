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
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItemValue;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.icons.AllIcons;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class NlFlagItemRenderer extends NlAttributeRenderer {
  private final JCheckBox myCheckbox;
  private final SimpleColoredComponent myLabel;

  public NlFlagItemRenderer() {
    JPanel panel = getContentPanel();
    myCheckbox = new JCheckBox();
    panel.add(myCheckbox, BorderLayout.LINE_START);
    myLabel = new SimpleColoredComponent();
    panel.add(myLabel, BorderLayout.CENTER);
  }

  @Override
  public void customizeRenderContent(@NotNull JTable table, @NotNull NlProperty item, boolean selected, boolean hasFocus, int row, int col) {
    myCheckbox.setEnabled(true);
    myLabel.clear();

    String propValue = item.getValue();

    if (propValue != null) {
      myCheckbox.setEnabled(true);
      myCheckbox.setSelected(SdkConstants.VALUE_TRUE.equalsIgnoreCase(propValue));
    } else {
      myCheckbox.setEnabled(false);
    }
  }

  @Nullable
  @Override
  public Icon getHoverIcon(@NotNull NlProperty p) {
    return AllIcons.General.EditItemInSection;
  }

  @Override
  public boolean canRender(@NotNull NlProperty property, @NotNull Set<AttributeFormat> formats) {
    return property instanceof NlFlagPropertyItemValue;
  }
}
