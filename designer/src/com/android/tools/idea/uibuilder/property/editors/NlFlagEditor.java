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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.SdkConstants;
import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.editors.BaseComponentEditor;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItemValue;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * The {@link NlFlagEditor} is used to edit a {@link NlFlagPropertyItemValue} by displaying
 * a single checkbox for a single flag value.
 */
public class NlFlagEditor extends BaseComponentEditor implements NlComponentEditor {
  private final JPanel myPanel;
  private final JCheckBox myCheckbox;
  private final boolean myIncludeLabel;

  private NlProperty myProperty;
  private String myValue;

  public static NlTableCellEditor createForTable() {
    NlTableCellEditor cellEditor = new NlTableCellEditor();
    cellEditor.init(new NlFlagEditor(cellEditor, false), null);
    return cellEditor;
  }

  public static NlFlagEditor createForInspector(@NotNull NlEditingListener listener) {
    return new NlFlagEditor(listener, true);
  }

  private NlFlagEditor(@NotNull NlEditingListener listener, boolean includeLabel) {
    super(listener);
    myIncludeLabel = includeLabel;
    myPanel = new AdtSecondaryPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));
    myCheckbox = new JCheckBox();
    myPanel.add(myCheckbox, BorderLayout.LINE_START);
    myCheckbox.addActionListener(this::actionPerformed);
    myProperty = EmptyProperty.INSTANCE;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public String getValue() {
    return myValue;
  }

  @NotNull
  @Override
  public NlProperty getProperty() {
    return myProperty;
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    assert property instanceof NlFlagPropertyItemValue || property == EmptyProperty.INSTANCE;
    if (property != myProperty) {
      myProperty = property;
      if (myIncludeLabel) {
        myCheckbox.setText(myProperty.getName());
      }
    }

    myValue = myProperty.getValue();
    myCheckbox.setSelected(SdkConstants.VALUE_TRUE.equalsIgnoreCase(myValue));
  }

  @Override
  public void activate() {
    myValue = SdkConstants.VALUE_TRUE.equalsIgnoreCase(myValue) ? SdkConstants.VALUE_FALSE : SdkConstants.VALUE_TRUE;
    stopEditing(myValue);
  }

  private void actionPerformed(ActionEvent e) {
    myValue = myCheckbox.isSelected() ? SdkConstants.VALUE_TRUE : SdkConstants.VALUE_FALSE;
    stopEditing(myValue);
  }
}
