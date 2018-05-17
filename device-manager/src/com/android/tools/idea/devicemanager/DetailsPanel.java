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
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.CurrentTheme.Label;
import com.intellij.util.ui.JBUI.CurrentTheme.Table;
import icons.StudioIcons;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DetailsPanel extends JBPanel<DetailsPanel> implements Disposable {
  static final int DEVICE_INFO_TAB_INDEX = 0;
  public static final int PAIRED_DEVICES_TAB_INDEX = 1;

  private final @NotNull Component myHeadingLabel;
  private final @NotNull AbstractButton myCloseButton;
  protected @Nullable Component mySummarySection;
  protected @Nullable Component myScreenDiagram;
  private @Nullable AbstractButton myCopyPropertiesToClipboardButton;
  protected final @NotNull List<Component> myInfoSections;
  protected final @NotNull Container myInfoSectionPanel;
  private final @NotNull Component myScrollPane;
  protected @Nullable PairedDevicesPanel myPairedDevicesPanel;
  private @Nullable JBTabbedPane myTabbedPane;

  private final @NotNull Supplier<Toolkit> myGetDefaultToolkit;

  protected DetailsPanel(@NotNull String heading) {
    this(heading, Toolkit::getDefaultToolkit);
  }

  @VisibleForTesting
  DetailsPanel(@NotNull String heading, @NotNull Supplier<Toolkit> getDefaultToolkit) {
    super(null);

    myHeadingLabel = newHeadingLabel(heading);
    myCloseButton = new IconButton(StudioIcons.Common.CLOSE);
    myInfoSections = new ArrayList<>();

    myInfoSectionPanel = new JBPanel<>(null);
    myInfoSectionPanel.setBackground(Table.BACKGROUND);

    myScrollPane = new JBScrollPane(myInfoSectionPanel);

    myGetDefaultToolkit = getDefaultToolkit;
  }

  @Override
  public void dispose() {
  }

  static @NotNull JLabel newHeadingLabel(@NotNull String heading) {
    JLabel label = new JBLabel(heading);
    label.setFont(label.getFont().deriveFont(Font.BOLD));

    return label;
  }

  protected final void init() {
    initSummarySection();
    initCopyPropertiesToClipboardButton();
    setInfoSectionPanelLayout();
    initTabbedPane();
    setLayout();
  }

  protected void initSummarySection() {
  }

  private void initCopyPropertiesToClipboardButton() {
    if (myInfoSections.isEmpty()) {
      return;
    }

    Color foreground = Label.disabledForeground();
    Icon icon = ColoredIconGenerator.INSTANCE.generateColoredIcon(AllIcons.Actions.Copy, foreground);

    myCopyPropertiesToClipboardButton = new JButton("Copy properties to clipboard", icon);

    myCopyPropertiesToClipboardButton.setBorder(null);
    myCopyPropertiesToClipboardButton.setContentAreaFilled(false);
    myCopyPropertiesToClipboardButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myCopyPropertiesToClipboardButton.setForeground(foreground);

    int gap = myCopyPropertiesToClipboardButton.getIconTextGap();
    Dimension size = BasicGraphicsUtils.getPreferredButtonSize(myCopyPropertiesToClipboardButton, gap);

    myCopyPropertiesToClipboardButton.setMaximumSize(size);
    myCopyPropertiesToClipboardButton.setMinimumSize(size);
    myCopyPropertiesToClipboardButton.setPreferredSize(size);

    myCopyPropertiesToClipboardButton.addActionListener(event -> copyPropertiesToClipboard());
  }

  private void copyPropertiesToClipboard() {
    StringBuilder builder = new StringBuilder(myInfoSections.get(0).toString());
    String separator = System.lineSeparator();

    myInfoSections.subList(1, myInfoSections.size()).forEach(section ->
                                                               builder
                                                                 .append(separator)
                                                                 .append(section));

    myGetDefaultToolkit.get().getSystemClipboard().setContents(new StringSelection(builder.toString()), null);
  }

  protected void setInfoSectionPanelLayout() {
    GroupLayout layout = new GroupLayout(myInfoSectionPanel);

    Group horizontalGroup = layout.createParallelGroup();
    SequentialGroup verticalGroup = layout.createSequentialGroup();

    if (myScreenDiagram == null) {
      horizontalGroup.addComponent(mySummarySection);
      verticalGroup.addComponent(mySummarySection);
    }
    else {
      horizontalGroup.addGroup(layout.createSequentialGroup()
                                 .addComponent(mySummarySection)
                                 .addPreferredGap(ComponentPlacement.UNRELATED)
                                 .addComponent(myScreenDiagram));

      verticalGroup.addGroup(layout.createParallelGroup()
                               .addComponent(mySummarySection)
                               .addComponent(myScreenDiagram));
    }

    if (!myInfoSections.isEmpty()) {
      horizontalGroup.addComponent(myCopyPropertiesToClipboardButton);

      verticalGroup
        .addPreferredGap(ComponentPlacement.UNRELATED)
        .addComponent(myCopyPropertiesToClipboardButton);
    }

    myInfoSections.forEach(section -> {
      horizontalGroup.addComponent(section);

      verticalGroup
        .addPreferredGap(ComponentPlacement.UNRELATED)
        .addComponent(section);
    });

    layout.setAutoCreateContainerGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    myInfoSectionPanel.setLayout(layout);
  }

  private void initTabbedPane() {
    if (myPairedDevicesPanel == null) {
      return;
    }

    myTabbedPane = new JBTabbedPane();
    myTabbedPane.setTabComponentInsets(JBUI.emptyInsets());

    myTabbedPane.insertTab("Device Info", null, myScrollPane, null, DEVICE_INFO_TAB_INDEX);
    myTabbedPane.insertTab("Paired Devices", null, myPairedDevicesPanel, null, PAIRED_DEVICES_TAB_INDEX);
  }

  private void setLayout() {
    Component component = myPairedDevicesPanel == null ? myScrollPane : myTabbedPane;
    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createParallelGroup()
      .addGroup(layout.createSequentialGroup()
                  .addContainerGap()
                  .addComponent(myHeadingLabel)
                  .addPreferredGap(ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .addComponent(myCloseButton)
                  .addContainerGap())
      .addComponent(component);

    Group verticalGroup = layout.createSequentialGroup()
      .addContainerGap()
      .addGroup(layout.createParallelGroup(Alignment.CENTER)
                  .addComponent(myHeadingLabel)
                  .addComponent(myCloseButton))
      .addPreferredGap(ComponentPlacement.RELATED)
      .addComponent(component);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  public final @NotNull AbstractButton getCloseButton() {
    return myCloseButton;
  }

  @VisibleForTesting
  final @NotNull AbstractButton getCopyPropertiesToClipboardButton() {
    assert myCopyPropertiesToClipboardButton != null;
    return myCopyPropertiesToClipboardButton;
  }

  @VisibleForTesting
  public final @NotNull Container getInfoSectionPanel() {
    return myInfoSectionPanel;
  }

  final @NotNull Optional<PairedDevicesPanel> getPairedDevicesPanel() {
    return Optional.ofNullable(myPairedDevicesPanel);
  }

  final @NotNull Optional<JTabbedPane> getTabbedPane() {
    return Optional.ofNullable(myTabbedPane);
  }
}
