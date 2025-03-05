/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction.TITLE;

/**
 * Dialog for editing convert to constraint layout parameters
 */
public class ConvertToConstraintLayoutForm extends DialogWrapper {

  private JPanel myPanel;
  private JBCheckBox myFlattenCheckBox;
  private JBCheckBox myFlattenReferencedCheckBox;
  private JBCheckBox myIncludeCustomViewCheckBox;

  protected ConvertToConstraintLayoutForm(@Nullable Project project) {
    super(project);
    setupUI();
    setTitle(TITLE);
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public boolean getFlattenHierarchy() {
    return myFlattenCheckBox.isSelected();
  }

  public boolean getFlattenReferenced() {
    return myFlattenReferencedCheckBox.isSelected();
  }

  public boolean getIncludeCustomViews() {
    return myIncludeCustomViewCheckBox.isSelected();
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(7, 1, new Insets(0, 0, 0, 0), -1, -1));
    final MultiLineLabel multiLineLabel1 = new MultiLineLabel();
    multiLineLabel1.setText(
      "This action will convert your layout into a ConstraintLayout, and attempt to set up constraints\nsuch that your layout looks the way it did before. You may need to go and adjust the constraints\nafterwards to ensure that it behaves correctly for different screen sizes.");
    myPanel.add(multiLineLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null, null, 0, false));
    final MultiLineLabel multiLineLabel2 = new MultiLineLabel();
    multiLineLabel2.setText(
      "When selected, this action will not just convert this layout to ConstraintLayout, it will\nrecursively remove all other nested layouts in the hierarchy as well such that you end up\nwith a single, flat layout. This is more efficient.");
    myPanel.add(multiLineLabel2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null, null, 3, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myFlattenCheckBox = new JBCheckBox();
    myFlattenCheckBox.setSelected(true);
    myFlattenCheckBox.setText("Flatten Layout Hierarchy");
    myPanel.add(myFlattenCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       null, null, 0, false));
    myFlattenReferencedCheckBox = new JBCheckBox();
    myFlattenReferencedCheckBox.setSelected(true);
    myFlattenReferencedCheckBox.setText("Don't flatten layouts referenced by id from other files");
    myPanel.add(myFlattenReferencedCheckBox, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final MultiLineLabel multiLineLabel3 = new MultiLineLabel();
    multiLineLabel3.setText(
      "If a layout defines an android:id attribute which is looked up from Java code, flattening \nout this layout may result in code that no longer compiles. Normally this action won't \ninclude these layouts, but if you want to get to a completely flat hierarchy, you may \nwant to enable removing these and then updating the code references as necessary \nafterwards.");
    myPanel.add(multiLineLabel3, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null, null, 3, false));
    myIncludeCustomViewCheckBox = new JBCheckBox();
    myIncludeCustomViewCheckBox.setHideActionText(false);
    myIncludeCustomViewCheckBox.setText("Include custom views");
    myPanel.add(myIncludeCustomViewCheckBox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 3, false));
  }
}
