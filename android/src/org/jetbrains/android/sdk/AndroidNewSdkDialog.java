package org.jetbrains.android.sdk;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class AndroidNewSdkDialog extends DialogWrapper {
  private JPanel myContentPanel;
  private JComboBox myBuildTargetComboBox;

  protected AndroidNewSdkDialog(@Nullable Project project,
                                @NotNull List<String> targetNames,
                                @NotNull String selectedTargetName) {
    super(project);
    setupUI();
    setTitle("Create New Android SDK");
    myBuildTargetComboBox.setModel(new CollectionComboBoxModel(targetNames, selectedTargetName));

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  public int getSelectedTargetIndex() {
    return myBuildTargetComboBox.getSelectedIndex();
  }

  private void setupUI() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JLabel label1 = new JLabel();
    label1.setText("Build target:");
    label1.setDisplayedMnemonic('B');
    label1.setDisplayedMnemonicIndex(0);
    myContentPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                   false));
    myBuildTargetComboBox = new JComboBox();
    myContentPanel.add(myBuildTargetComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                  null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myContentPanel.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    label1.setLabelFor(myBuildTargetComboBox);
  }
}
