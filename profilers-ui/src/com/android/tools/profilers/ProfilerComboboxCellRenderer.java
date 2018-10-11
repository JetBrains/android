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
package com.android.tools.profilers;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import javax.swing.JList;

/**
 * Renderer used to stylize instances of {@link ProfilerCombobox}.
 */
public abstract class ProfilerComboboxCellRenderer<T> extends ColoredListCellRenderer<T> {

  public ProfilerComboboxCellRenderer() {
    setIpad(new JBInsets(0, UIUtil.isUnderNativeMacLookAndFeel() ? 5 : UIUtil.getListCellHPadding(), 0, 0));
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends T> list,
                                                T value,
                                                int index,
                                                boolean selected,
                                                boolean hasFocus) {
    Component listCellRendererComponent = super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    listCellRendererComponent
      .setBackground(selected ? ProfilerColors.COMBOBOX_SELECTED_CELL : ProfilerColors.DEFAULT_BACKGROUND);
    return listCellRendererComponent;
  }
}