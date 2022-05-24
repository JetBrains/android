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

import com.android.tools.adtui.common.ColoredIconGenerator;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PairingState;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UIUtil.FontSize;
import icons.StudioIcons;
import java.awt.Color;
import java.awt.Component;
import java.util.Optional;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceTableCellRenderer<D extends Device> implements TableCellRenderer {
  private final @NotNull Class<@NotNull D> myValueClass;
  private final @NotNull WearPairingManager myManager;

  private final @NotNull JLabel myIconLabel;
  private final @NotNull JLabel myNameLabel;
  private final @NotNull JLabel myOnlineLabel;
  private final @NotNull JLabel myLine2Label;
  private final @NotNull JLabel myPairedLabel;
  private final @NotNull JComponent myPanel;

  protected DeviceTableCellRenderer(@NotNull Class<@NotNull D> valueClass) {
    this(valueClass, WearPairingManager.INSTANCE);
  }

  @VisibleForTesting
  DeviceTableCellRenderer(@NotNull Class<@NotNull D> valueClass, @NotNull WearPairingManager manager) {
    myValueClass = valueClass;
    myManager = manager;

    myIconLabel = new JBLabel();
    myNameLabel = new JBLabel();
    myOnlineLabel = new JBLabel();
    myLine2Label = new JBLabel();
    myPairedLabel = new JBLabel();

    myPanel = new JBPanel<>(null);
    GroupLayout layout = new GroupLayout(myPanel);

    Group horizontalGroup = layout.createSequentialGroup()
      .addComponent(myIconLabel)
      .addPreferredGap(ComponentPlacement.RELATED)
      .addGroup(layout.createParallelGroup()
                  .addGroup(layout.createSequentialGroup()
                              .addComponent(myNameLabel, 0, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addComponent(myOnlineLabel))
                  .addComponent(myLine2Label, 0, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
      .addPreferredGap(ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      .addComponent(myPairedLabel)
      .addGap(JBUIScale.scale(4));

    Group verticalGroup = layout.createParallelGroup(Alignment.CENTER)
      .addGroup(layout.createSequentialGroup()
                  .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .addGroup(layout.createParallelGroup(Alignment.CENTER)
                              .addComponent(myIconLabel)
                              .addComponent(myNameLabel)
                              .addComponent(myOnlineLabel))
                  .addComponent(myLine2Label)
                  .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      .addComponent(myPairedLabel);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    myPanel.setLayout(layout);
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    D device = myValueClass.cast(value);
    Color foreground = Tables.getForeground(table, selected);

    myIconLabel.setForeground(foreground);
    setIcon(myIconLabel, device.getIcon(), selected);

    myNameLabel.setForeground(foreground);
    myNameLabel.setText(getName(device));

    myOnlineLabel.setForeground(foreground);
    setIcon(myOnlineLabel, device.isOnline() ? StudioIcons.Avd.STATUS_DECORATOR_ONLINE : null, selected);

    myLine2Label.setFont(UIUtil.getLabelFont(FontSize.SMALL));
    myLine2Label.setForeground(brighten(foreground));
    myLine2Label.setText(getLine2(device));

    myPairedLabel.setForeground(foreground);

    if (StudioFlags.WEAR_OS_VIRTUAL_DEVICE_PAIRING_ASSISTANT_ENABLED.get()) {
      Optional<Icon> icon = getPairedLabelIcon(device);

      setIcon(myPairedLabel, icon.orElse(null), selected);
      myPairedLabel.setVisible(icon.isPresent());
    }

    myPanel.setBackground(Tables.getBackground(table, selected));
    myPanel.setBorder(Tables.getBorder(selected, focused));

    return myPanel;
  }

  private static void setIcon(@NotNull JLabel label, @Nullable Icon icon, boolean selected) {
    if (icon == null) {
      label.setIcon(null);
    }
    else if (selected) {
      label.setIcon(ColoredIconGenerator.INSTANCE.generateColoredIcon(icon, label.getForeground()));
    }
    else {
      label.setIcon(icon);
    }
  }

  protected @NotNull String getName(@NotNull D device) {
    return device.getName();
  }

  protected @NotNull String getLine2(@NotNull D device) {
    return device.getTarget();
  }

  private static @NotNull Color brighten(@NotNull Color color) {
    int red = Math.min(color.getRed() + 50, 255);
    int green = Math.min(color.getGreen() + 50, 255);
    int blue = Math.min(color.getBlue() + 50, 255);

    return new JBColor(new Color(red, green, blue), color.darker());
  }

  private @NotNull Optional<@NotNull Icon> getPairedLabelIcon(@NotNull Device device) {
    PhoneWearPair pair = myManager.getPairedDevices(device.getKey().toString());

    if (pair == null) {
      return Optional.empty();
    }

    if (pair.getPairingStatus().equals(PairingState.CONNECTED)) {
      return Optional.of(StudioIcons.DeviceExplorer.DEVICE_PAIRED_AND_CONNECTED);
    }

    return Optional.of(StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN);
  }

  @VisibleForTesting
  final @NotNull JLabel getIconLabel() {
    return myIconLabel;
  }

  @VisibleForTesting
  public final @NotNull JLabel getNameLabel() {
    return myNameLabel;
  }

  @VisibleForTesting
  final @NotNull JLabel getOnlineLabel() {
    return myOnlineLabel;
  }

  @VisibleForTesting
  final @NotNull JLabel getLine2Label() {
    return myLine2Label;
  }

  @VisibleForTesting
  final @NotNull JLabel getPairedLabel() {
    return myPairedLabel;
  }
}
