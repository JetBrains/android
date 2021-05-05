/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanel;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JComponent;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.jetbrains.annotations.NotNull;

final class VirtualDevicePanel extends JBPanel<VirtualDevicePanel> {
  private final @NotNull VirtualDisplayList myAvdDisplayList;
  private final @NotNull PreconfiguredDisplayList myPreconfiguredDisplayList;
  private final @NotNull DocumentListener mySearchDocumentListener;

  VirtualDevicePanel(@NotNull Project project) {
    myAvdDisplayList = new VirtualDisplayList(project);
    myPreconfiguredDisplayList = new PreconfiguredDisplayList(project, myAvdDisplayList);
    mySearchDocumentListener = new SearchDocumentListener(myAvdDisplayList);

    setLayout();
  }

  private void setLayout() {
    JComponent toolbar = new VirtualToolbar(myAvdDisplayList, myAvdDisplayList, mySearchDocumentListener).getPanel();

    GroupLayout groupLayout = new GroupLayout(this);
    Group horizontalGroup = groupLayout.createParallelGroup(Alignment.LEADING);
    horizontalGroup.addComponent(toolbar);
    horizontalGroup.addComponent(myAvdDisplayList);
    if (showPreconfiguredList()) {
      horizontalGroup.addComponent(myPreconfiguredDisplayList);
    }

    SequentialGroup verticalGroup = groupLayout.createSequentialGroup();
    verticalGroup.addContainerGap();
    verticalGroup.addComponent(toolbar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);
    verticalGroup.addComponent(myAvdDisplayList);
    if (showPreconfiguredList()) {
      verticalGroup.addPreferredGap(ComponentPlacement.UNRELATED);
      verticalGroup.addComponent(myPreconfiguredDisplayList);
    }

    groupLayout.setHorizontalGroup(horizontalGroup);
    groupLayout.setVerticalGroup(verticalGroup);
    setLayout(groupLayout);
  }

  private static boolean showPreconfiguredList() {
    return StudioFlags.ENABLE_DEVICE_MANAGER_HALF_BAKED_FEATURES.get();
  }

  private static final class SearchDocumentListener implements DocumentListener {
    private final @NotNull VirtualDisplayList myAvdDisplayList;

    private SearchDocumentListener(@NotNull VirtualDisplayList avdDisplayList) {
      myAvdDisplayList = avdDisplayList;
    }

    @Override
    public void insertUpdate(@NotNull DocumentEvent event) {
      updateSearchResults(event);
    }

    @Override
    public void removeUpdate(@NotNull DocumentEvent event) {
      updateSearchResults(event);
    }

    @Override
    public void changedUpdate(@NotNull DocumentEvent event) {
      updateSearchResults(event);
    }

    private void updateSearchResults(@NotNull DocumentEvent event) {
      String text;
      try {
        Document document = event.getDocument();
        text = document.getText(0, document.getLength());
      }
      catch (@NotNull BadLocationException exception) {
        text = "";
      }
      myAvdDisplayList.updateSearchResults(text);
    }
  }
}
