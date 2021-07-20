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

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DetailsPanel extends JBPanel<DetailsPanel> {
  private final @NotNull PhysicalDevice myDevice;
  private final @NotNull Collection<@NotNull Component> myNameLabels;

  private final @NotNull GroupLayout myLayout;
  private final @NotNull Group myHorizontalGroup;
  private final @NotNull SequentialGroup myVerticalGroup;

  DetailsPanel(@NotNull PhysicalDevice device) {
    super(null);

    myDevice = device;
    myNameLabels = new ArrayList<>();

    Component headingLabel = new JBLabel(device.getName());
    myLayout = new GroupLayout(this);

    myHorizontalGroup = myLayout.createParallelGroup()
      .addComponent(headingLabel);

    myVerticalGroup = myLayout.createSequentialGroup()
      .addComponent(headingLabel)
      .addPreferredGap(ComponentPlacement.UNRELATED);

    addSections();
    myLayout.linkSize(SwingConstants.HORIZONTAL, myNameLabels.toArray(new Component[0]));

    myLayout.setAutoCreateContainerGaps(true);
    myLayout.setAutoCreateGaps(true);
    myLayout.setHorizontalGroup(myHorizontalGroup);
    myLayout.setVerticalGroup(myVerticalGroup);

    setLayout(myLayout);
  }

  private void addSections() {
    Iterator<InfoSection> sections = Arrays.asList(newQuickSummarySection(), newDeviceSection()).iterator();
    addSection(sections.next());

    while (sections.hasNext()) {
      myVerticalGroup.addPreferredGap(ComponentPlacement.UNRELATED);
      addSection(sections.next());
    }
  }

  private @NotNull InfoSection newQuickSummarySection() {
    return new InfoSection("Quick summary")
      .putInfo("API level", myDevice.getApi())
      .putInfo("Resolution", myDevice.getResolution());
  }

  private @NotNull InfoSection newDeviceSection() {
    return new InfoSection("Device")
      .putInfo("Name", myDevice.getName());
  }

  private void addSection(@NotNull InfoSection section) {
    Component headingLabel = new JBLabel(section.getHeading());

    myHorizontalGroup.addComponent(headingLabel);
    myVerticalGroup.addComponent(headingLabel);

    section.forEachInfo(this::addNameAndValueLabels);
  }

  private void addNameAndValueLabels(@NotNull String name, @Nullable Object value) {
    Component nameLabel = new JBLabel(name);
    myNameLabels.add(nameLabel);

    if (value == null) {
      myHorizontalGroup.addComponent(nameLabel);
      myVerticalGroup.addComponent(nameLabel);

      return;
    }

    Component valueLabel = new JBLabel(value.toString());

    myHorizontalGroup.addGroup(myLayout.createSequentialGroup()
                                 .addComponent(nameLabel)
                                 .addComponent(valueLabel));

    myVerticalGroup.addGroup(myLayout.createParallelGroup()
                               .addComponent(nameLabel)
                               .addComponent(valueLabel));
  }
}
