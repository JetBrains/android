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
package com.android.tools.idea.welcome.wizard;

import com.android.repository.api.RemotePackage;
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.welcome.SdkLocationUtils;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Provides an explanation of changes the wizard will perform.
 */
public final class InstallSummaryStep extends FirstRunWizardStep {
  private final Key<Boolean> myKeyCustomInstall;
  private final Key<String> myKeySdkInstallLocation;
  private final Supplier<? extends Collection<RemotePackage>> myPackagesProvider;
  private JPanel myPanel;
  private JTextPane mySummaryText;

  public InstallSummaryStep(Key<Boolean> keyCustomInstall,
                            Key<String> keySdkInstallLocation,
                            Supplier<? extends Collection<RemotePackage>> packagesProvider) {
    super("Verify Settings");
    myKeyCustomInstall = keyCustomInstall;
    myKeySdkInstallLocation = keySdkInstallLocation;
    myPackagesProvider = packagesProvider;
    mySummaryText.setContentType(UIUtil.HTML_MIME);
    // There is no need to add whitespace on the top
    mySummaryText.setBorder(new EmptyBorder(0, WizardConstants.STUDIO_WIZARD_INSET_SIZE, WizardConstants.STUDIO_WIZARD_INSET_SIZE,
                                            WizardConstants.STUDIO_WIZARD_INSET_SIZE));
    setComponent(myPanel);
  }

  private static Section getPackagesSection(@NotNull Collection<RemotePackage> remotePackages) {
    return new Section("SDK Components to Download", getPackagesTable(remotePackages));
  }

  @Nullable
  private static String getPackagesTable(@NotNull Collection<RemotePackage> remotePackages) {
    if (remotePackages.isEmpty()) {
      return null;
    }
    TreeSet<RemotePackage> sortedPackagesList = Sets.newTreeSet(new PackageInfoComparator());
    sortedPackagesList.addAll(remotePackages);
    StringBuilder table = new StringBuilder("<table>");
    for (RemotePackage remotePkgInfo : sortedPackagesList) {
      Archive archive = remotePkgInfo.getArchive();
      assert archive != null;

      // Adds some whitespace between name and size columns
      table
        .append("<tr><td>")
        .append(remotePkgInfo.getDisplayName())
        .append("</td><td>&nbsp;&nbsp;</td><td>")
        .append(WelcomeUIUtils.getSizeLabel(archive.getComplete().getSize()))
        .append("</td></tr>");
    }
    table.append("</table>");
    return table.toString();
  }

  private static Section getDownloadSizeSection(@NotNull Collection<RemotePackage> remotePackages) {
    long downloadSize = 0;
    for (RemotePackage remotePackage : remotePackages) {
      // TODO: patches?
      Archive archive = remotePackage.getArchive();
      assert archive != null;

      downloadSize += archive.getComplete().getSize();
    }
    return new Section("Total Download Size", downloadSize == 0 ? "" : WelcomeUIUtils.getSizeLabel(downloadSize));
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
    builder.append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont(), UIUtil.getLabelForeground(), null, null)).append("</head><body>");

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

    String text = SdkLocationUtils.isWritable(FileOpUtils.create(), location)
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

    public Section(@NotNull String title, @Nullable String text) {
      myTitle = title;
      myText = StringUtil.notNullize(text);
    }

    public boolean isEmpty() {
      return StringUtil.isEmptyOrSpaces(myText);
    }

    public String toHtml() {
      return String.format("<p><strong>%1$s:</strong><br>%2$s</p>", myTitle, myText);
    }
  }

  /**
   * Sorts package info in descending size order. Packages with the same size are sorted alphabetically.
   */
  private static class PackageInfoComparator implements Comparator<RemotePackage> {
    @Override
    public int compare(RemotePackage o1, RemotePackage o2) {
      if (o1 == o2) {
        return 0;
      }
      else if (o1 == null) {
        return -1;
      }
      else if (o2 == null) {
        return 1;
      }
      ComparisonChain comparisonChain = ComparisonChain.start();
      return comparisonChain.compare(o1.getDisplayName(), o2.getDisplayName()).result();
    }
  }
}
