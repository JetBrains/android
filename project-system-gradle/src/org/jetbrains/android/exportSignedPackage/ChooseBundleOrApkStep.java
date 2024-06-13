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

import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.HyperlinkLabel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

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
}
