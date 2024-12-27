/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.welcome.wizard.SdkComponentsTableModel;
import com.android.tools.idea.welcome.wizard.WelcomeUiUtils;
import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * UI for wizard page for selecting SDK components to download.
 */
@UiThread
public class SdkComponentsStepForm implements Disposable {
  private JPanel myContents;
  private JBTable myComponentsTable;
  private JTextPane myComponentDescription;
  private JLabel myNeededSpace;
  private JLabel myAvailableSpace;
  private JLabel myErrorMessage;
  private TextFieldWithBrowseButton myPath;
  private JBLoadingPanel myContentPanel;
  @SuppressWarnings("unused") private JPanel myBody;

  public SdkComponentsStepForm() {
    // Since we create and initialize a new AndroidSdkHandler/RepoManager for every (partial)
    // path that's entered, disallow direct editing of the path.
        try {
      setupUI();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    myPath.setEditable(false);

    if (!IdeInfo.getInstance().isGameTools()) {
      // Game tools does not allow changing Android SDK install directory from the UI.
      myPath.addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withTitle("Android SDK")
        .withDescription("Select Android SDK install directory"));
    }

    Font smallLabelFont = JBUI.Fonts.smallFont();
    myNeededSpace.setFont(smallLabelFont);
    myAvailableSpace.setFont(smallLabelFont);
    myErrorMessage.setText(null);
  }

  public TextFieldWithBrowseButton getPath() {
    return myPath;
  }

  private void createUIComponents() {
    Splitter splitter = new Splitter(false, 0.5f, 0.2f, 0.8f);
    myBody = splitter;
    myComponentsTable = new JBTable();
    myComponentDescription = new JTextPane();
    splitter.setShowDividerIcon(false);
    splitter.setShowDividerControls(false);
    myContentPanel = new JBLoadingPanel(new BorderLayout(), this);
    myContentPanel.add(myComponentsTable, BorderLayout.CENTER);

    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myContentPanel, false));
    splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myComponentDescription, false));

    myComponentDescription.setFont(StartupUiUtil.getLabelFont());
    myComponentDescription.setEditable(false);
    myComponentDescription.setBorder(BorderFactory.createEmptyBorder(WizardConstants.STUDIO_WIZARD_INSET_SIZE,
                                                                     WizardConstants.STUDIO_WIZARD_INSET_SIZE,
                                                                     WizardConstants.STUDIO_WIZARD_INSET_SIZE,
                                                                     WizardConstants.STUDIO_WIZARD_INSET_SIZE));
  }

  public void setTableModel(@NotNull SdkComponentsTableModel sdkComponentsTableModel) {
    myComponentsTable.setModel(sdkComponentsTableModel);
    myComponentsTable.setTableHeader(null);
    myComponentsTable.getSelectionModel().addListSelectionListener(e -> {
      int row = myComponentsTable.getSelectedRow();
      myComponentDescription.setText(row < 0 ? "" : sdkComponentsTableModel.getComponentDescription(row));
    });
  }

  public JBTable getComponentsTable() {
    return myComponentsTable;
  }

  public JPanel getContents() {
    return myContents;
  }

  public void setDiskSpace(String diskSpace) {
    myAvailableSpace.setText("Available disk space: " + diskSpace);
  }

  public void setDownloadSize(Long downloadSize) {
    myNeededSpace.setText(String.format("Total download size: %s", WelcomeUiUtils.getSizeLabel(downloadSize)));
  }

  public void startLoading() {
    myContentPanel.startLoading();
  }

  public void stopLoading() {
    myContentPanel.stopLoading();
  }

  public void setLoadingText(String text) {
    myContentPanel.setLoadingText(text);
  }

  public void setErrorIcon(Icon icon) {
    myErrorMessage.setIcon(icon);
  }

  public void setErrorMessage(@Nullable String message) {
    if (message == null) {
      // If completely empty, the height calculations are off.
      myErrorMessage.setText(" ");
    }
    else {
      myErrorMessage.setText(toHtml(message));
    }
  }

  public JLabel getErrorLabel() {
    return myErrorMessage;
  }

  private String toHtml(String text) {
    if (!StringUtil.isEmpty(text) && !text.startsWith("<html>")) {
      text = String.format("<html>%1$s</html>", text.trim());
    }
    return text;
  }

  public void setCellRenderer(@NotNull TableCellRenderer renderer) {
    getTableColumn().setCellRenderer(renderer);
  }

  public void setCellEditor(@NotNull TableCellEditor editor) {
    getTableColumn().setCellEditor(editor);
  }

  private TableColumn getTableColumn() {
    return myComponentsTable.getColumnModel().getColumn(0);
  }

  @Override
  public void dispose() { }

  private void setupUI() {
    createUIComponents();
    myContents = new JPanel();
    myContents.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JLabel label1 = new JLabel();
    label1.setText("Check the components you want to update/install. Click Next to continue.");
    myContents.add(label1,
                   new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myContents.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 16), null, 0, false));
    myContents.add(myBody, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                               null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Android SDK Location:");
    myContents.add(label2,
                   new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myPath = new TextFieldWithBrowseButton();
    myContents.add(myPath, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                               false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myContents.add(panel1, new GridConstraints(3, 1, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_FIXED,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                               null, 0, false));
    myNeededSpace = new JLabel();
    myNeededSpace.setText("Total download size: 5.1Gb");
    panel1.add(myNeededSpace,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myAvailableSpace = new JLabel();
    myAvailableSpace.setText("Available disk space: 23.0Gb");
    panel1.add(myAvailableSpace,
               new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    panel1.add(spacer2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myErrorMessage = new JLabel();
    myErrorMessage.setText("Label");
    myContents.add(myErrorMessage, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    label2.setLabelFor(myPath);
  }

  public JComponent getRootComponent() { return myContents; }
}