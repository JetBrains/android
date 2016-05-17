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

import com.android.resources.ResourceType;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.FixedSizeButton;
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
import java.util.EnumSet;
import java.util.Set;

public abstract class NlBaseComponentEditor implements NlComponentEditor {
  protected static final JBColor DEFAULT_VALUE_TEXT_COLOR = new JBColor(Gray._128, Gray._128);
  protected static final JBColor CHANGED_VALUE_TEXT_COLOR = JBColor.BLUE;

  private final NlEditingListener myListener;

  private JLabel myLabel;
  private JButton myBrowseButton;

  public NlBaseComponentEditor(@NotNull NlEditingListener listener) {
    myListener = listener;
  }

  protected JComponent createBrowsePanel() {
    myBrowseButton = new FixedSizeButton(new JBCheckBox());
    myBrowseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
    JPanel browsePanel = new JPanel();
    browsePanel.setLayout(new BoxLayout(browsePanel, BoxLayout.X_AXIS));
    browsePanel.add(myBrowseButton);
    myBrowseButton.addActionListener(event -> displayResourcePicker());
    return browsePanel;
  }

  @Nullable
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
    getComponent().setVisible(visible);
    if (myLabel != null) {
      myLabel.setVisible(visible);
    }
  }

  @Override
  public void refresh() {
    NlProperty property = getProperty();
    if (property != null) {
      setProperty(property);
    }
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    if (myBrowseButton != null) {
      myBrowseButton.setVisible(hasResourceChooser(property));
    }
  }

  @Override
  @Nullable
  public Object getValue() {
    return null;
  }

  @Override
  public void activate() {
  }

  @Override
  public void setEnabled(boolean enabled) {
    getComponent().setEnabled(enabled);
  }

  @Override
  public void requestFocus() {
    getComponent().requestFocus();
  }

  protected void cancelEditing() {
    myListener.cancelEditing(this);
  }

  protected void stopEditing(@Nullable Object newValue) {
    myListener.stopEditing(this, newValue);
    refresh();
  }

  protected void displayResourcePicker() {
    NlProperty property = getProperty();
    if (property == null) {
      return;
    }
    ChooseResourceDialog dialog = showResourceChooser(property);
    if (dialog.showAndGet()) {
      stopEditing(dialog.getResourceName());
    } else {
      cancelEditing();
    }
  }

  private static ChooseResourceDialog showResourceChooser(@NotNull NlProperty property) {
    Module module = property.getModel().getModule();
    AttributeDefinition definition = property.getDefinition();
    ResourceType[] types = getResourceTypes(definition);
    return new ChooseResourceDialog(module, types, property.getValue(), property.getTag());
  }

  public static boolean hasResourceChooser(@NotNull NlProperty property) {
    return getResourceTypes(property.getDefinition()).length > 0;
  }

  @NotNull
  protected static ResourceType[] getResourceTypes(@Nullable AttributeDefinition definition) {
    Set<AttributeFormat> formats = definition != null ? definition.getFormats() : EnumSet.allOf(AttributeFormat.class);
    // for some special known properties, we can narrow down the possible types (rather than the all encompassing reference type)
    ResourceType type = definition != null ? AndroidDomUtil.SPECIAL_RESOURCE_TYPES.get(definition.getName()) : null;
    return type == null ? AttributeFormat.convertTypes(formats) : new ResourceType[]{type};
  }
}
