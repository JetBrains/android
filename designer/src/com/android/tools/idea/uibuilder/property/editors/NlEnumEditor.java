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
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
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
import java.awt.event.*;
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
  private boolean myValueAdded;
  private boolean myUpdatingProperty;

  public interface Listener {
    /** Invoked when one of the enums is selected. */
    void itemPicked(@NotNull NlEnumEditor source, @NotNull String value);

    /** Invoked when a resource was selected using the resource picker. */
    void resourcePicked(@NotNull NlEnumEditor source, @NotNull String value);

    /** Invoked when the resource picker was cancelled. */
    void resourcePickerCancelled(@NotNull NlEnumEditor source);
  }

  public static NlEnumEditor createForTable(@NotNull Listener listener) {
    return new NlEnumEditor(listener, true, true, true);
  }

  public static NlEnumEditor createForInspector(@NotNull Listener listener) {
    return new NlEnumEditor(listener, false, false, false);
  }

  private NlEnumEditor(@NotNull Listener listener, boolean useDarculaUI, boolean includeBrowseButton, boolean includeUnset) {
    myListener = listener;
    myIncludeBrowseButton = includeBrowseButton;
    myIncludeUnset = includeUnset;
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));

    //noinspection unchecked
    myCombo = (JComboBox<String>)new ComboBox();
    if (useDarculaUI) {
      // Some LAF will draw a beveled border which does not look good in the table grid.
      // Avoid that by explicit use of the Darcula UI for combo boxes when used as a cell editor in the table.
      myCombo.setUI(new DarculaComboBoxUI(myCombo));
    }
    myCombo.setEditable(true);
    myPanel.add(myCombo, BorderLayout.CENTER);

    myBrowseButton = new FixedSizeButton(new JBCheckBox());
    myBrowseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
    myBrowseButton.setVisible(includeBrowseButton);
    myPanel.add(myBrowseButton, BorderLayout.LINE_END);

    myBrowseButton.addActionListener(event -> resourcePicked());
    myCombo.addActionListener(this::comboValuePicked);
    myCombo.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent event) {
        myListener.itemPicked(NlEnumEditor.this, myCombo.getEditor().getItem().toString());
      }
    });
    JComponent editor = (JComponent) myCombo.getEditor().getEditorComponent();
    editor.registerKeyboardAction(event -> enter(),
                                  KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                  JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myCombo.registerKeyboardAction(event -> resourcePicked(),
                                  KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_MASK),
                                  JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  public void setEnabled(boolean en) {
    myCombo.setEnabled(en);
    myBrowseButton.setEnabled(en);
  }

  public void setProperty(@NotNull NlProperty property) {
    if (property != myProperty) {
      myProperty = property;

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

      DefaultComboBoxModel<String> newModel = new DefaultComboBoxModel<>(values);
      if (myIncludeUnset) {
        newModel.insertElementAt(UNSET, 0);
      }
      myCombo.setModel(newModel);
    }
    try {
      myUpdatingProperty = true;
      selectItem(StringUtil.notNullize(property.getValue()));
    }
    finally {
      myUpdatingProperty = false;
    }
  }

  @Nullable
  public NlProperty getProperty() {
    return myProperty;
  }

  private void selectItem(@NotNull String value) {
    DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>)myCombo.getModel();
    if (model.getIndexOf(value) == -1) {
      int addedIndex = myIncludeUnset ? 1 : 0;
      if (myValueAdded) {
        model.removeElementAt(addedIndex);
      }
      model.insertElementAt(value, addedIndex);
      myValueAdded = true;
    }
    if (!value.equals(model.getSelectedItem())) {
      model.setSelectedItem(value);
    }
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

  private void enter() {
    myListener.itemPicked(this, myCombo.getEditor().getItem().toString());
    myCombo.hidePopup();
  }

  private void resourcePicked() {
    if (myProperty == null) {
      return;
    }
    ChooseResourceDialog dialog = NlReferenceEditor.showResourceChooser(myProperty);
    if (dialog.showAndGet()) {
      String value = dialog.getResourceName();

      selectItem(value);

      myListener.resourcePicked(this, value);
    } else {
      myListener.resourcePickerCancelled(this);
    }
  }

  private void comboValuePicked(ActionEvent event) {
    if (myUpdatingProperty || myProperty == null) {
      return;
    }
    Object value = myCombo.getModel().getSelectedItem();
    String actionCommand = event.getActionCommand();

    // only notify listener if a value has been picked from the combo box, not for every event from the combo
    // Note: these action names seem to be platform dependent?
    if (value != null && ("comboBoxEdited".equals(actionCommand) || "comboBoxChanged".equals(actionCommand))) {
      myListener.itemPicked(this, value.toString());
    }
  }
}
