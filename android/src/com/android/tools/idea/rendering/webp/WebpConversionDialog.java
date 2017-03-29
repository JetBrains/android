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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class WebpConversionDialog extends DialogWrapper implements DocumentListener, ChangeListener, ActionListener {
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

  public WebpConversionDialog(@NotNull Project project, int minSdkVersion, @NotNull WebpConversionSettings settings,
                              boolean singleFile) {
    super(project);
    setTitle(ConvertToWebpAction.TITLE);
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
    } else if (minSdkVersion < 18) {
      mySkipTransparency.setSelected(true);
      if (singleFile) {
        myMinSdkVersionLabel.setForeground(JBColor.RED);
        myMinSdkVersionLabel2.setForeground(JBColor.RED);
      }
    } else {
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
    } finally {
      myIgnore = old;
    }
  }

  private int getQualityPercent() {
    String text = myQualityField.getText().trim();
    try {
      int parsed = (int)Float.parseFloat(text);
      return Math.max(0, Math.min(100, parsed));
    } catch (NumberFormatException ignore) {
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
      } finally {
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
}
