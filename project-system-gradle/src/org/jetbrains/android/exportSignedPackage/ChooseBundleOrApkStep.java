/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage;

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.intellij.ui.HyperlinkLabel;

import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;

public class ChooseBundleOrApkStep extends ExportSignedPackageWizardStep {
  public static final String DOC_URL = "https://d.android.com/r/studio-ui/dynamic-delivery/overview.html";
  private final ExportSignedPackageWizard myWizard;
  private JPanel myContentPanel;
  @VisibleForTesting
  JRadioButton myBundleButton;
  @VisibleForTesting
  JRadioButton myApkButton;
  private JPanel myBundlePanel;
  private JPanel myApkPanel;
  private HyperlinkLabel myLearnMoreLink;

  public ChooseBundleOrApkStep(ExportSignedPackageWizard wizard) {
    setupUI();
    myWizard = wizard;

    final GenerateSignedApkSettings settings = GenerateSignedApkSettings.getInstance(wizard.getProject());
    myBundleButton.setSelected(settings.BUILD_TARGET_KEY.equals(ExportSignedPackageWizard.BUNDLE.toString()));
    myApkButton.setSelected(settings.BUILD_TARGET_KEY.equals(ExportSignedPackageWizard.APK.toString()));

    myLearnMoreLink.setHyperlinkText("Learn more");
    myLearnMoreLink.setHyperlinkTarget(DOC_URL);
  }

  @Override
  public String getHelpId() {
    return AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing";
  }

  @Override
  protected void commitForNext() {
    boolean isBundle = myBundleButton.isSelected();
    GenerateSignedApkSettings.getInstance(myWizard.getProject()).BUILD_TARGET_KEY =
      isBundle ? ExportSignedPackageWizard.BUNDLE.toString() : ExportSignedPackageWizard.APK.toString();
    myWizard.setTargetType(isBundle ? ExportSignedPackageWizard.BUNDLE : ExportSignedPackageWizard.APK);
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  private void setupUI() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
    myBundlePanel = new JPanel();
    myBundlePanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    myContentPanel.add(myBundlePanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, false));
    myBundleButton = new JRadioButton();
    Font myBundleButtonFont = getFont(null, Font.BOLD, -1, myBundleButton.getFont());
    if (myBundleButtonFont != null) myBundleButton.setFont(myBundleButtonFont);
    myBundleButton.setSelected(true);
    myBundleButton.setText("Android App Bundle");
    myBundlePanel.add(myBundleButton, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel1 = new JBLabel();
    loadLabelText(jBLabel1,
                             getMessageFromBundle("messages/AndroidBundle", "android.export.package.bundle.description"));
    myBundlePanel.add(jBLabel1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2,
                                                    false));
    myLearnMoreLink = new HyperlinkLabel();
    myLearnMoreLink.setVisible(true);
    myBundlePanel.add(myLearnMoreLink, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
    myApkPanel = new JPanel();
    myApkPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myContentPanel.add(myApkPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       null, null, 0, false));
    myApkButton = new JRadioButton();
    Font myApkButtonFont = getFont(null, Font.BOLD, -1, myApkButton.getFont());
    if (myApkButtonFont != null) myApkButton.setFont(myApkButtonFont);
    myApkButton.setText("APK");
    myApkPanel.add(myApkButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("Build a signed APK that you can deploy to a device");
    myApkPanel.add(jBLabel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
    final Spacer spacer1 = new Spacer();
    myContentPanel.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 2), null, 0, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myBundleButton);
    buttonGroup.add(myApkButton);
  }

  private Font getFont(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    }
    else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      }
      else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac
                            ? new Font(font.getFamily(), font.getStyle(), font.getSize())
                            : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }

  private static Method cachedGetBundleMethod = null;

  private String getMessageFromBundle(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if (cachedGetBundleMethod == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        cachedGetBundleMethod = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)cachedGetBundleMethod.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  private void loadLabelText(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }
}
