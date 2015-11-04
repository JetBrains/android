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
package com.android.tools.idea.devservices;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Tab of the Developer Services side panel which represents a list of the available APIs in this
 * particular bundling.
 */
public final class ServicesBundleTab extends JPanel {
  private JPanel myRootPanel;
  private JPanel myTopPanel;
  private JScrollPane myLibraryItemsScroll;
  private JPanel myLibraryItemsPanel;

  public ServicesBundleTab() {
    super(new BorderLayout());
    myTopPanel.add(new JBLabel("Top Panel (to be implemented)"));

    myLibraryItemsPanel = new JPanel();
    myLibraryItemsPanel.setOpaque(false);
    myLibraryItemsPanel.setLayout(new VerticalFlowLayout());

    myLibraryItemsScroll.add(myLibraryItemsPanel);
    myLibraryItemsScroll.setViewportView(myLibraryItemsPanel);
    myLibraryItemsScroll.getViewport().setOpaque(false);
    myLibraryItemsScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DeveloperServicesUtils.SEPARATOR_COLOR));

    add(myRootPanel);
  }

  public void addHelperPanel(@NotNull DeveloperServiceHelperPanel helperPanel) {
    boolean firstEntry = myLibraryItemsPanel.getComponentCount() == 0;
    myLibraryItemsPanel.add(helperPanel);
    if (firstEntry) {
      myLibraryItemsPanel.add(Box.createVerticalStrut(10));
    }
  }
}
