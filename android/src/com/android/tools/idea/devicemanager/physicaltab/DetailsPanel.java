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
  private final @NotNull GroupLayout myLayout;

  private final @NotNull Group myHorizontalGroup;
  private final @NotNull Group myVerticalGroup;

  DetailsPanel(@NotNull Device device) {
    super(null);

    String name = device.getName();

    Component headingLabel = new JBLabel(name);

    InfoSection section = new InfoSection("Device")
      .putInfo("Name", name);

    Component subheadingLabel = new JBLabel(section.getHeading());

    myLayout = new GroupLayout(this);

    myHorizontalGroup = myLayout.createParallelGroup()
      .addComponent(headingLabel)
      .addComponent(subheadingLabel);

    myVerticalGroup = myLayout.createSequentialGroup()
      .addComponent(headingLabel)
      .addComponent(subheadingLabel);

    section.forEachInfo(this::addNameAndValueLabels);

    myLayout.setAutoCreateContainerGaps(true);
    myLayout.setAutoCreateGaps(true);
    myLayout.setHorizontalGroup(myHorizontalGroup);
    myLayout.setVerticalGroup(myVerticalGroup);

    setLayout(myLayout);
  }

  private void addNameAndValueLabels(@NotNull String name, @NotNull String value) {
    Component nameLabel = new JBLabel(name);
    Component valueLabel = new JBLabel(value);

    myHorizontalGroup.addGroup(myLayout.createSequentialGroup()
                                 .addComponent(nameLabel)
                                 .addComponent(valueLabel));

    myVerticalGroup.addGroup(myLayout.createParallelGroup()
                               .addComponent(nameLabel)
                               .addComponent(valueLabel));
  }
}
