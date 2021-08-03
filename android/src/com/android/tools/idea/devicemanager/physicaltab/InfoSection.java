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

import com.google.common.collect.Streams;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class InfoSection extends JBPanel<InfoSection> {
  private final @NotNull Component myHeadingLabel;
  private final @NotNull Collection<@NotNull Component> myNameLabels;
  private final @NotNull Collection<@NotNull Component> myValueLabels;

  InfoSection(@NotNull String heading) {
    super(null);

    myHeadingLabel = new JBLabel(heading);
    myNameLabels = new ArrayList<>();
    myValueLabels = new ArrayList<>();
  }

  final @NotNull JLabel addNameAndValueLabels(@NotNull String name) {
    myNameLabels.add(new JBLabel(name));

    JLabel label = new JBLabel();
    myValueLabels.add(label);

    return label;
  }

  final void setLayout() {
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

  final @NotNull Collection<@NotNull Component> getNameLabels() {
    return myNameLabels;
  }

  static void setText(@NotNull JLabel label, @Nullable Object value) {
    if (value == null) {
      return;
    }

    label.setText(value.toString());
  }

  static void setText(@NotNull JLabel label, @NotNull Iterable<@NotNull String> values) {
    label.setText(String.join(", ", values));
  }
}
