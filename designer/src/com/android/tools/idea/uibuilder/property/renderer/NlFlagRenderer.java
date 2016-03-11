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

import com.android.tools.idea.uibuilder.property.NlFlagProperty;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class NlFlagRenderer extends NlAttributeRenderer {
  private final JBLabel myLabel;

  public NlFlagRenderer() {
    myLabel = new JBLabel();
    JPanel panel = getContentPanel();
    panel.add(myLabel, BorderLayout.CENTER);
  }

  @Override
  public void customizeRenderContent(@NotNull JTable table,
                                     @NotNull PTableItem item,
                                     boolean selected,
                                     boolean hasFocus,
                                     int row,
                                     int col) {
    assert item instanceof NlFlagProperty;
    NlFlagProperty property = (NlFlagProperty)item;

    if (col != 1) {
      return;
    }
    myLabel.setText(property.getFormattedValue());
  }

  @Nullable
  @Override
  public Icon getHoverIcon(@NotNull PTableItem p) {
    return null;
  }

  @Override
  public boolean canRender(@NotNull PTableItem item, @NotNull Set<AttributeFormat> formats) {
    return formats.contains(AttributeFormat.Flag);
  }
}
