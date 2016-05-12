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

import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.renderer.NlBooleanRenderer;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class NlBooleanEditor extends NlBaseComponentEditor implements NlComponentEditor {
  private final NlEditingListener myListener;
  private final JPanel myPanel;
  private final ThreeStateCheckBox myCheckbox;

  private NlProperty myProperty;
  private Object myValue;

  public static NlTableCellEditor createForTable() {
    NlTableCellEditor cellEditor = new NlTableCellEditor();
    cellEditor.init(new NlBooleanEditor(cellEditor, true));
    return cellEditor;
  }

  public static NlBooleanEditor createForInspector(@NotNull NlEditingListener listener) {
    return new NlBooleanEditor(listener, false);
  }

  private NlBooleanEditor(@NotNull NlEditingListener listener, boolean includeBrowseButton) {
    myListener = listener;
    myCheckbox = new ThreeStateCheckBox();
    myCheckbox.addActionListener(this::checkboxChanged);

    if (!includeBrowseButton) {
      myPanel = null;
    }
    else {
      myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));
      myPanel.add(myCheckbox, BorderLayout.LINE_START);

      FixedSizeButton browseButton = new FixedSizeButton(myCheckbox);
      browseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
      myPanel.add(browseButton, BorderLayout.LINE_END);

      browseButton.addActionListener(this::browse);
    }
  }

  @Nullable
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
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel != null ? myPanel : myCheckbox;
  }

  @Nullable
  @Override
  public Object getValue() {
    return myValue;
  }

  @Override
  public void activate() {
    myValue = NlBooleanRenderer.getNextState(myCheckbox.getState());
    myListener.stopEditing(this, myValue);
  }

  private void checkboxChanged(ActionEvent e) {
    myValue = NlBooleanRenderer.getBoolean(myCheckbox.getState());
    myListener.stopEditing(this, myValue);
  }

  private void browse(ActionEvent e) {
    ChooseResourceDialog dialog = NlReferenceEditor.showResourceChooser(myProperty);
    if (dialog.showAndGet()) {
      myValue = dialog.getResourceName();
      myListener.stopEditing(this, myValue);
    } else {
      myListener.cancelEditing(this);
    }
  }
}
