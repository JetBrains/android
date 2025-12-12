/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.deploy;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class DeploymentConfigurable implements Configurable, Configurable.NoScroll {
  private final DeploymentConfiguration myConfiguration;
  private JPanel myContentPanel;
  private JBCheckBox myRunAfterApplyChanges;
  private JBCheckBox myRunAfterApplyCodeChanges;

  public DeploymentConfigurable() {
    myConfiguration = DeploymentConfiguration.getInstance();
    setupUI();
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("configurable.DeploymentConfigurable.displayName");
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    return myConfiguration.APPLY_CHANGES_FALLBACK_TO_RUN != myRunAfterApplyChanges.isSelected() ||
           myConfiguration.APPLY_CODE_CHANGES_FALLBACK_TO_RUN != myRunAfterApplyCodeChanges.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfiguration.APPLY_CHANGES_FALLBACK_TO_RUN = myRunAfterApplyChanges.isSelected();
    myConfiguration.APPLY_CODE_CHANGES_FALLBACK_TO_RUN = myRunAfterApplyCodeChanges.isSelected();
  }

  @Override
  public void reset() {
    myRunAfterApplyChanges.setSelected(myConfiguration.APPLY_CHANGES_FALLBACK_TO_RUN);
    myRunAfterApplyCodeChanges.setSelected(myConfiguration.APPLY_CODE_CHANGES_FALLBACK_TO_RUN);
  }

  private void setupUI() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), -1, -1));
    final Spacer spacer1 = new Spacer();
    myContentPanel.add(spacer1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myRunAfterApplyChanges = new JBCheckBox();
    myRunAfterApplyChanges.setText("Automatically perform \"Run\" when Apply Changes fails");
    myContentPanel.add(myRunAfterApplyChanges, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myRunAfterApplyCodeChanges = new JBCheckBox();
    myRunAfterApplyCodeChanges.setText("Automatically perform \"Run\" when Apply Code Changes fails");
    myContentPanel.add(myRunAfterApplyCodeChanges, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                       GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                       GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText(
      "Enabling these options trigger an automatic rerun of your app only if there is an incompatible change when using Apply Changes.");
    myContentPanel.add(jBLabel1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText(
      "In some cases, when Apply Changes or Apply Code Changes succeeds, you might need to manually restart your app to see your changes.");
    myContentPanel.add(jBLabel2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final JBLabel jBLabel3 = new JBLabel();
    jBLabel3.setText("For example, to see changes you made in your Activity's onCreate() method.");
    myContentPanel.add(jBLabel3, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
  }
}
