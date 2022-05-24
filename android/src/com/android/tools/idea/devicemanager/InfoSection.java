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
package com.android.tools.idea.devicemanager;

import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.google.common.collect.Streams;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI.CurrentTheme.Label;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InfoSection extends JBPanel<InfoSection> {
  private final @NotNull Component myHeadingLabel;
  private final @NotNull Collection<@NotNull Component> myNameLabels;
  private final @NotNull Collection<@NotNull Component> myValueLabels;

  public InfoSection(@NotNull String heading) {
    super(null);

    myHeadingLabel = DetailsPanel.newHeadingLabel(heading);
    myNameLabels = new ArrayList<>();
    myValueLabels = new ArrayList<>();

    setOpaque(false);
  }

  public final @NotNull JLabel addNameAndValueLabels(@NotNull String name) {
    Component nameLabel = new JBLabel(name);
    nameLabel.setForeground(Label.disabledForeground());

    myNameLabels.add(nameLabel);

    JLabel valueLabel = new JBLabel();
    myValueLabels.add(valueLabel);

    return valueLabel;
  }

  public final void setLayout() {
    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createParallelGroup().addComponent(myHeadingLabel);
    Group verticalGroup = layout.createSequentialGroup().addComponent(myHeadingLabel);

    // noinspection UnstableApiUsage
    Streams.forEachPair(myNameLabels.stream(), myValueLabels.stream(), (nameLabel, valueLabel) -> {
      horizontalGroup.addGroup(layout.createSequentialGroup()
                                 .addComponent(nameLabel)
                                 .addComponent(valueLabel));

      verticalGroup.addGroup(layout.createParallelGroup()
                               .addComponent(nameLabel)
                               .addComponent(valueLabel));
    });

    layout.setAutoCreateGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  public static @NotNull Optional<@NotNull InfoSection> newPairedDeviceSection(@NotNull Device device,
                                                                               @NotNull WearPairingManager manager) {
    String key = device.getKey().toString();
    PhoneWearPair pair = manager.getPairedDevices(key);

    if (pair == null) {
      return Optional.empty();
    }

    InfoSection section = new InfoSection("Paired device");
    setText(section.addNameAndValueLabels("Paired with"), pair.getPeerDevice(key).getDisplayName());
    String paringStatus;
    switch (pair.getPairingStatus()) {
      case OFFLINE:
        paringStatus = "Offline";
        break;
      case CONNECTING:
        paringStatus = "Connecting";
        break;
      case CONNECTED:
        paringStatus = "Connected";
        break;
      case PAIRING_FAILED:
        paringStatus = "Error pairing";
        break;
      default:
        throw new AssertionError(pair.getPairingStatus());
    }
    setText(section.addNameAndValueLabels("Status"), paringStatus);
    section.setLayout();

    return Optional.of(section);
  }

  public static void setText(@NotNull JLabel label, @Nullable Object value) {
    if (value == null) {
      return;
    }

    label.setText(value.toString());
  }

  public static void setText(@NotNull JLabel label, @NotNull Iterable<@NotNull String> values) {
    label.setText(String.join(", ", values));
  }

  public final @NotNull Collection<@NotNull Component> getNameLabels() {
    return myNameLabels;
  }
}
