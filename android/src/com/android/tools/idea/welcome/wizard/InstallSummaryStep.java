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

import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import com.android.tools.idea.wizard.WizardConstants;
import com.google.common.base.Supplier;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

/**
 * Provides an explanation of changes the wizard will perform.
 */
public final class InstallSummaryStep extends FirstRunWizardStep {
  private final Key<Boolean> myKeyCustomInstall;
  private final Key<String> myKeySdkInstallLocation;
  private final Supplier<? extends Collection<RemotePkgInfo>> myPackagesProvider;
  private JPanel myPanel;
  private JTextPane mySummaryText;

  public InstallSummaryStep(Key<Boolean> keyCustomInstall,
                            Key<String> keySdkInstallLocation,
                            Supplier<? extends Collection<RemotePkgInfo>> packagesProvider) {
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

  private static Section getPackagesSection(@NotNull Collection<RemotePkgInfo> remotePackages) {
    return new Section("Sdk Components to Download", getPackagesTable(remotePackages));
  }

  @Nullable
  private static String getPackagesTable(@NotNull Collection<RemotePkgInfo> remotePackages) {
    if (remotePackages.isEmpty()) {
      return null;
    }
    TreeSet<RemotePkgInfo> sortedPackagesList = Sets.newTreeSet(new PackageInfoComparator());
    sortedPackagesList.addAll(remotePackages);
    StringBuilder table = new StringBuilder("<table>");
    for (RemotePkgInfo remotePkgInfo : sortedPackagesList) {
      // Adds some whitespace between name and size columns
      table.append("<tr><td>").append(remotePkgInfo.getShortDescription()).append("</td><td>&nbsp;&nbsp;</td><td>")
        .append(WelcomeUIUtils.getSizeLabel(remotePkgInfo.getDownloadSize())).append("</td></tr>");
    }
    table.append("</table>");
    return table.toString();
  }

  private static Section getDownloadSizeSection(@NotNull Collection<RemotePkgInfo> remotePackages) {
    long downloadSize = 0;
    for (RemotePkgInfo remotePackage : remotePackages) {
      downloadSize += remotePackage.getDownloadSize();
    }
    return new Section("Total Download Size", downloadSize == 0 ? "" : WelcomeUIUtils.getSizeLabel(downloadSize));
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    generateSummary();
  }

  @Override
  public void init() {
  }

  private void generateSummary() {
    Collection<RemotePkgInfo> remotePackages = myPackagesProvider.get();
    StringBuilder builder = new StringBuilder("<html><head>");
    builder.append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont(), UIUtil.getLabelForeground(), null, null)).append("</head><body>");
    Collection<Section> sections = ImmutableList.of(getSetupTypeSection(), getDestinationFolderSection(),
                                                    getDownloadSizeSection(remotePackages), getPackagesSection(remotePackages));
    for (Section section : sections) {
      if (!section.isEmpty()) {
        builder.append(section.toHtml());
      }
    }
    builder.append("</body></html>");
    mySummaryText.setText(builder.toString());
  }

  private Section getDestinationFolderSection() {
    String destinationFolder = PathUtil.toSystemDependentName(myState.get(myKeySdkInstallLocation));
    return new Section("Destination Folder", destinationFolder);
  }

  private Section getSetupTypeSection() {
    String setupType = myState.getNotNull(myKeyCustomInstall, false) ? "Custom" : "Standard";
    return new Section("Setup type", setupType);
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

    @NotNull
    public String getTitle() {
      return myTitle;
    }

    @NotNull
    public String getText() {
      return myText;
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
  private static class PackageInfoComparator implements Comparator<RemotePkgInfo> {
    @Override
    public int compare(RemotePkgInfo o1, RemotePkgInfo o2) {
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
      return comparisonChain.compare(o1.getShortDescription(), o2.getShortDescription()).result();
    }
  }
}
