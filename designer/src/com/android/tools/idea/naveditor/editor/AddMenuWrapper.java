/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.editor;

import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.FlatComboAction;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.ui.ASGallery;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.IconUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.dvlib.DeviceSchema.ATTR_NAME;

/**
 * "Add" popup menu in the navigation editor.
 */
class AddMenuWrapper extends FlatComboAction {

  private final NavDesignSurface mySurface;
  private final List<NavActionManager.Destination> myDestinations;

  AddMenuWrapper(@NotNull NavDesignSurface surface, @NotNull List<NavActionManager.Destination> destinations) {
    mySurface = surface;
    myDestinations = destinations;
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(IconUtil.getAddIcon());
    presentation.setDescription("Add Destination");
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup() {
    return new DefaultActionGroup();
  }

  @NotNull
  @Override
  protected JBPopup createPopup(@Nullable Runnable onDispose, @Nullable DataContext context) {
    CollectionListModel<NavActionManager.Destination> listModel = new CollectionListModel<>(myDestinations);
    ASGallery<NavActionManager.Destination> destinations = new ASGallery<NavActionManager.Destination>(
      listModel, NavActionManager.Destination::getThumbnail, NavActionManager.Destination::getName, new Dimension(96, 96), null) {
      @Override
      @NotNull
      public Dimension getPreferredScrollableViewportSize() {
        Dimension cellSize = computeCellSize();
        int heightInsets = getInsets().top + getInsets().bottom;
        int widthInsets = getInsets().left + getInsets().right;
        // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
        return new Dimension(cellSize.width * 3 + widthInsets, (int)(cellSize.height * 2.2) + heightInsets);
      }

      @Override
      public int locationToIndex(@NotNull Point location) {
        int index = super.locationToIndex(location);
        if (index != -1 && !getCellBounds(index, index).contains(location)) {
          return -1;
        }
        else {
          return index;
        }
      }
    };
    destinations.setBackground(null);
    destinations.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(@NotNull MouseEvent event) {
        int index = destinations.locationToIndex(event.getPoint());
        if (index != -1) {
          destinations.setSelectedIndex(index);
          destinations.requestFocusInWindow();
        }
        else {
          destinations.clearSelection();
        }
      }
    });

    JPanel panel = new JPanel(new VerticalLayout(5));
    // TODO: hook up search
    panel.add(new SearchTextField());

    JBScrollPane scrollPane = new JBScrollPane(destinations);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    panel.add(scrollPane);

    // TODO: implement "details" screen
    JButton createButton = new JButton("New Destination");
    JPanel createButtonPanel = new JPanel();
    createButtonPanel.add(createButton);
    panel.add(createButtonPanel);

    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel)
      .setRequestFocus(true)
      .setCancelOnClickOutside(true)
      .setLocateWithinScreenBounds(true)
      .setShowShadow(true)
      .setCancelOnWindowDeactivation(true)
      .setCancelCallback(() -> {
        if (onDispose != null) {
          onDispose.run();
        }
        return true;
      })
      .createPopup();

    createButton.addActionListener(event -> {
      addElement(null, mySurface);
      popup.closeOk(null);
    });
    destinations.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(@NotNull MouseEvent event) {
        if (destinations.getSelectedIndex() != -1) {
          addElement(destinations.getSelectedElement(), mySurface);
          popup.closeOk(null);
        }
      }
    });
    return popup;
  }

  static void addElement(@Nullable NavActionManager.Destination destination, @NotNull NavDesignSurface surface) {
    new WriteCommandAction(surface.getProject(), "Create Fragment", surface.getModel().getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        String tagName = surface.getSchema().getTag(NavigationSchema.DestinationType.FRAGMENT);
        if (destination != null) {
          tagName = destination.getTag();
        }
        NlComponent parent = surface.getCurrentNavigation();
        XmlTag tag = parent.getTag().createChildTag(tagName, null, null, true);
        String idBase = tagName;
        if (destination != null) {
          idBase = destination.getQualifiedName();
        }
        NlComponent newComponent = surface.getModel().createComponent(tag, parent, null);
        newComponent.assignId(idBase);
        if (destination != null) {
          newComponent.setAttribute(ANDROID_URI, ATTR_NAME, destination.getQualifiedName());
          XmlFile layout = destination.getLayoutFile();
          if (layout != null) {
            // TODO: do this the right way
            String layoutId = "@" + ResourceType.LAYOUT.getName() + "/" + FileUtil.getNameWithoutExtension(layout.getName());
            newComponent.setAttribute(TOOLS_URI, ATTR_LAYOUT, layoutId);
          }
        }
      }
    }.execute();
  }
}
