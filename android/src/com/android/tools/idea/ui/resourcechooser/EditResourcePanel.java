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
package com.android.tools.idea.ui.resourcechooser;

import com.android.ide.common.res2.ResourceItem;
import com.google.common.collect.Lists;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

public class EditResourcePanel extends JBScrollPane {
  private JPanel myFullPanel;
  private JTextField myResourceNameField;
  private JComboBox myVariantComboBox;
  private JBLabel myResourceNameMessage;
  private JTabbedPane myEditorTabs;

  private @NotNull List<ResourceItem> myVariants = Collections.emptyList();
  private final @NotNull Map<Component, ResourceEditorTab> myAllTabs = new HashMap<Component, ResourceEditorTab>();

  public EditResourcePanel(@Nullable String resourceName) {
    Color notificationsBackgroundColor = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.NOTIFICATION_BACKGROUND);
    myResourceNameMessage.setBackground(notificationsBackgroundColor == null ? JBColor.YELLOW : notificationsBackgroundColor);

    myEditorTabs.setUI(new SimpleTabUI());

    if (resourceName != null) {
      myResourceNameField.setText(resourceName);
    }

    setBorder(null);
    setViewportView(myFullPanel);
  }

  @Override
  @NotNull
  public Dimension getMinimumSize() {
    Insets insets = getInsets();
    return new Dimension(getViewport().getView().getMinimumSize().width + insets.left + insets.right, super.getMinimumSize().height);
  }

  public void setResourceName(@NotNull String resourceName) {
    myResourceNameField.setText(resourceName);
  }

  @NotNull
  public String getResourceName() {
    return myResourceNameField.getText();
  }

  @NotNull
  public String getResourceNameMessage() {
    return myResourceNameMessage.getText();
  }

  public void setResourceNameMessage(@NotNull String message) {
    myResourceNameMessage.setText(message);
  }

  @NotNull
  public JTextField getResourceNameField() {
    return myResourceNameField;
  }

  public void addTab(@NotNull ResourceEditorTab panel) {
    myAllTabs.put(panel.getFullPanel(), panel);
    myEditorTabs.addTab(panel.getTabTitle(), panel.getFullPanel());
  }

  @NotNull
  public ResourceEditorTab getSelectedTab() {
    return myAllTabs.get(myEditorTabs.getSelectedComponent());
  }

  public void setSelectedTab(@NotNull ResourceEditorTab panel) {
    myEditorTabs.setSelectedComponent(panel.getFullPanel());
  }

  public void addVariantActionListener(@NotNull ActionListener al) {
    myVariantComboBox.addActionListener(al);
  }

  public void setVariant(@NotNull List<ResourceItem> resources, @Nullable ResourceItem defaultValue) {
    if (resources.size() > 1) {
      resources = Lists.newArrayList(resources);
      Collections.sort(resources, (element1, element2) -> {
        File directory1 = element1.getFile().getParentFile();
        File directory2 = element2.getFile().getParentFile();
        return directory1.getName().compareTo(directory2.getName());
      });

      DefaultComboBoxModel model = new DefaultComboBoxModel();
      String defaultSelection = null;
      for (ResourceItem resource : resources) {
        String name = resource.getFile().getParentFile().getName();
        model.addElement(name);
        if (defaultSelection == null && resource == defaultValue) {
          defaultSelection = name;
        }
      }

      model.setSelectedItem(defaultSelection);
      myVariantComboBox.setModel(model);
    }
    myVariants = resources;
    myVariantComboBox.setVisible(resources.size() > 1);
  }

  @NotNull
  public ResourceItem getSelectedVariant() {
    return myVariants.size() > 1 ? myVariants.get(myVariantComboBox.getSelectedIndex()) : myVariants.get(0);
  }

  public void setSelectedVariant(@NotNull ResourceItem selectedVariant) {
    if (myVariants.size() == 1) {
      assert myVariants.get(0) == selectedVariant;
    }
    else {
      ComboBoxModel model = myVariantComboBox.getModel();
      ActionListener[] listeners = myVariantComboBox.getActionListeners();
      // if we are setting the selected item, we don't want to fire the listeners, as they should only listen to user selection
      for (ActionListener l : listeners) myVariantComboBox.removeActionListener(l);
      model.setSelectedItem(model.getElementAt(myVariants.indexOf(selectedVariant)));
      for (ActionListener l : listeners) myVariantComboBox.addActionListener(l);
    }
  }

  @NotNull
  public Collection<ResourceEditorTab> getAllTabs() {
    return myAllTabs.values();
  }
}
