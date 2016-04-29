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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NlEnumEditor implements NlComponentEditor {
  private static final int SMALL_WIDTH = 65;
  private static final JBColor DIM_TEXT_COLOR = new JBColor(Gray._128, Gray._128);
  private static final List<String> AVAILABLE_TEXT_SIZES = ImmutableList.of("8sp", "10sp", "12sp", "14sp", "18sp", "24sp", "30sp", "36sp");
  private static final List<String> AVAILABLE_LINE_SPACINGS = AVAILABLE_TEXT_SIZES;

  private final JPanel myPanel;
  private final JComboBox<ValueWithDisplayString> myCombo;
  private final FixedSizeButton myBrowseButton;
  private final boolean myIncludeBrowseButton;
  private final Listener myListener;

  private NlProperty myProperty;
  private boolean myUpdatingProperty;
  private int myAddedValueIndex;
  private JLabel myLabel;

  public interface Listener {
    /** Invoked when one of the enums is selected. */
    void itemPicked(@NotNull NlEnumEditor source, @Nullable String value);

    /** Invoked when a resource was selected using the resource picker. */
    void resourcePicked(@NotNull NlEnumEditor source, @NotNull String value);

    /** Invoked when the resource picker was cancelled. */
    void resourcePickerCancelled(@NotNull NlEnumEditor source);
  }

  public static NlEnumEditor createForTable(@NotNull Listener listener) {
    return new NlEnumEditor(listener, true, true);
  }

  public static NlEnumEditor createForInspector(@NotNull Listener listener) {
    return new NlEnumEditor(listener, false, false);
  }

  private NlEnumEditor(@NotNull Listener listener, boolean useDarculaUI, boolean includeBrowseButton) {
    myAddedValueIndex = -1; // nothing added
    myListener = listener;
    myIncludeBrowseButton = includeBrowseButton;
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));

    //noinspection unchecked
    myCombo = new ComboBox(SMALL_WIDTH);
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
    myCombo.registerKeyboardAction(event -> enter(),
                                   KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                   JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myCombo.registerKeyboardAction(event -> resourcePicked(),
                                   KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_MASK),
                                   JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    //noinspection unchecked
    myCombo.setRenderer(new ColoredListCellRenderer<ValueWithDisplayString>() {
      @Override
      protected void customizeCellRenderer(JList list, ValueWithDisplayString value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          if (!selected && !myProperty.isDefaultValue(value.getValue()) && Objects.equals(value.getValue(), getValue())) {
            myForeground = JBColor.BLUE;
          }
          else if (index == 0 || index == myAddedValueIndex) {
            myForeground = DIM_TEXT_COLOR;
          }
          append(value.toString());
        }
      }
    });
  }

  @Override
  public void setEnabled(boolean en) {
    myCombo.setEnabled(en);
    myBrowseButton.setEnabled(en);
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    if (property != myProperty) {
      setModel(property);
    }
    try {
      myUpdatingProperty = true;
      selectItem(ValueWithDisplayString.create(property.getValue(), property));
    }
    finally {
      myUpdatingProperty = false;
    }
  }

  @Override
  public void refresh() {
    if (myProperty != null) {
      setProperty(myProperty);
    }
  }

  @Override
  public void requestFocus() {
    myCombo.requestFocus();
  }

  @Override
  public JLabel getLabel() {
    return myLabel;
  }

  @Override
  public void setLabel(@NotNull JLabel label) {
    myLabel = label;
  }

  @Override
  public void setVisible(boolean visible) {
    myPanel.setVisible(visible);
    if (myLabel != null) {
      myLabel.setVisible(visible);
    }
  }

  private void setModel(@NotNull NlProperty property) {
    myProperty = property;

    myBrowseButton.setVisible(myIncludeBrowseButton && NlReferenceEditor.hasResourceChooser(property));

    AttributeDefinition definition = property.getDefinition();
    ValueWithDisplayString[] values;
    switch (property.getName()) {
      case SdkConstants.ATTR_FONT_FAMILY:
        values = ValueWithDisplayString.create(AndroidDomUtil.AVAILABLE_FAMILIES);
        break;
      case SdkConstants.ATTR_TEXT_SIZE:
        values = ValueWithDisplayString.create(AVAILABLE_TEXT_SIZES);
        break;
      case SdkConstants.ATTR_LINE_SPACING_EXTRA:
        values = ValueWithDisplayString.create(AVAILABLE_LINE_SPACINGS);
        break;
      case SdkConstants.ATTR_TEXT_APPEARANCE:
        values = createTextAttributeList(property);
        break;
      default:
        values = definition == null ? ValueWithDisplayString.EMPTY_ARRAY : ValueWithDisplayString.create(definition.getValues());
    }

    DefaultComboBoxModel<ValueWithDisplayString> newModel = new DefaultComboBoxModel<ValueWithDisplayString>(values) {
      @Override
      public void setSelectedItem(Object object) {
        if (object instanceof String) {
          String newValue = (String)object;
          object = new ValueWithDisplayString(newValue, newValue);
        }
        super.setSelectedItem(object);
      }
    };
    newModel.insertElementAt(ValueWithDisplayString.UNSET, 0);
    myCombo.setModel(newModel);
  }

  @Override
  @Nullable
  public NlProperty getProperty() {
    return myProperty;
  }

  private void selectItem(@NotNull ValueWithDisplayString value) {
    DefaultComboBoxModel<ValueWithDisplayString> model = (DefaultComboBoxModel<ValueWithDisplayString>)myCombo.getModel();
    int index = model.getIndexOf(value);
    if (index == -1) {
      if (myAddedValueIndex >= 0) {
        model.removeElementAt(myAddedValueIndex);
      }
      myAddedValueIndex = findBestInsertionPoint(value);
      model.insertElementAt(value, myAddedValueIndex);
    }
    if (!value.equals(model.getSelectedItem())) {
      model.setSelectedItem(value);
    }
    if (!myProperty.isDefaultValue(value.getValue())) {
      myCombo.getEditor().getEditorComponent().setForeground(JBColor.BLUE);
    }
    else {
      myCombo.getEditor().getEditorComponent().setForeground(UIManager.getColor("ComboBox.foreground"));
    }
  }

  private int findBestInsertionPoint(@NotNull ValueWithDisplayString newValue) {
    AttributeDefinition definition = myProperty.getDefinition();
    boolean isDimension = definition != null && definition.getFormats().contains(AttributeFormat.Dimension);
    int startIndex = 1;
    if (!isDimension) {
      return startIndex;
    }
    String newTextValue = newValue.getValue();
    Quantity newQuantity = newTextValue != null ? Quantity.parse(newTextValue) : null;
    if (newQuantity == null) {
      return startIndex;
    }

    ComboBoxModel<ValueWithDisplayString> model = myCombo.getModel();
    for (int index = startIndex, size = model.getSize(); index < size; index++) {
      String textValue = model.getElementAt(index).getValue();
      if (textValue != null) {
        Quantity quantity = Quantity.parse(textValue);
        if (newQuantity.compareTo(quantity) <= 0) {
          return index;
        }
      }
    }
    return model.getSize();
  }

  public Object getValue() {
    ValueWithDisplayString value = (ValueWithDisplayString)myCombo.getSelectedItem();
    return value.getValue();
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  public void showPopup() {
    myCombo.showPopup();
  }

  private void enter() {
    myListener.itemPicked(this, getText());
    myCombo.hidePopup();
  }

  private String getText() {
    String text = myCombo.getEditor().getItem().toString();
    return Quantity.addUnit(myProperty, text);
  }

  private void resourcePicked() {
    if (myProperty == null) {
      return;
    }
    ChooseResourceDialog dialog = NlReferenceEditor.showResourceChooser(myProperty);
    if (dialog.showAndGet()) {
      String value = dialog.getResourceName();
      selectItem(ValueWithDisplayString.create(value, myProperty));
      myListener.resourcePicked(this, value);
    } else {
      myListener.resourcePickerCancelled(this);
    }
  }

  private void comboValuePicked(ActionEvent event) {
    if (myUpdatingProperty || myProperty == null) {
      return;
    }
    ValueWithDisplayString value = (ValueWithDisplayString)myCombo.getModel().getSelectedItem();
    String actionCommand = event.getActionCommand();

    // only notify listener if a value has been picked from the combo box, not for every event from the combo
    // Note: these action names seem to be platform dependent?
    if (value != null && ("comboBoxEdited".equals(actionCommand) || "comboBoxChanged".equals(actionCommand))) {
      myListener.itemPicked(this, value.getValue());
    }
  }

  private static ValueWithDisplayString[] createTextAttributeList(@NotNull NlProperty property) {
    List<ValueWithDisplayString> list = new ArrayList<>();
    ResourceResolver resolver = property.getResolver();
    Map<String, ResourceValue> styles = resolver.getFrameworkResources().get(ResourceType.STYLE);
    for (String name : styles.keySet()) {
      ValueWithDisplayString value = ValueWithDisplayString.createTextAppearanceValue(name, SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX);
      if (value != null) {
        list.add(value);
      }
    }
    styles = resolver.getProjectResources().get(ResourceType.STYLE);
    for (String name : styles.keySet()) {
      ValueWithDisplayString value = ValueWithDisplayString.createTextAppearanceValue(name, SdkConstants.STYLE_RESOURCE_PREFIX);
      if (value != null) {
        list.add(value);
      }
    }
    list.sort((value, other) -> value.toString().compareTo(other.toString()));
    ValueWithDisplayString[] array = new ValueWithDisplayString[list.size()];
    list.toArray(array);
    return array;
  }
}
