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
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColoredListCellRenderer;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NlEnumEditor extends NlBaseComponentEditor implements NlComponentEditor {
  private static final int SMALL_WIDTH = 65;
  private static final List<String> AVAILABLE_TEXT_SIZES = ImmutableList.of("8sp", "10sp", "12sp", "14sp", "18sp", "24sp", "30sp", "36sp");
  private static final List<String> AVAILABLE_LINE_SPACINGS = AVAILABLE_TEXT_SIZES;
  private static final List<String> AVAILABLE_TYPEFACES = ImmutableList.of("normal", "sans", "serif", "monospace");
  private static final List<String> AVAILABLE_SIZES = ImmutableList.of("match_parent", "wrap_content");

  private final JPanel myPanel;
  private final JComboBox<ValueWithDisplayString> myCombo;

  private NlProperty myProperty;
  private boolean myUpdatingProperty;
  private int myAddedValueIndex;

  public static NlTableCellEditor createForTable() {
    NlTableCellEditor cellEditor = new NlTableCellEditor();
    cellEditor.init(new NlEnumEditor(cellEditor, cellEditor, true));
    return cellEditor;
  }

  public static NlEnumEditor createForInspector(@NotNull NlEditingListener listener) {
    return new NlEnumEditor(listener, null, false);
  }

  private NlEnumEditor(@NotNull NlEditingListener listener,
                       @Nullable BrowsePanel.Context context,
                       boolean useDarculaUI) {
    super(listener);
    myAddedValueIndex = -1; // nothing added
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
    if (context != null) {
      myPanel.add(new BrowsePanel(context, true), BorderLayout.LINE_END);
    }

    myCombo.addActionListener(this::comboValuePicked);
    myCombo.registerKeyboardAction(event -> enter(),
                                   KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                   JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    //noinspection unchecked
    myCombo.setRenderer(new ColoredListCellRenderer<ValueWithDisplayString>() {
      @Override
      protected void customizeCellRenderer(JList list, ValueWithDisplayString value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          boolean isDefaultValue = myProperty.isDefaultValue(value.getValue());
          if (!selected && !isDefaultValue && Objects.equals(value.getValue(), getValue())) {
            myForeground = CHANGED_VALUE_TEXT_COLOR;
          }
          else if (index == 0 || isDefaultValue) {
            myForeground = DEFAULT_VALUE_TEXT_COLOR;
          }
          append(value.toString());
        }
      }
    });
    myCombo.getEditor().getEditorComponent().addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myCombo.getEditor().selectAll();
      }

      @Override
      public void focusLost(FocusEvent e) {
        ComboBoxEditor editor = myCombo.getEditor();
        if (editor instanceof JTextComponent) {
          ((JTextComponent)editor).select(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
      }
    });
  }

  @Override
  public void setEnabled(boolean en) {
    myCombo.setEnabled(en);
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
  public void requestFocus() {
    myCombo.requestFocus();
  }

  private void setModel(@NotNull NlProperty property) {
    myProperty = property;

    AttributeDefinition definition = property.getDefinition();
    ValueWithDisplayString[] values;
    switch (property.getName()) {
      case SdkConstants.ATTR_FONT_FAMILY:
        values = ValueWithDisplayString.create(AndroidDomUtil.AVAILABLE_FAMILIES);
        break;
      case SdkConstants.ATTR_TYPEFACE:
        values = ValueWithDisplayString.create(AVAILABLE_TYPEFACES);
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
      case SdkConstants.ATTR_LAYOUT_HEIGHT:
      case SdkConstants.ATTR_LAYOUT_WIDTH:
      case SdkConstants.ATTR_DROPDOWN_HEIGHT:
      case SdkConstants.ATTR_DROPDOWN_WIDTH:
        values = ValueWithDisplayString.create(AVAILABLE_SIZES);
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
      myCombo.getEditor().getEditorComponent().setForeground(CHANGED_VALUE_TEXT_COLOR);
    }
    else {
      myCombo.getEditor().getEditorComponent().setForeground(DEFAULT_VALUE_TEXT_COLOR);
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

  @Override
  public Object getValue() {
    ValueWithDisplayString value = (ValueWithDisplayString)myCombo.getSelectedItem();
    return value.getValue();
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  private void enter() {
    String newValue = getText();
    selectItem(ValueWithDisplayString.create(newValue, myProperty));
    stopEditing(newValue);
    myCombo.hidePopup();
  }

  private String getText() {
    String text = myCombo.getEditor().getItem().toString();
    return Quantity.addUnit(myProperty, text);
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
      stopEditing(value.getValue());
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
