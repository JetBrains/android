/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.monitor;

import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.logcat.AndroidLogConsole;
import com.android.tools.idea.logcat.AndroidLogcatView;
import com.android.tools.idea.logcat.RegexFilterComponent;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.LayoutStyle.ComponentPlacement;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

final class DeviceAndSearchPanel extends JPanel {
  private final Component myDeviceComboBox;
  private final Component myClientComboBox;
  private final Component myLogFilterComboBox;
  private final Component mySearchTextField;
  private final Component myRegexCheckBox;
  private final Component myEditFiltersComboBox;

  DeviceAndSearchPanel(@NotNull DevicePanel devicePanel, @NotNull AndroidLogcatView logcatView) {
    AndroidLogConsole console = (AndroidLogConsole)logcatView.getLogConsole();
    RegexFilterComponent component = (RegexFilterComponent)console.getTextFilterComponent();

    myDeviceComboBox = devicePanel.getDeviceComboBox();
    myClientComboBox = devicePanel.getClientComboBox();
    myLogFilterComboBox = console.getLogFilterComboBox();
    mySearchTextField = component.getSearchTextField();
    myRegexCheckBox = component.getRegexCheckBox();
    myEditFiltersComboBox = logcatView.createEditFiltersComboBox();

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(@NotNull ComponentEvent event) {
        setLayout(getWidth() > JBUI.scale(500) ? createWideLayout() : createNarrowLayout());
      }
    });
  }

  @NotNull
  private LayoutManager createWideLayout() {
    GroupLayout layout = new GroupLayout(this);

    int minimumWidth = new ComboBox().getMinimumSize().width;
    int preferredWidth = myEditFiltersComboBox.getPreferredSize().width;

    Group horizontalGroup = layout.createSequentialGroup()
      .addComponent(myDeviceComboBox, minimumWidth, preferredWidth, preferredWidth)
      .addComponent(myClientComboBox, minimumWidth, preferredWidth, preferredWidth)
      .addPreferredGap(ComponentPlacement.UNRELATED)
      .addComponent(myLogFilterComboBox, minimumWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
      .addComponent(mySearchTextField, GroupLayout.DEFAULT_SIZE, preferredWidth, Short.MAX_VALUE)
      .addComponent(myRegexCheckBox)
      .addComponent(myEditFiltersComboBox, minimumWidth, preferredWidth, preferredWidth);

    Group verticalGroup = layout.createParallelGroup(Alignment.CENTER)
      .addComponent(myDeviceComboBox)
      .addComponent(myClientComboBox)
      .addComponent(myLogFilterComboBox)
      .addComponent(mySearchTextField)
      .addComponent(myRegexCheckBox)
      .addComponent(myEditFiltersComboBox);

    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    return layout;
  }

  @NotNull
  private LayoutManager createNarrowLayout() {
    GroupLayout layout = new GroupLayout(this);

    int minimumWidth = new ComboBox().getMinimumSize().width;
    int preferredWidth = myEditFiltersComboBox.getPreferredSize().width;

    Group horizontalGroup = layout.createParallelGroup()
      .addGroup(layout.createSequentialGroup()
                  .addComponent(myDeviceComboBox)
                  .addComponent(myClientComboBox))
      .addGroup(layout.createSequentialGroup()
                  .addComponent(myLogFilterComboBox, minimumWidth, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                  .addComponent(mySearchTextField)
                  .addComponent(myRegexCheckBox)
                  .addComponent(myEditFiltersComboBox, minimumWidth, preferredWidth, preferredWidth));

    Group verticalGroup = layout.createSequentialGroup()
      .addGroup(layout.createParallelGroup()
                  .addComponent(myDeviceComboBox)
                  .addComponent(myClientComboBox))
      .addGroup(layout.createParallelGroup(Alignment.CENTER)
                  .addComponent(myLogFilterComboBox)
                  .addComponent(mySearchTextField)
                  .addComponent(myRegexCheckBox)
                  .addComponent(myEditFiltersComboBox));

    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    return layout;
  }
}
