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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.help.StudioHelpManagerImpl;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;

public class ChooseBundleOrApkStep extends ExportSignedPackageWizardStep {
  public static final String DOC_URL = "https://d.android.com/r/studio-ui/dynamic-delivery/overview.html";
  private final ExportSignedPackageWizard myWizard;
  private JPanel myContentPanel;
  @VisibleForTesting
  JRadioButton myBundleButton;
  @VisibleForTesting
  JRadioButton myApksButton;
  private JPanel myBundlePanel;
  private JPanel myApkPanel;
  private HyperlinkLabel myLearMoreLink;
  @VisibleForTesting
  JBLabel myGradleErrorLabel;

  public ChooseBundleOrApkStep(ExportSignedPackageWizard wizard, GradleVersion version) {
    myWizard = wizard;
    // bundle only available with gradle >= 3.2
    if (version.isAtLeastIncludingPreviews(3, 2, 0)) {
      myBundleButton.setSelected(true);
      myGradleErrorLabel.setVisible(false);
    } else {
      myBundleButton.setEnabled(false);
      myApksButton.setSelected(true);
    }

    myLearMoreLink.setHyperlinkText("Learn more");
    myLearMoreLink.setHyperlinkTarget(DOC_URL);
  }

  @Override
  public String getHelpId() {
    return StudioHelpManagerImpl.STUDIO_HELP_PREFIX + "dynamic-delivery/overview.html";
  }

  @Override
  protected void commitForNext() {
    myWizard.setTargetType(myBundleButton.isSelected() ? "bundle" : "apk");
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }
}
