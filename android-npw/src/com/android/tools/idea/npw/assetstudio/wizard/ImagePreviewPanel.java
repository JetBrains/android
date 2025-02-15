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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.tools.adtui.ImageComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImagePreviewPanel {
  private JBLabel myImageLabel;
  private ImageComponent myImage;
  private JPanel myComponent;

  public ImagePreviewPanel() {
    setupUI();
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  public void setLabelText(@Nullable String title) {
    // The label is displayed on top of a gray/white checkered background, so we *do* want to use the actual Black color.
    //noinspection UseJBColor
    myImageLabel.setForeground(Color.BLACK);
    myImageLabel.setText(title);
  }

  public void setImage(@Nullable BufferedImage image) {
    if (image == null) {
      myImage.setIcon(null);
      return;
    }
    ImageIcon icon = new ImageIcon(image);
    Dimension d = new Dimension(icon.getIconWidth(), icon.getIconHeight());
    myImage.setPreferredSize(d);
    myImage.setMinimumSize(d);
    myImage.setIcon(icon);
  }

  public void setImageBorder(@Nullable Border border) {
    myImage.setBorder(border);
  }

  public void setImageBackground(@Nullable Color background) {
    myImage.setBackground(background);
  }

  public void setImageOpaque(boolean opaque) {
    myImage.setOpaque(opaque);
  }

  private void createUIComponents() {
    // Note: We override baseline so that the component can be vertically aligned at the bottom of the panel
    myComponent = new JPanel() {
      @Override
      public int getBaseline(int width, int height) {
        return height;
      }
    };
  }

  private void setupUI() {
    createUIComponents();
    myComponent.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myComponent.setOpaque(false);
    myImageLabel = new JBLabel();
    myImageLabel.setHorizontalAlignment(0);
    myImageLabel.setHorizontalTextPosition(0);
    myImageLabel.setText("ImageTitle");
    myComponent.add(myImageLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myImage = new ImageComponent();
    myComponent.add(myImage, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                 null, 0, false));
  }
}