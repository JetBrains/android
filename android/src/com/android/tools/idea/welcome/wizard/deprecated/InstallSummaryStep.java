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

import static com.android.tools.idea.welcome.wizard.InstallSummaryStepKt.getPackagesTable;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

import com.android.repository.api.RemotePackage;
import com.android.repository.impl.meta.Archive;
import com.android.tools.idea.welcome.SdkLocationUtils;
import com.android.tools.idea.welcome.wizard.WelcomeUiUtils;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides an explanation of changes the wizard will perform.
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.InstallSummaryStep}
 */
public final class InstallSummaryStep extends FirstRunWizardStep {
  private final Key<Boolean> myKeyCustomInstall;
  private final Key<String> myKeySdkInstallLocation;
  private final Supplier<? extends Collection<RemotePackage>> myPackagesProvider;
  private JTextPane mySummaryText;
  private JPanel myRoot;

  public InstallSummaryStep(Key<Boolean> keyCustomInstall,
                            Key<String> keySdkInstallLocation,
                            Supplier<? extends Collection<RemotePackage>> packagesProvider) {
    super("Verify Settings");
    myKeyCustomInstall = keyCustomInstall;
    myKeySdkInstallLocation = keySdkInstallLocation;
    myPackagesProvider = packagesProvider;
    mySummaryText.setEditorKit(HTMLEditorKitBuilder.simple());
    // There is no need to add whitespace on the top
    mySummaryText.setBorder(JBUI.Borders.empty(0, WizardConstants.STUDIO_WIZARD_INSET_SIZE, WizardConstants.STUDIO_WIZARD_INSET_SIZE,
                                               WizardConstants.STUDIO_WIZARD_INSET_SIZE));
    mySummaryText.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          URL url = event.getURL();
          if (url != null) {
            BrowserUtil.browse(url);
          }
        }
      }
    });
    setComponent(myRoot);
  }

  private static Section getPackagesSection(@NotNull Collection<RemotePackage> remotePackages) {
    return new Section("SDK Components to Download", getPackagesTable(remotePackages));
  }

  private static Section getDownloadSizeSection(@NotNull Collection<RemotePackage> remotePackages) {
    long downloadSize = 0;
    for (RemotePackage remotePackage : remotePackages) {
      // TODO: patches?
      Archive archive = remotePackage.getArchive();
      assert archive != null;

      downloadSize += archive.getComplete().getSize();
    }
    return new Section("Total Download Size", downloadSize == 0 ? "" : WelcomeUiUtils.getSizeLabel(downloadSize));
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
      mySummaryText.setText("An error occurred while trying to compute required packages.");
      return;
    }
    Section[] sections = {getSetupTypeSection(), getSdkFolderSection(), getDownloadSizeSection(packages), getPackagesSection(packages)};

    StringBuilder builder = new StringBuilder("<html><head>");
    builder.append(UIUtil.getCssFontDeclaration(StartupUiUtil.getLabelFont(), UIUtil.getLabelForeground(), null, null)).append("</head><body>");

    for (Section section : sections) {
      if (!section.isEmpty()) {
        builder.append(section.toHtml());
      }
    }
    builder.append("</body></html>");
    mySummaryText.setText(builder.toString());
  }

  private Section getSdkFolderSection() {
    File location = getSdkDirectory();

    String text = SdkLocationUtils.isWritable(location.toPath())
                  ? location.getAbsolutePath()
                  : location.getAbsolutePath() + " (read-only)";

    return new Section("SDK Folder", text);
  }

  private File getSdkDirectory() {
    String path = myState.get(myKeySdkInstallLocation);
    assert path != null;

    return new File(path);
  }

  private Section getSetupTypeSection() {
    String setupType = myState.getNotNull(myKeyCustomInstall, false) ? "Custom" : "Standard";
    return new Section("Setup Type", setupType);
  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySummaryText;
  }

  /**
   * Summary section, consists of a header and a body text.
   */
  private final static class Section {
    @NotNull private final String myTitle;
    @NotNull private final String myText;

    private Section(@NotNull String title, @Nullable String text) {
      myTitle = title;
      myText = StringUtil.notNullize(text);
    }

    public boolean isEmpty() {
      return isEmptyOrSpaces(myText);
    }

    public String toHtml() {
      return String.format("<p><strong>%1$s:</strong><br>%2$s</p>", myTitle, myText);
    }
  }
}
