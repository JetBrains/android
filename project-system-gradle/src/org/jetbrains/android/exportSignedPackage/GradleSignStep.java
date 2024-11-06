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
package org.jetbrains.android.exportSignedPackage;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.google.common.collect.Sets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.TargetType;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class GradleSignStep extends ExportSignedPackageWizardStep {
  @NonNls private static final String PROPERTY_APK_PATH = "ExportApk.ApkPath";
  @NonNls private static final String PROPERTY_BUNDLE_PATH = "ExportBundle.BundlePath";
  @VisibleForTesting
  @NonNls static final String PROPERTY_BUILD_VARIANTS = "ExportApk.BuildVariants";

  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myApkPathField;
  private JBList<String> myBuildVariantsList;

  private final ExportSignedPackageWizard myWizard;
  private final DefaultListModel<String> myBuildVariantsListModel = new DefaultListModel<>();

  private GradleAndroidModel myAndroidModel;

  public GradleSignStep(@NotNull ExportSignedPackageWizard exportSignedPackageWizard) {
    myWizard = exportSignedPackageWizard;

    myBuildVariantsList.setModel(myBuildVariantsListModel);
    myBuildVariantsList.setEmptyText(AndroidBundle.message("android.apk.sign.gradle.no.variants"));
    ListSpeedSearch.installOn(myBuildVariantsList);
    myApkPathField.addBrowseFolderListener(myWizard.getProject(), FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle("Select APK Destination Folder"));
  }

  @Override
  public void _init() {
    _init(GradleAndroidModel.get(myWizard.getFacet()));
  }

  @VisibleForTesting
  void _init(GradleAndroidModel androidModel) {
    myAndroidModel = androidModel;

    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());

    myBuildVariantsListModel.clear();
    List<String> buildVariants = new ArrayList<>();
    if (myAndroidModel != null) {
      buildVariants.addAll(myAndroidModel.getFilteredVariantNames());
      Collections.sort(buildVariants);
    }

    IntList lastSelectedIndices = new IntArrayList(buildVariants.size());
    List<String> cachedVariants = properties.getList(PROPERTY_BUILD_VARIANTS);
    Set<String> lastSelectedVariants = cachedVariants == null ? Collections.emptySet() : Sets.newHashSet(cachedVariants);

    for (int i = 0; i < buildVariants.size(); i++) {
      String variant = buildVariants.get(i);
      myBuildVariantsListModel.addElement(variant);

      if (lastSelectedVariants.contains(variant)) {
        lastSelectedIndices.add(i);
      }
    }

    myBuildVariantsList.setSelectedIndices(lastSelectedIndices.toIntArray());

    String moduleName = myAndroidModel.getModuleName();
    TargetType targetType = myWizard.getTargetType();
    myApkPathField.setText(FileUtil.toSystemDependentName(getInitialPath(properties, moduleName, targetType)));
  }

  @Override
  public String getHelpId() {
    return AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing";
  }

  @Override
  protected void commitForNext() throws CommitStepException {
    if (myAndroidModel == null) {
      throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.no.model"));
    }

    final String apkFolder = myApkPathField.getText().stripLeading();
    if (apkFolder.isEmpty()) {
      throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.missing.destination", myWizard.getTargetType()));
    }

    File f = new File(apkFolder);
    if (!f.isDirectory() || !f.canWrite()) {
      throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.invalid.destination"));
    }

    int[] selectedVariantIndices = myBuildVariantsList.getSelectedIndices();
    if (myBuildVariantsList.isEmpty() || selectedVariantIndices.length == 0) {
      throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.missing.variants"));
    }

    List<String> buildVariants = myBuildVariantsList.getSelectedValuesList();

    myWizard.setApkPath(apkFolder);
    myWizard.setGradleOptions(myBuildVariantsList.getSelectedValuesList());

    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());
    properties.setValue(getApkPathPropertyName(myAndroidModel.getModuleName(), myWizard.getTargetType()), apkFolder);
    properties.setList(PROPERTY_BUILD_VARIANTS, buildVariants);
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  @VisibleForTesting
  String getInitialPath(@NotNull PropertiesComponent properties, @NotNull String moduleName, @NotNull TargetType targetType) {
    String lastApkFolderPath = properties.getValue(getApkPathPropertyName(moduleName, targetType));
    if (!isNullOrEmpty(lastApkFolderPath)) {
      return lastApkFolderPath;
    }
    if (myAndroidModel == null) {
      return myWizard.getProject().getBaseDir().getPath();
    }
    else {
      return myAndroidModel.getRootDirPath().getPath();
    }
  }

  @VisibleForTesting
  String getApkPathPropertyName(String moduleName, TargetType targetType) {
    return (targetType.equals(ExportSignedPackageWizard.APK) ? PROPERTY_APK_PATH : PROPERTY_BUNDLE_PATH) +
           (isNullOrEmpty(moduleName) ? "" : "For" + moduleName);
  }
}