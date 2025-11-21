/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.refactoring.rtl;

import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nullable;

public class RtlSupportDialog extends DialogWrapper {
  private JPanel myPanel;
  private JCheckBox myAndroidManifestCheckBox;
  private JCheckBox myLayoutsCheckBox;
  private JCheckBox myReplaceLeftRightPropertiesCheckBox;
  private JCheckBox myGenerateV17VersionsCheckBox;

  private final RtlSupportProperties myProperties;

  public RtlSupportDialog(Project project) {
    super(project, true);
    setupUI();

    myProperties = new RtlSupportProperties();

    setTitle(AndroidBundle.message("android.refactoring.rtl.addsupport.title"));
    setOKButtonText(AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.ok.button.text"));

    myLayoutsCheckBox.addItemListener(itemEvent -> {
      final boolean isSelected = myLayoutsCheckBox.isSelected();
      myReplaceLeftRightPropertiesCheckBox.setEnabled(isSelected);
      myGenerateV17VersionsCheckBox.setEnabled(isSelected);
    });

    setDefaultValues();

    init();
  }

  @Override
  protected String getHelpId() {
    return AndroidWebHelpProvider.HELP_PREFIX + "r/studio-ui/rtl-refactor-help";
  }

  private void setDefaultValues() {
    myAndroidManifestCheckBox.setSelected(true);
    myLayoutsCheckBox.setSelected(true);
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAndroidManifestCheckBox;
  }

  public final RtlSupportProperties getProperties() {
    myProperties.updateAndroidManifest = myAndroidManifestCheckBox.isSelected();
    myProperties.updateLayouts = myLayoutsCheckBox.isSelected();
    myProperties.replaceLeftRightPropertiesOption = myReplaceLeftRightPropertiesCheckBox.isSelected();
    myProperties.generateV17resourcesOption = myGenerateV17VersionsCheckBox.isSelected();

    // When generating v17 layout file, we force replacing left/right properties by start/end properties
    if (myProperties.generateV17resourcesOption) {
      myProperties.replaceLeftRightPropertiesOption = true;
    }

    return myProperties;
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new BorderLayout(0, 0));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.add(panel1, BorderLayout.NORTH);
    final JTextArea textArea1 = new JTextArea();
    textArea1.setBackground(UIManager.getColor("Button.background"));
    textArea1.setText(AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.label.text"));
    panel1.add(textArea1,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.add(panel2, BorderLayout.CENTER);
    myAndroidManifestCheckBox = new JCheckBox();
    loadButtonText(myAndroidManifestCheckBox, AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.option.label.update.manifest.text"));
    panel2.add(myAndroidManifestCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myLayoutsCheckBox = new JCheckBox();
    loadButtonText(myLayoutsCheckBox, AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.option.label.update.layouts.text"));
    panel2.add(myLayoutsCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    panel2.add(panel3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    panel3.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(null,
                                                                              AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.option.label.layouts.options.txt"),
                                                                              TitledBorder.DEFAULT_JUSTIFICATION,
                                                                              TitledBorder.DEFAULT_POSITION, null, null));
    myReplaceLeftRightPropertiesCheckBox = new JCheckBox();
    loadButtonText(myReplaceLeftRightPropertiesCheckBox, AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.option.label.layouts.options.replace.leftright.txt"));
    panel3.add(myReplaceLeftRightPropertiesCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                         GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                         GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(251, 23),
                                                                         null, 0, false));
    myGenerateV17VersionsCheckBox = new JCheckBox();
    loadButtonText(myGenerateV17VersionsCheckBox, AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.option.label.layouts.options.generate.v17.txt"));
    panel3.add(myGenerateV17VersionsCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                  null, new Dimension(251, 23), null, 0, false));
  }

  private void loadButtonText(AbstractButton component, String text) {
    TextWithMnemonic textWithMnemonic = TextWithMnemonic.parse(text);
    component.setText(text);
    if (textWithMnemonic.hasMnemonic()) {
      component.setMnemonic(textWithMnemonic.getMnemonicCode());
      component.setDisplayedMnemonicIndex(textWithMnemonic.getMnemonicIndex());
    }
  }
}
