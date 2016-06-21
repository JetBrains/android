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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItemResolver;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.res.AppResourceRepository;
import com.google.common.collect.Lists;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Locale;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.tools.idea.ui.resourcechooser.ResourceChooserItem.DEFAULT_FOLDER_NAME;

/**
 * Panel used to edit bitmaps, XML drawables and solid colors
 */
public class ResourceDrawablePanel extends JBScrollPane implements ActionListener {
  private final ChooseResourceDialog myDialog;
  private JBLabel myNameLabel;
  private JBLabel myImageLabel;
  private JComboBox myQualifierCombo;
  private JBLabel myTypeLabel;
  private JPanel myPanel;
  private JPanel myResolvedPanel;
  private JBScrollPane myResolvedScrollPane;
  private boolean myIgnoreSelection;
  private ResourceChooserItem myItem;

  public ResourceDrawablePanel(@NotNull ChooseResourceDialog dialog) {
    myDialog = dialog;
    setBorder(null);
    setViewportView(myPanel);
    myQualifierCombo.addActionListener(this);
    myResolvedPanel.setLayout(new VerticalFlowLayout());
    myResolvedScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    myResolvedScrollPane.setViewportBorder(null);
  }

  public void select(@NotNull ResourceChooserItem item) {
    myItem = item;
    List<String> qualifiers = item.getQualifiers();
    //noinspection unchecked
    myQualifierCombo.setModel(new DefaultComboBoxModel(ArrayUtil.toStringArray(qualifiers)));

    // Select the current item's qualifiers!
    //noinspection UnnecessaryLocalVariable
    String currentQualifier = DEFAULT_FOLDER_NAME;
    ResourceResolver resourceResolver = myDialog.getConfiguration().getResourceResolver();
    if (resourceResolver != null) {
      ResourceValue resourceValue = resourceResolver.resolveResValue(item.getResourceValue());
      if (resourceValue != null) {
        String value = resourceValue.getValue();
        if (value != null) {
          File rendered = new File(value);
          if (rendered.exists()) {
            File folder = rendered.getParentFile();
            if (folder != null) {
              String folderName = folder.getName();
              FolderConfiguration folderConfig = FolderConfiguration.getConfigForFolder(folderName);
              if (folderConfig != null) {
                int index = folderName.indexOf('-');
                if (index != -1) {
                  currentQualifier = folderName.substring(index + 1);
                }
              }
            }
          }
        }
      }
    }
    try {
      myIgnoreSelection = true;
      myQualifierCombo.setSelectedItem(currentQualifier);
    }
    finally {
      myIgnoreSelection = false;
    }

    show(item);
  }

  private void show(@NotNull ResourceChooserItem item) {
    updateName(item);
    updateIcon(item);
    updateTypeLabel(item);
    updateResolutionChain(item);
    validate();
  }

  private void updateName(@NotNull ResourceChooserItem item) {
    String name = item.getName();
    myNameLabel.setText(name);
  }

  private void updateIcon(@NotNull ResourceChooserItem item) {
    String path;
    String selectedItem = myQualifierCombo.getSelectedItem() != null
                          ? myQualifierCombo.getSelectedItem().toString() : DEFAULT_FOLDER_NAME;
    if (DEFAULT_FOLDER_NAME.equals(selectedItem)) {
      path = item.getPath();
    } else {
      path = item.getFileForQualifiers(selectedItem);
      if (path == null) {
        path = item.getPath();
      }
    }

    int height = myImageLabel.getHeight();
    if (height == 0) {
      height = 300;
    }
    Icon icon = myDialog.createIcon(height, JBUI.scale(8), false, path,
                                    item.getResourceValue(), item.getType());
    myImageLabel.setIcon(icon);
  }

  private void updateTypeLabel(@NotNull ResourceChooserItem item) {
    String type = "";
    if (item.getType() == ResourceType.MIPMAP) { // don't show file type of just one of the images
      type = ResourceType.MIPMAP.getDisplayName().toUpperCase(Locale.US); // uppercase for symmetry with other file types
    } else if (item.getType() != ResourceType.COLOR) {
      String fileName = item.getFile().getName();
      String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
      if (!extension.isEmpty()) {
        type = extension.toUpperCase(Locale.US);
      }
    }
    myTypeLabel.setText(type);
  }

  private void updateResolutionChain(@NotNull ResourceChooserItem item) {
    // Resource resolver
    myResolvedPanel.removeAll();
    ResourceValue resourceValue = item.getResourceValue();
    Configuration configuration = myDialog.getConfiguration();
    ResourceRepository frameworkResources = configuration.getFrameworkResources();
    if (frameworkResources != null) {
      AppResourceRepository appResources = AppResourceRepository.getAppResources(myDialog.geFacet(), true);
      ResourceItemResolver resolver = new ResourceItemResolver(configuration.getFullConfig(), frameworkResources, appResources, null);
      List<ResourceValue> lookupChain = Lists.newArrayList();
      lookupChain.add(resourceValue);
      resolver.setLookupChainList(lookupChain);
      resolver.resolveResValue(resourceValue);

      String prev = null;
      int indent = 0;
      if (lookupChain.size() >= 2) {
        for (ResourceValue element : lookupChain) {
          if (element == null) {
            continue;
          }
          String value = element.getValue();
          if (value == null) {
            continue;
          }
          String text = value;
          if (text.equals(prev)) {
            continue;
          }

          // Strip paths
          if (!(text.startsWith(PREFIX_THEME_REF) || text.startsWith(PREFIX_RESOURCE_REF))) {
            if (indent == 0) {
              break;
            }
            int end = Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'));
            if (end != -1) {
              text = text.substring(end + 1);
            }
          }

          if (indent > 0) {
            text = "\u21D2 " + text; // 21D2: Rightwards arrow
          }

          JBLabel label = new JBLabel(text);
          label.setBorder(IdeBorderFactory.createEmptyBorder(0, JBUI.scale(indent * 12), 0, 0));
          myResolvedPanel.add(label);
          indent++;

          prev = value;
        }
      }
    }
  }

  // ---- Implements ActionListener ----

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myQualifierCombo && !myIgnoreSelection) {
      show(myItem);
    }
  }
}
