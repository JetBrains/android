/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.editors.BaseComponentEditor;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.renderer.NlBooleanRenderer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class NlBooleanEditor extends BaseComponentEditor implements NlComponentEditor {
  @VisibleForTesting static final String TIP_TEXT_DONT_CARE = "(No value)";
  @VisibleForTesting static final String TIP_TEXT_NOT_SELECTED = "false";
  @VisibleForTesting static final String TIP_TEXT_SELECTED = "true";

  private final JPanel myPanel;
  private final ThreeStateCheckBox myCheckbox;
  private final BrowsePanel myBrowsePanel;

  private NlProperty myProperty;
  private Object myValue;

  public static NlTableCellEditor createForTable() {
    NlTableCellEditor cellEditor = new NlTableCellEditor();
    BrowsePanel browsePanel = new BrowsePanel(cellEditor, true);
    cellEditor.init(new NlBooleanEditor(cellEditor, browsePanel), browsePanel);
    return cellEditor;
  }

  public static NlBooleanEditor createForInspector(@NotNull NlEditingListener listener) {
    return new NlBooleanEditor(listener, null);
  }

  private NlBooleanEditor(@NotNull NlEditingListener listener, @Nullable BrowsePanel browsePanel) {
    super(listener);
    myCheckbox = new ThreeStateCheckBox();
    myCheckbox.addActionListener(this::checkboxChanged);
    myPanel = new AdtSecondaryPanel(new BorderLayout(JBUI.scale(HORIZONTAL_COMPONENT_GAP), 0));
    myPanel.add(myCheckbox, BorderLayout.LINE_START);
    myPanel.setBorder(JBUI.Borders.empty(VERTICAL_SPACING, 0, HORIZONTAL_SPACING, 0));

    myBrowsePanel = browsePanel;
    if (browsePanel != null) {
      myPanel.add(browsePanel, BorderLayout.LINE_END);
    }
    myProperty = EmptyProperty.INSTANCE;
  }

  @NotNull
  @Override
  public NlProperty getProperty() {
    return myProperty;
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    myProperty = property;

    String propValue = myProperty.getValue();
    myValue = propValue;
    ThreeStateCheckBox.State state = NlBooleanRenderer.getState(propValue);
    myCheckbox.setState(state == null ? ThreeStateCheckBox.State.NOT_SELECTED : state);
    updateTipText();
    if (myBrowsePanel != null) {
      myBrowsePanel.setProperty(property);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public Object getValue() {
    return myValue;
  }

  private void checkboxChanged(@SuppressWarnings("unused") ActionEvent event) {
    myValue = NlBooleanRenderer.getBoolean(myCheckbox.getState());
    stopEditing(myValue);

    updateTipText();
  }

  private void updateTipText() {
    switch(myCheckbox.getState()) {
      case DONT_CARE:
        myCheckbox.setToolTipText(TIP_TEXT_DONT_CARE);
        break;
      case NOT_SELECTED:
        myCheckbox.setToolTipText(TIP_TEXT_NOT_SELECTED);
        break;
      case SELECTED:
        myCheckbox.setToolTipText(TIP_TEXT_SELECTED);
        break;
    }
  }
}
