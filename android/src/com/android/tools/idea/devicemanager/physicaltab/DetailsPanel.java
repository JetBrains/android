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
import java.awt.Dimension;
import java.util.Collection;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JLabel;
import javax.swing.LayoutStyle.ComponentPlacement;
import org.jetbrains.annotations.NotNull;

final class DetailsPanel extends JBPanel<DetailsPanel> {
  private final @NotNull Component myHeadingLabel;
  private final @NotNull InfoSection mySummarySection;
  private final @NotNull InfoSection myDeviceSection;

  private static final class SummarySection extends InfoSection {
    private SummarySection(@NotNull PhysicalDevice device) {
      super("Summary");

      JLabel apiLevelLabel = addNameAndValueLabels("API level");
      setText(apiLevelLabel, device.getApi());

      JLabel resolutionLabel = addNameAndValueLabels("Resolution");
      setText(resolutionLabel, device.getResolution());

      JLabel dpLabel = addNameAndValueLabels("dp");
      setText(dpLabel, device.getDp());

      JLabel abiListLabel = addNameAndValueLabels("ABI list");
      setText(abiListLabel, device.getAbis());

      setLayout();
    }
  }

  private static final class DeviceSection extends InfoSection {
    private DeviceSection(@NotNull PhysicalDevice device) {
      super("Device");

      JLabel nameLabel = addNameAndValueLabels("Name");
      setText(nameLabel, device.getName());

      setLayout();
    }
  }

  DetailsPanel(@NotNull PhysicalDevice device) {
    super(null);

    myHeadingLabel = new JBLabel(device.getName());
    mySummarySection = new SummarySection(device);
    myDeviceSection = new DeviceSection(device);

    setNameLabelPreferredWidthsToMax();
    setLayout();
  }

  private void setNameLabelPreferredWidthsToMax() {
    Collection<Component> labels = Stream.of(mySummarySection, myDeviceSection)
      .map(InfoSection::getNameLabels)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    OptionalInt optionalWidth = labels.stream()
      .map(Component::getPreferredSize)
      .mapToInt(size -> size.width)
      .max();

    int width = optionalWidth.orElseThrow(AssertionError::new);

    labels.forEach(component -> {
      Dimension size = component.getPreferredSize();
      size.width = width;

      component.setPreferredSize(size);
      component.setMaximumSize(size);
    });
  }

  private void setLayout() {
    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createParallelGroup()
      .addComponent(myHeadingLabel)
      .addComponent(mySummarySection)
      .addComponent(myDeviceSection);

    Group verticalGroup = layout.createSequentialGroup()
      .addComponent(myHeadingLabel)
      .addPreferredGap(ComponentPlacement.UNRELATED)
      .addComponent(mySummarySection)
      .addPreferredGap(ComponentPlacement.UNRELATED)
      .addComponent(myDeviceSection);

    layout.setAutoCreateContainerGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }
}
