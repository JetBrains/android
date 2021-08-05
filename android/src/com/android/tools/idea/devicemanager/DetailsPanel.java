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

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import icons.StudioIcons;
import java.awt.Component;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.LayoutStyle.ComponentPlacement;
import org.jetbrains.annotations.NotNull;

public final class DetailsPanel extends JBPanel<DetailsPanel> {
  private final @NotNull AbstractButton myCloseButton;

  public DetailsPanel(@NotNull String heading) {
    super(null);

    Component headingLabel = new JBLabel(heading);
    myCloseButton = Buttons.newIconButton(StudioIcons.Common.CLOSE);

    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createSequentialGroup()
      .addComponent(headingLabel)
      .addPreferredGap(ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      .addComponent(myCloseButton);

    Group verticalGroup = layout.createParallelGroup(Alignment.CENTER)
      .addComponent(headingLabel)
      .addComponent(myCloseButton);

    layout.setAutoCreateContainerGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  public @NotNull AbstractButton getCloseButton() {
    return myCloseButton;
  }
}
