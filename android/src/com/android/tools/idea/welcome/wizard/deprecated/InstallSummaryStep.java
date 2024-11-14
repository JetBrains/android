/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import static com.android.tools.idea.welcome.wizard.InstallSummaryStep.generateSummaryHtml;
import static com.android.tools.idea.welcome.wizard.InstallSummaryStep.getDownloadSizeSection;
import static com.android.tools.idea.welcome.wizard.InstallSummaryStep.getPackagesSection;
import static com.android.tools.idea.welcome.wizard.InstallSummaryStep.getSdkFolderSection;
import static com.android.tools.idea.welcome.wizard.InstallSummaryStep.getSetupTypeSection;

import com.android.repository.api.RemotePackage;
import com.android.tools.idea.welcome.wizard.Section;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.Nullable;

/**
 * Provides an explanation of changes the wizard will perform.
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.InstallSummaryStep}
 */
@Deprecated
public final class InstallSummaryStep extends FirstRunWizardStep {
  private final Key<Boolean> myKeyCustomInstall;
  private final Key<String> myKeySdkInstallLocation;
  private final Supplier<? extends Collection<RemotePackage>> myPackagesProvider;
  private final InstallSummaryStepForm myForm = new InstallSummaryStepForm();

  public InstallSummaryStep(Key<Boolean> keyCustomInstall,
                            Key<String> keySdkInstallLocation,
                            Supplier<? extends Collection<RemotePackage>> packagesProvider) {
    super("Verify Settings");
    myKeyCustomInstall = keyCustomInstall;
    myKeySdkInstallLocation = keySdkInstallLocation;
    myPackagesProvider = packagesProvider;

    setComponent(myForm.getRoot());
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    generateSummary();
    invokeUpdate(null);
  }

  @Override
  public void init() {
  }

  private void generateSummary() {
    Collection<RemotePackage> packages = myPackagesProvider.get();
    if (packages == null) {
      myForm.getSummaryText().setText("An error occurred while trying to compute required packages.");
      return;
    }
    Section[] sections = {
      getSetupTypeSection(myState.getNotNull(myKeyCustomInstall, false) ? "Custom" : "Standard"),
      getSdkFolderSection(getSdkDirectory()),
      getDownloadSizeSection(packages),
      getPackagesSection(packages)
    };
    myForm.getSummaryText().setText(generateSummaryHtml(Arrays.stream(sections).toList()));
    myForm.getSummaryText().setCaretPosition(0); // Otherwise the scroll view will already be scrolled to the bottom when the UI is first shown
  }

  private File getSdkDirectory() {
    String path = myState.get(myKeySdkInstallLocation);
    assert path != null;

    return new File(path);
  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myForm.getSummaryText();
  }

}
