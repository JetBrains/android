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
package com.android.tools.idea.rendering.webp;

import com.android.tools.adtui.webp.WebpMetadata;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WebpConversionDialog extends DialogWrapper implements DocumentListener, ChangeListener, ActionListener {
  @Nls(capitalization = Nls.Capitalization.Title) public static final String TITLE = "Converting Images to WebP";

  private JSlider myQualitySlider;
  private JBTextField myQualityField;
  private JBRadioButton myLossyButton;
  private JBRadioButton myLosslessButton;
  private JBLabel myMinSdkVersionLabel;
  private JBCheckBox mySkipLargerCheckBox;
  private JBCheckBox mySkipNinePatchCheckBox;
  private JBCheckBox myPreviewImagesCheckBox;
  private JBLabel myWarningLabel;
  private JPanel myPanel;
  private JBLabel myPercentLabel;
  private JBLabel myLosslessReqLabel;
  private JBLabel myQualityLabel;
  private JBCheckBox mySkipTransparency;
  private JBLabel myMinSdkVersionLabel2;
  private boolean myIgnore;

  public WebpConversionDialog(@NotNull Project project, int minSdkVersion, @NotNull WebpConversionSettings settings, boolean singleFile) {
    super(project);
    setupUI();
    setTitle(TITLE);
    fromSettings(settings);
    myQualityField.getDocument().addDocumentListener(this);
    myQualitySlider.addChangeListener(this);
    myLossyButton.addActionListener(this);
    myLosslessButton.addActionListener(this);
    actionPerformed(null);

    String minSdkVersionString = minSdkVersion == Integer.MAX_VALUE ? "unknown" : String.valueOf(minSdkVersion);
    String minSdkText = "Current minSdkVersion is " + minSdkVersionString;
    myMinSdkVersionLabel.setText(minSdkText);
    myMinSdkVersionLabel2.setText(minSdkText);

    if (minSdkVersion < 14) {
      myWarningLabel.setText("WARNING: WebP requires API 14; current minSdkVersion is " + minSdkVersionString);
      myWarningLabel.setForeground(JBColor.RED);
      myWarningLabel.setVisible(true);
    }
    else if (minSdkVersion < 18) {
      mySkipTransparency.setSelected(true);
      if (singleFile) {
        myMinSdkVersionLabel.setForeground(JBColor.RED);
        myMinSdkVersionLabel2.setForeground(JBColor.RED);
      }
    }
    else {
      myLosslessReqLabel.setVisible(false);
      myMinSdkVersionLabel.setVisible(false);
    }

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myPanel.setPreferredSize(JBUI.size(500, 300));
    return myPanel;
  }

  private void updateQuality(int quality, boolean updateText, boolean updateSlider) {
    boolean old = myIgnore;
    try {
      myIgnore = true;
      if (updateSlider) {
        myQualitySlider.setValue(quality);
      }
      if (updateText) {
        myQualityField.setText(Integer.toString(quality));
      }
    }
    finally {
      myIgnore = old;
    }
  }

  private int getQualityPercent() {
    String text = myQualityField.getText().trim();
    try {
      int parsed = (int)Float.parseFloat(text);
      return Math.max(0, Math.min(100, parsed));
    }
    catch (NumberFormatException ignore) {
      return -1;
    }
  }

  public void toSettings(@NotNull WebpConversionSettings settings) {
    settings.skipLargerImages = mySkipLargerCheckBox.isSelected();
    settings.skipNinePatches = mySkipNinePatchCheckBox.isSelected();
    settings.previewConversion = myPreviewImagesCheckBox.isSelected();
    settings.skipTransparentImages = mySkipTransparency.isSelected();
    settings.lossless = myLosslessButton.isSelected();
    int quality = getQualityPercent();
    if (quality < 0) {
      quality = (int)(100 * WebpMetadata.DEFAULT_ENCODING_QUALITY);
    }
    settings.quality = quality;
  }

  public void fromSettings(@Nullable WebpConversionSettings settings) {
    if (settings != null) {
      boolean old = myIgnore;
      try {
        myIgnore = true;
        mySkipLargerCheckBox.setSelected(settings.skipLargerImages);
        mySkipNinePatchCheckBox.setSelected(settings.skipNinePatches);
        myPreviewImagesCheckBox.setSelected(settings.previewConversion);
        mySkipTransparency.setSelected(settings.skipTransparentImages);
        myLosslessButton.setSelected(settings.lossless);
        myQualitySlider.setValue(settings.quality);
        myQualityField.setText(Integer.toString(settings.quality));
      }
      finally {
        myIgnore = old;
      }
    }
  }

  private void qualityFieldEdited() {
    if (!myIgnore) {
      int quality = getQualityPercent();
      if (quality >= 0) {
        updateQuality(quality, false, true);
      }
    }
  }

  // Implements DocumentListener

  @Override
  public void insertUpdate(DocumentEvent e) {
    qualityFieldEdited();
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    qualityFieldEdited();
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    qualityFieldEdited();
  }

  // Implements ChangeListener

  @Override
  public void stateChanged(ChangeEvent e) {
    if (!myIgnore) {
      if (e.getSource() == myQualitySlider) {
        updateQuality(myQualitySlider.getValue(), true, false);
      }
    }
  }

  // Implements ActionListener

  @Override
  public void actionPerformed(@Nullable ActionEvent e) {
    if (!myIgnore) {
      boolean lossless = myLosslessButton.isSelected();
      myQualityLabel.setEnabled(!lossless);
      myQualitySlider.setEnabled(!lossless);
      myQualityField.setEnabled(!lossless);
      myPercentLabel.setEnabled(!lossless);
      myPreviewImagesCheckBox.setEnabled(!lossless);

      myMinSdkVersionLabel.setEnabled(lossless);
      myLosslessReqLabel.setEnabled(lossless);
    }
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(14, 5, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.setVisible(true);
    myLossyButton = new JBRadioButton();
    myLossyButton.setSelected(true);
    myLossyButton.setText("Lossy encoding");
    myPanel.add(myLossyButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
    mySkipLargerCheckBox = new JBCheckBox();
    mySkipLargerCheckBox.setSelected(true);
    mySkipLargerCheckBox.setText("Skip files where the encoded result is larger than the original");
    myPanel.add(mySkipLargerCheckBox, new GridConstraints(7, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(0, 20), null, 0, false));
    myQualityLabel = new JBLabel();
    myQualityLabel.setText("Encoding quality:");
    myPanel.add(myQualityLabel,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 4, false));
    final Spacer spacer2 = new Spacer();
    myPanel.add(spacer2, new GridConstraints(1, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myQualityField = new JBTextField();
    myQualityField.setColumns(3);
    myPanel.add(myQualityField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
    myQualitySlider = new JSlider();
    myQualitySlider.setMajorTickSpacing(10);
    myQualitySlider.setPaintLabels(false);
    myQualitySlider.setPaintTicks(true);
    myQualitySlider.setValue(80);
    myPanel.add(myQualitySlider, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(150, -1), null, 0, false));
    myPercentLabel = new JBLabel();
    myPercentLabel.setText("%");
    myPanel.add(myPercentLabel,
                new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myLosslessButton = new JBRadioButton();
    myLosslessButton.setText("Lossless encoding");
    myPanel.add(myLosslessButton, new GridConstraints(3, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null, null, 0, false));
    myLosslessReqLabel = new JBLabel();
    myLosslessReqLabel.setText("Warning: Lossless encoding requires Android 4.3 (API 18)");
    myPanel.add(myLosslessReqLabel,
                new GridConstraints(4, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 4, false));
    myMinSdkVersionLabel = new JBLabel();
    myMinSdkVersionLabel.setText("");
    myPanel.add(myMinSdkVersionLabel,
                new GridConstraints(5, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 4, false));
    myPreviewImagesCheckBox = new JBCheckBox();
    myPreviewImagesCheckBox.setSelected(true);
    myPreviewImagesCheckBox.setText("Preview/inspect each converted image before saving");
    myPanel.add(myPreviewImagesCheckBox, new GridConstraints(2, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             null, null, null, 4, false));
    myWarningLabel = new JBLabel();
    myWarningLabel.setVisible(false);
    myPanel.add(myWarningLabel,
                new GridConstraints(13, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 4, false));
    final Spacer spacer3 = new Spacer();
    myPanel.add(spacer3, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    mySkipTransparency = new JBCheckBox();
    mySkipTransparency.setText("Skip images with transparency/alpha channel");
    myPanel.add(mySkipTransparency, new GridConstraints(9, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Transparency requires Android 4.3 (API 18)");
    myPanel.add(jBLabel1,
                new GridConstraints(10, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 4, false));
    mySkipNinePatchCheckBox = new JBCheckBox();
    mySkipNinePatchCheckBox.setEnabled(false);
    mySkipNinePatchCheckBox.setText("Skip nine-patch (.9.png) images");
    myPanel.add(mySkipNinePatchCheckBox, new GridConstraints(8, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             null, null, null, 0, false));
    myMinSdkVersionLabel2 = new JBLabel();
    myMinSdkVersionLabel2.setText("");
    myPanel.add(myMinSdkVersionLabel2,
                new GridConstraints(11, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 4, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myLossyButton);
    buttonGroup.add(myLosslessButton);
  }
}
