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

import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.DeveloperServiceMetadata;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Component to wrap a given API's title, description, and icon, along with a JPanel container to
 * tighten UI presentation.
 */
public final class DeveloperServiceHelperPanel extends JComponent {
  private JPanel myRootPanel;
  private JButton myAddButton;
  private JLabel myIconLabel;
  private JTextPane myDescriptionPane;
  private JTextPane myTitlePane;
  @NotNull private DeveloperService myDeveloperService;

  public DeveloperServiceHelperPanel(@NotNull DeveloperService service) {
    setLayout(new BorderLayout());
    myDeveloperService = service;
    DeveloperServiceMetadata metadata = service.getMetadata();
    myTitlePane.setText(metadata.getName());
    myDescriptionPane.setText(metadata.getDescription());
    myIconLabel.setIcon(metadata.getIcon());
    add(myRootPanel);
  }
}
