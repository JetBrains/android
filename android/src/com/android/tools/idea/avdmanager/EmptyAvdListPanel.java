/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.tools.idea.npw.FormFactorUtils;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

/**
 * If no AVDs are present on the system, the {@link com.android.tools.idea.avdmanager.AvdListDialog} will display
 * this panel instead which contains instructional messages about AVDs and links to documentation as well as a button
 * to create a new AVD.
 */
public class EmptyAvdListPanel extends JPanel {
  private final AvdUiAction.AvdInfoProvider myProvider;
  private JButton myCreateAVirtualDeviceButton;
  private JPanel myRootPane;
  private JBLabel myDashboardText;
  private JBLabel myIcons;

  public EmptyAvdListPanel(AvdUiAction.AvdInfoProvider provider) {
    super(false);
    myProvider = provider;
    setLayout(new BorderLayout());
    add(myRootPane, BorderLayout.CENTER);
    myDashboardText.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        try {
          Desktop.getDesktop().browse(new URI("https://developer.android.com/about/dashboards/index.html"));
        } catch (Exception ex) {
          // Pass;
        }
      }
    });
    myDashboardText.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myIcons.setIcon(FormFactorUtils.getFormFactorsImage(myIcons, true));
  }

  private void createUIComponents() {
    myCreateAVirtualDeviceButton = new JButton(new CreateAvdAction(myProvider));
  }
}
