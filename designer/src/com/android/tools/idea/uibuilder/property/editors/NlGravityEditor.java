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

import com.android.assetstudiolib.AndroidVectorIcons;
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItem;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;
import static java.awt.GridBagConstraints.*;

public class NlGravityEditor extends NlBaseComponentEditor implements NlComponentEditor {
  private static final int SMALL_WIDTH = 65;
  private static final List<String> ALL_VERTICAL_ITEMS = ImmutableList.of(
    GRAVITY_VALUE_TOP, GRAVITY_VALUE_BOTTOM, GRAVITY_VALUE_CENTER_VERTICAL, GRAVITY_VALUE_CENTER, GRAVITY_VALUE_FILL_VERTICAL,
    GRAVITY_VALUE_FILL);
  private static final List<String> ALL_HORIZONTAL_ITEMS = ImmutableList.of(
    GRAVITY_VALUE_START, GRAVITY_VALUE_END, GRAVITY_VALUE_LEFT, GRAVITY_VALUE_RIGHT, GRAVITY_VALUE_CENTER_HORIZONTAL, GRAVITY_VALUE_CENTER,
    GRAVITY_VALUE_FILL_HORIZONTAL, GRAVITY_VALUE_FILL);

  private final JPanel myPanel;
  private final NlBooleanIconEditor myHorizontalClipButton;
  private final NlBooleanIconEditor myVerticalClipButton;
  private final JComboBox<ValueWithDisplayString> myHorizontalGravity;
  private final JComboBox<ValueWithDisplayString> myVerticalGravity;

  private NlFlagPropertyItem myProperty;
  private JLabel myLabel;
  private boolean myUpdatingProperty;

  public interface Listener {
    void valueChanged(@NotNull NlGravityEditor editor);
  }

  public NlGravityEditor() {
    super(DEFAULT_LISTENER);
    myPanel = new JPanel(new GridBagLayout());
    myHorizontalClipButton = new NlBooleanIconEditor(AndroidVectorIcons.LayoutEditorIcons.Clip);
    myVerticalClipButton = new NlBooleanIconEditor(AndroidVectorIcons.LayoutEditorIcons.Clip);
    //noinspection unchecked
    myHorizontalGravity = new ComboBox(getHorizontalModel(), SMALL_WIDTH);
    //noinspection unchecked
    myVerticalGravity = new ComboBox(getVerticalModel(), SMALL_WIDTH);
    myHorizontalGravity.addActionListener(
      event -> comboValuePicked(myHorizontalGravity, ALL_HORIZONTAL_ITEMS, GRAVITY_VALUE_CENTER_VERTICAL, GRAVITY_VALUE_FILL_VERTICAL));
    myVerticalGravity.addActionListener(
      event -> comboValuePicked(myVerticalGravity, ALL_VERTICAL_ITEMS, GRAVITY_VALUE_CENTER_HORIZONTAL, GRAVITY_VALUE_FILL_HORIZONTAL));
    addRow(0, "Horizontal:", myHorizontalGravity, myHorizontalClipButton.getComponent());
    addRow(1, "Vertical:", myVerticalGravity, myVerticalClipButton.getComponent());
  }

  private void addRow(int row, @NotNull String direction, @NotNull JComponent comboBox, @NotNull Component button) {
    JLabel label = new JBLabel(direction);
    Insets margin = new Insets(0, 20, 0, 0);
    Insets insets = new Insets(0, 0, 0, 0);
    myPanel.add(label,    new GridBagConstraints(0, row, 1, 1, 0, 0, WEST, NONE, margin, 0, 0));
    myPanel.add(comboBox, new GridBagConstraints(1, row, 1, 1, 1, 0, CENTER, HORIZONTAL, insets, 0, 0));
    myPanel.add(button,   new GridBagConstraints(2, row, 1, 1, 0, 0, CENTER, NONE, insets, 0, 0));
  }

  private void comboValuePicked(@NotNull JComboBox combo,
                                @NotNull List<String> allItemsInDirection,
                                @NotNull String oppositeCenter,
                                @NotNull String oppositeFill) {
    if (myUpdatingProperty || myProperty == null) {
      return;
    }
    Set<String> itemsToAdd = new HashSet<>();
    Set<String> itemsToRemove = new HashSet<>();

    String value = ((ValueWithDisplayString)combo.getSelectedItem()).getValue();
    if (!StringUtil.isEmpty(value)) {
      itemsToAdd.add(value);
    }
    itemsToRemove.addAll(allItemsInDirection);
    if (myProperty.isItemSet(GRAVITY_VALUE_CENTER)) {
      itemsToAdd.add(oppositeCenter);
    }
    if (myProperty.isItemSet(GRAVITY_VALUE_CENTER)) {
      itemsToAdd.add(oppositeFill);
    }
    myProperty.updateItems(itemsToAdd, itemsToRemove);
  }


  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void setEnabled(boolean enabled) {
    myHorizontalGravity.setEnabled(enabled);
    myVerticalGravity.setEnabled(enabled);
    myHorizontalClipButton.getComponent().setEnabled(enabled);
    myVerticalClipButton.getComponent().setEnabled(enabled);
  }

  @Override
  public void setVisible(boolean visible) {
    myPanel.setVisible(visible);
    if (myLabel != null) {
      myLabel.setVisible(visible);
    }
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    myUpdatingProperty = true;
    try {
      myProperty = (NlFlagPropertyItem)property;
      myHorizontalClipButton.setProperty(property.getChildProperty(GRAVITY_VALUE_CLIP_HORIZONTAL));
      myVerticalClipButton.setProperty(property.getChildProperty(GRAVITY_VALUE_CLIP_VERTICAL));
      myHorizontalGravity.setSelectedItem(getSelectedHorizontalValue());
      myVerticalGravity.setSelectedItem(getSelectedVerticalValue());
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
    myHorizontalGravity.requestFocus();
  }

  @Override
  public NlFlagPropertyItem getProperty() {
    return myProperty;
  }

  @Override
  public void setLabel(@NotNull JLabel label) {
    myLabel = label;
  }

  @Override
  public JLabel getLabel() {
    return myLabel;
  }

  private ValueWithDisplayString getSelectedHorizontalValue() {
    if (myProperty.isAnyItemSet(GRAVITY_VALUE_FILL, GRAVITY_VALUE_FILL_HORIZONTAL)) {
      return new ValueWithDisplayString("fill", GRAVITY_VALUE_FILL_HORIZONTAL);
    }
    if (myProperty.isAnyItemSet(GRAVITY_VALUE_CENTER, GRAVITY_VALUE_CENTER_HORIZONTAL)) {
      return new ValueWithDisplayString("center", GRAVITY_VALUE_CENTER_HORIZONTAL);
    }
    if (myProperty.isAnyItemSet(GRAVITY_VALUE_START, GRAVITY_VALUE_LEFT)) {
      return new ValueWithDisplayString("start", GRAVITY_VALUE_START);
    }
    if (myProperty.isAnyItemSet(GRAVITY_VALUE_END, GRAVITY_VALUE_RIGHT)) {
      return new ValueWithDisplayString("end", GRAVITY_VALUE_END);
    }
    return new ValueWithDisplayString("none", "");
  }

  private ValueWithDisplayString getSelectedVerticalValue() {
    if (myProperty.isAnyItemSet(GRAVITY_VALUE_FILL, GRAVITY_VALUE_FILL_VERTICAL)) {
      return new ValueWithDisplayString("fill", GRAVITY_VALUE_FILL_VERTICAL);
    }
    if (myProperty.isAnyItemSet(GRAVITY_VALUE_CENTER, GRAVITY_VALUE_CENTER_VERTICAL)) {
      return new ValueWithDisplayString("center", GRAVITY_VALUE_CENTER_VERTICAL);
    }
    if (myProperty.isAnyItemSet(GRAVITY_VALUE_TOP)) {
      return new ValueWithDisplayString("top", GRAVITY_VALUE_TOP);
    }
    if (myProperty.isAnyItemSet(GRAVITY_VALUE_BOTTOM)) {
      return new ValueWithDisplayString("bottom", GRAVITY_VALUE_BOTTOM);
    }
    return new ValueWithDisplayString("none", "");
  }

  private static ComboBoxModel<ValueWithDisplayString> getHorizontalModel() {
    return new DefaultComboBoxModel<>(new ValueWithDisplayString[]{
      new ValueWithDisplayString("none", ""),
      new ValueWithDisplayString("start", GRAVITY_VALUE_START),
      new ValueWithDisplayString("end", GRAVITY_VALUE_END),
      new ValueWithDisplayString("center", GRAVITY_VALUE_CENTER_HORIZONTAL),
      new ValueWithDisplayString("fill", GRAVITY_VALUE_FILL_HORIZONTAL),
    });
  }

  private static ComboBoxModel<ValueWithDisplayString> getVerticalModel() {
    return new DefaultComboBoxModel<>(new ValueWithDisplayString[]{
      new ValueWithDisplayString("none", ""),
      new ValueWithDisplayString("top", GRAVITY_VALUE_TOP),
      new ValueWithDisplayString("bottom", GRAVITY_VALUE_BOTTOM),
      new ValueWithDisplayString("center", GRAVITY_VALUE_CENTER_VERTICAL),
      new ValueWithDisplayString("fill", GRAVITY_VALUE_FILL_VERTICAL),
    });
  }
}
