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
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

public class NlEnumEditor {
  public static final String UNSET = "<unset>";
  private static final List<String> AVAILABLE_TEXT_SIZES = ImmutableList.of("8sp", "10sp", "12sp", "14sp", "18sp", "24sp", "30sp", "36sp");
  private static final List<String> AVAILABLE_LINE_SPACINGS = AVAILABLE_TEXT_SIZES;

  private final JPanel myPanel;
  private final JComboBox<String> myCombo;
  private final FixedSizeButton myBrowseButton;
  private final boolean myIncludeBrowseButton;
  private final boolean myIncludeUnset;

  private final Listener myListener;
  private NlProperty myProperty;

  public interface Listener {
    /** Invoked when one of the enums is selected. */
    void itemPicked(@NotNull NlEnumEditor source, @NotNull String value);

    /** Invoked when a resource was selected using the resource picker. */
    void resourcePicked(@NotNull NlEnumEditor source, @NotNull String value);

    /** Invoked when the resource picker was cancelled. */
    void resourcePickerCancelled(@NotNull NlEnumEditor source);
  }

  public static NlEnumEditor create(@NotNull Listener listener) {
    return new NlEnumEditor(listener, true, true);
  }

  public static NlEnumEditor createWithoutBrowseButton(@NotNull Listener listener) {
    return new NlEnumEditor(listener, false, false);
  }

  private NlEnumEditor(@NotNull Listener listener, boolean includeBrowseButton, boolean includeUnset) {
    myListener = listener;
    myIncludeBrowseButton = includeBrowseButton;
    myIncludeUnset = includeUnset;
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));

    //noinspection unchecked
    myCombo = (JComboBox<String>)new ComboBox();
    myCombo.setEditable(true);
    myPanel.add(myCombo, BorderLayout.CENTER);

    myBrowseButton = new FixedSizeButton(new JBCheckBox());
    myBrowseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
    myBrowseButton.setVisible(includeBrowseButton);
    myPanel.add(myBrowseButton, BorderLayout.LINE_END);

    myBrowseButton.addActionListener(event -> resourcePicked());
    myCombo.addActionListener(this::comboValuePicked);
    myCombo.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent event) {
        if (event.getKeyChar() == KeyEvent.VK_ENTER) {
          myListener.itemPicked(NlEnumEditor.this, myCombo.getEditor().getItem().toString());
        }
      }
    });
  }

  public void setEnabled(boolean en) {
    myCombo.setEnabled(en);
    myBrowseButton.setEnabled(en);
  }

  public void setProperty(@NotNull NlProperty property) {
    myProperty = property;
    String propValue = StringUtil.notNullize(property.getValue());

    myBrowseButton.setVisible(myIncludeBrowseButton && NlReferenceEditor.hasResourceChooser(property));

    AttributeDefinition definition = property.getDefinition();
    String[] values;
    switch (property.getName()) {
      case SdkConstants.ATTR_FONT_FAMILY:
        values = ArrayUtil.toStringArray(AndroidDomUtil.AVAILABLE_FAMILIES);
        break;
      case SdkConstants.ATTR_TEXT_SIZE:
        values = ArrayUtil.toStringArray(AVAILABLE_TEXT_SIZES);
        break;
      case SdkConstants.ATTR_LINE_SPACING_EXTRA:
        values = ArrayUtil.toStringArray(AVAILABLE_LINE_SPACINGS);
        break;
      default:
        values = definition == null ? ArrayUtil.EMPTY_STRING_ARRAY : definition.getValues();
    }

    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(values);
    if (myIncludeUnset) {
      model.insertElementAt(UNSET, 0);
    }
    selectItem(model, propValue);
    myCombo.setModel(model);
  }

  @Nullable
  public NlProperty getProperty() {
    return myProperty;
  }

  private void selectItem(@NotNull DefaultComboBoxModel<String> model, @NotNull String value) {
    if (model.getIndexOf(value) == -1) {
      model.insertElementAt(value, myIncludeUnset ? 1 : 0);
    }
    model.setSelectedItem(value);
  }

  public Object getValue() {
    return myCombo.getSelectedItem();
  }

  @NotNull
  public Component getComponent() {
    return myPanel;
  }

  public void showPopup() {
    myCombo.showPopup();
  }

  private void resourcePicked() {
    if (myProperty == null) {
      return;
    }
    ChooseResourceDialog dialog = NlReferenceEditor.showResourceChooser(myProperty);
    if (dialog.showAndGet()) {
      String value = dialog.getResourceName();

      DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>)myCombo.getModel();
      selectItem(model, value);

      myListener.resourcePicked(this, value);
    } else {
      myListener.resourcePickerCancelled(this);
    }
  }

  private void comboValuePicked(ActionEvent event) {
    if (myProperty == null) {
      return;
    }
    Object value = myCombo.getModel().getSelectedItem();
    String actionCommand = event.getActionCommand();

    // only notify listener if a value has been picked from the combo box, not for every event from the combo
    // Note: these action names seem to be platform dependent?
    if ("comboBoxEdited".equals(actionCommand) || "comboBoxChanged".equals(actionCommand)) {
      myListener.itemPicked(this, value.toString());
    }
  }
}
