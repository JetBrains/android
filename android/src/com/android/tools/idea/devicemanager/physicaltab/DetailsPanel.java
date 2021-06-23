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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.Device;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import java.awt.Component;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import org.jetbrains.annotations.NotNull;

final class DetailsPanel extends JBPanel<DetailsPanel> {
  DetailsPanel(@NotNull Device device) {
    super(null);
    Component headingLabel = new JBLabel(device.toString());

    Component nameLabel = new JBLabel("Name");
    Component nameValueLabel = new JBLabel(device.toString());

    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createParallelGroup()
      .addComponent(headingLabel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      .addGroup(layout.createSequentialGroup()
                  .addComponent(nameLabel)
                  .addComponent(nameValueLabel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));

    Group verticalGroup = layout.createSequentialGroup()
      .addComponent(headingLabel)
      .addGroup(layout.createParallelGroup()
                  .addComponent(nameLabel)
                  .addComponent(nameValueLabel));

    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }
}
