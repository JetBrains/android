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

import com.android.annotations.NonNull;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class NlEnumEditor implements ActionListener {
  public static final String UNSET = "<unset>";

  private final JPanel myPanel;
  private final JComboBox myCombo;
  private final FixedSizeButton myBrowseButton;

  private final Listener myListener;
  private NlProperty myProperty;

  public interface Listener {
    /** Invoked when one of the enums is selected. */
    void itemPicked(@NonNull NlEnumEditor source, @NonNull String value);

    /** Invoked when a resource was selected using the resource picker. */
    void resourcePicked(@NonNull NlEnumEditor source, @NonNull String value);

    /** Invoked when the resource picker was cancelled. */
    void resourcePickerCancelled(@NonNull NlEnumEditor source);
  }

  public NlEnumEditor(@NonNull Listener listener) {
    myListener = listener;
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));

    myCombo = new ComboBox();
    myCombo.setEditable(true);
    myPanel.add(myCombo, BorderLayout.CENTER);

    myBrowseButton = new FixedSizeButton(new JBCheckBox());
    myBrowseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
    myPanel.add(myBrowseButton, BorderLayout.LINE_END);

    // TODO: Hook up general editing. (Such that the control would recognize "100dp" for example)
    myCombo.addActionListener(this);
    myBrowseButton.addActionListener(this);
  }

  public void setEnabled(boolean en) {
    myCombo.setEnabled(en);
    myBrowseButton.setEnabled(en);
  }


  public void setProperty(@NonNull NlProperty property) {
    myProperty = property;
    String propValue = StringUtil.notNullize(property.getValue());

    myBrowseButton.setVisible(NlReferenceEditor.hasResourceChooser(property));

    AttributeDefinition definition = property.getDefinition();
    String[] values = definition == null ? ArrayUtil.EMPTY_STRING_ARRAY : definition.getValues();

    DefaultComboBoxModel model = new DefaultComboBoxModel(values);
    model.insertElementAt(UNSET, 0);
    selectItem(model, propValue);
    myCombo.setModel(model);
  }

  private static void selectItem(@NonNull DefaultComboBoxModel model, @NonNull String value) {
    if (model.getIndexOf(value) == -1) {
      model.insertElementAt(value, 1);
    }
    model.setSelectedItem(value);
  }

  public Object getValue() {
    return myCombo.getSelectedItem();
  }

  @NonNull
  public Component getComponent() {
    return myPanel;
  }

  public void showPopup() {
    myCombo.showPopup();
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (myProperty == null) {
      return;
    }

    if (e.getSource() == myBrowseButton) {
      ChooseResourceDialog dialog = NlReferenceEditor.showResourceChooser(myProperty);
      if (dialog.showAndGet()) {
        String value = dialog.getResourceName();

        DefaultComboBoxModel model = (DefaultComboBoxModel)myCombo.getModel();
        selectItem(model, value);

        myListener.resourcePicked(this, value);
      } else {
        myListener.resourcePickerCancelled(this);
      }
    }
    else if (e.getSource() == myCombo) {
      Object value = myCombo.getModel().getSelectedItem();
      String actionCommand = e.getActionCommand();

      // only notify listener if a value has been picked from the combo box, not for every event from the combo
      // Note: these action names seem to be platform dependent?
      if ("comboBoxEdited".equals(actionCommand) || "comboBoxChanged".equals(actionCommand)) {
        myListener.itemPicked(this, value.toString());
      }
    }
  }
}
