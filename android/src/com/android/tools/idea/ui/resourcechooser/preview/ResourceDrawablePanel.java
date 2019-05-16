/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser.preview;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItemResolver;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.ui.resourcechooser.ResourceChooserItem;
import com.android.tools.idea.ui.resourcechooser.icons.IconFactory;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.tools.idea.ui.resourcechooser.ResourceChooserItem.DEFAULT_FOLDER_NAME;

/**
 * Panel used to edit bitmaps, XML drawables and solid colors
 */
public class ResourceDrawablePanel extends JBScrollPane implements ActionListener {
  @NotNull private final Configuration myConfiguration;
  @NotNull private final Module myModule;
  @NotNull private final IconFactory myIconFactory;
  @NotNull private final AndroidFacet myFacet;
  private JBLabel myNameLabel;
  private JBLabel myImageLabel;
  private JComboBox myQualifierCombo;
  private JBLabel myTypeLabel;
  private JPanel myPanel;
  private JPanel myResolvedPanel;
  private JBScrollPane myResolvedScrollPane;
  private boolean myIgnoreSelection;
  private ResourceChooserItem myItem;

  public ResourceDrawablePanel(@NotNull Configuration configuration, @NotNull AndroidFacet facet, @NotNull IconFactory iconFactory) {
    myConfiguration = configuration;
    myFacet = facet;
    myModule = facet.getModule();
    myIconFactory = iconFactory;
    setBorder(null);
    setViewportView(myPanel);
    myQualifierCombo.addActionListener(this);
    myResolvedPanel.setLayout(new VerticalFlowLayout());
    myResolvedScrollPane.setBorder(JBUI.Borders.empty());
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
    ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
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

  @NotNull
  private String getSelectedQualifier() {
    return myQualifierCombo.getSelectedItem() != null
           ? myQualifierCombo.getSelectedItem().toString() : DEFAULT_FOLDER_NAME;
  }

  private void show(@NotNull ResourceChooserItem item) {
    myNameLabel.setText(item.getName());
    myImageLabel.setIcon(newItemIcon(item, myImageLabel.getHeight(), getSelectedQualifier(), myImageLabel::repaint));
    myTypeLabel.setText(getItemTypeLabel(item));
    updateResolutionChain(item, myConfiguration, myFacet, myResolvedPanel);
    validate();
  }

  @NotNull
  private Icon newItemIcon(@NotNull ResourceChooserItem item,
                                int height,
                                @NotNull String folderName,
                                @NotNull Runnable iconCallback) {
    String path = null;
    if (!DEFAULT_FOLDER_NAME.equals(folderName)) {
      path = item.getFileForQualifiers(folderName);
    }

    if (path == null) {
      path = item.getPath();
    }

    if (height == 0) {
      height = 300;
    }

    Icon icon = null;
    if (path != null) {
      icon = myIconFactory.createIconFromPath(height, JBUI.scale(8), false, path);
    }
    if (icon == null) {
      icon = myIconFactory.createAsyncIconFromResourceValue(height, JBUI.scale(8), false,
                                                            item.getResourceValue(),
                                                            EmptyIcon.create(height), iconCallback);
    }
    return icon;
  }


  @NotNull
  private static String getItemTypeLabel(@NotNull ResourceChooserItem item) {
    if (item.getType() == ResourceType.MIPMAP) { // don't show file type of just one of the images
      return StringUtil.toUpperCase(ResourceType.MIPMAP.getDisplayName()); // uppercase for symmetry with other file types
    }

    PathString file = item.getFile();
    if (file != null && item.getType() != ResourceType.COLOR) {
      String extension = Files.getFileExtension(file.getFileName());
      if (!extension.isEmpty()) {
        return StringUtil.toUpperCase(extension);
      }
    }

    return "";
  }

  /**
   * Returns the resolution chain of the given {@link ResourceValue}
   */
  @NotNull
  private static Stream<String> getResolutionChain(@NotNull Configuration configuration,
                                                   @NotNull AndroidFacet facet,
                                                   @NotNull ResourceValue value) {
    ResourceRepository frameworkResources = configuration.getFrameworkResources();
    if (frameworkResources == null) {
      return Stream.empty();
    }

    LocalResourceRepository appResources = ResourceRepositoryManager.getAppResources(facet);
    ResourceItemResolver resolver = new ResourceItemResolver(configuration.getFullConfig(), frameworkResources, appResources, null);
    List<ResourceValue> lookupChain = Lists.newArrayList();
    resolver.setLookupChainList(lookupChain);
    resolver.resolveResValue(value);

    return lookupChain.stream()
      .skip(2)
      .filter(Objects::nonNull)
      .map(e -> e.getValue())
      .filter(Objects::nonNull)
      .distinct()
      .map(text -> {
        if (!(text.startsWith(PREFIX_THEME_REF) || text.startsWith(PREFIX_RESOURCE_REF))) {
          int end = Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'));
          if (end != -1) {
            return text.substring(end + 1);
          }
        }

        return text;
      });
  }

  /**
   * Fills the given panel with the resolution chain of the given {@link ResourceChooserItem}. This method will add a new
   * {@link JBLabel} per element in the chain.
   */
  private static void updateResolutionChain(@NotNull ResourceChooserItem item,
                                            @NotNull Configuration configuration,
                                            @NotNull AndroidFacet facet,
                                            @NotNull JPanel resolvedPanel) {
    // Resource resolver
    resolvedPanel.removeAll();
    ResourceValue value = item.getResourceValue();
    if (value.getValue() == null) {
      return;
    }

    JBLabel rootLabel = new JBLabel(value.getValue());
    resolvedPanel.add(rootLabel);

    AtomicInteger indentCount = new AtomicInteger(0);
    getResolutionChain(configuration, facet, value)
      .map(text -> "\u21D2 " + text)
      .map(text -> new JBLabel(text))
      .forEach(label -> {
        label.setBorder(JBUI.Borders.emptyLeft(JBUI.scale(indentCount.incrementAndGet() * 12)));
        resolvedPanel.add(label);
      });
  }

  // ---- Implements ActionListener ----

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myQualifierCombo && !myIgnoreSelection) {
      show(myItem);
    }
  }
}
