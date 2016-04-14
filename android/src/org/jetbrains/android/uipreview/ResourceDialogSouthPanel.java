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
package org.jetbrains.android.uipreview;

import com.android.ide.common.res2.ResourceItem;
import com.google.common.collect.Lists;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import javax.swing.*;

public class ResourceDialogSouthPanel {
  private JTextField myResourceNameField;
  private JBLabel myResourceNameMessage;
  private JPanel myFullPanel;
  private JPanel myExpertPlaceholder;
  private JPanel myExpertPanel;
  private JComboBox myVariantComboBox;
  private HideableDecorator myExpertDecorator;

  private @NotNull List<ResourceItem> myVariants = Collections.emptyList();

  public ResourceDialogSouthPanel() {
    Color backgroundColor = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.NOTIFICATION_BACKGROUND);
    myResourceNameMessage.setBackground(backgroundColor == null ? JBColor.YELLOW : backgroundColor);
    myExpertDecorator = new HideableDecorator(myExpertPlaceholder, "Device Configuration", true) {
      private void pack() {
        // Hack to not shrink the window too small when we close or open the advanced panel.
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            SwingUtilities.getWindowAncestor(myExpertPlaceholder).pack();
          }
        });
      }

      @Override
      protected void on() {
        super.on();
        pack();
      }

      @Override
      protected void off() {
        super.off();
        pack();
      }
    };
    myExpertDecorator.setContentComponent(myExpertPanel);
  }

  public void setExpertPanel(Component comp) {
    myExpertPanel.removeAll();
    myExpertPanel.add(comp);
  }

  public void showExpertPanel(boolean show) {
    myExpertPlaceholder.setVisible(show);
  }

  public JPanel getFullPanel() {
    return myFullPanel;
  }

  public JBLabel getResourceNameMessage() {
    return myResourceNameMessage;
  }

  public JTextField getResourceNameField() {
    return myResourceNameField;
  }

  public void setOn(boolean on) {
    myExpertDecorator.setOn(on);
  }

  public void addVariantActionListener(@NotNull ActionListener al) {
    myVariantComboBox.addActionListener(al);
  }

  public void setVariant(@NotNull List<ResourceItem> resources, @Nullable ResourceItem defaultValue) {
    if (resources.size() > 1) {
      resources = Lists.newArrayList(resources);
      Collections.sort(resources, new Comparator<ResourceItem>() {
        @Override
        public int compare(ResourceItem element1, ResourceItem element2) {
          File directory1 = element1.getFile().getParentFile();
          File directory2 = element2.getFile().getParentFile();
          return directory1.getName().compareTo(directory2.getName());
        }
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
      model.setSelectedItem(model.getElementAt(myVariants.indexOf(selectedVariant)));
    }
  }
}
