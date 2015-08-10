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

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class GradleSignStep extends ExportSignedPackageWizardStep {
  @NonNls private static final String PROPERTY_APK_PATH = "ExportApk.ApkPath";
  @NonNls private static final String PROPERTY_BUILD_TYPE = "ExportApk.BuildType";
  @NonNls private static final String PROPERTY_FLAVORS = "ExportApk.Flavors";

  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myApkPathField;
  private JComboBox myBuildTypeCombo;
  private JBList myFlavorsList;

  private final ExportSignedPackageWizard myWizard;
  private final DefaultListModel myFlavorsListModel = new DefaultListModel();
  private final DefaultComboBoxModel myBuildTypeComboModel = new DefaultComboBoxModel();

  private AndroidGradleModel myAndroidModel;

  public GradleSignStep(@NotNull ExportSignedPackageWizard exportSignedPackageWizard) {
    myWizard = exportSignedPackageWizard;

    myFlavorsList.setModel(myFlavorsListModel);
    myFlavorsList.setEmptyText(AndroidBundle.message("android.apk.sign.gradle.no.flavors"));
    new ListSpeedSearch(myFlavorsList);

    myBuildTypeCombo.setModel(myBuildTypeComboModel);
  }

  @Override
  public void _init() {
    myAndroidModel = AndroidGradleModel.get(myWizard.getFacet());

    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());
    String lastSelectedBuildType = properties.getValue(PROPERTY_BUILD_TYPE);

    myBuildTypeComboModel.removeAllElements();
    Set<String> buildTypes = myAndroidModel == null ? Collections.<String>emptySet() : myAndroidModel.getBuildTypes();
    for (String buildType : buildTypes) {
      myBuildTypeComboModel.addElement(buildType);

      if ((lastSelectedBuildType == null && buildType.equals("release")) || buildType.equals(lastSelectedBuildType)) {
        myBuildTypeComboModel.setSelectedItem(buildType);
      }
    }

    myFlavorsListModel.clear();
    List<String> productFlavors;
    if (myAndroidModel == null || myAndroidModel.getProductFlavors().isEmpty()) {
      productFlavors = Collections.emptyList();
    } else {
      // if there are multiple flavors, we want the merged flavor list
      Set<String> mergedFlavors = Sets.newHashSet();
      for (Variant v : myAndroidModel.getAndroidProject().getVariants()) {
        mergedFlavors.add(ExportSignedPackageWizard.getMergedFlavorName(v));
      }
      productFlavors = Lists.newArrayList(mergedFlavors);
      Collections.sort(productFlavors);
    }

    TIntArrayList lastSelectedIndices = new TIntArrayList(productFlavors.size());
    String[] flavors = properties.getValues(PROPERTY_FLAVORS);
    Set<String> lastSelectedFlavors = flavors == null ? Collections.<String>emptySet() : Sets.newHashSet(flavors);

    for (int i = 0; i < productFlavors.size(); i++) {
      String flavor = productFlavors.get(i);
      myFlavorsListModel.addElement(flavor);

      if (lastSelectedFlavors.contains(flavor)) {
        lastSelectedIndices.add(i);
      }
    }

    myFlavorsList.setSelectedIndices(lastSelectedIndices.toNativeArray());

    String lastApkFolderPath = properties.getValue(PROPERTY_APK_PATH);
    File lastApkFolder;
    if (lastApkFolderPath != null) {
      lastApkFolder = new File(lastApkFolderPath);
    }
    else {
      if (myAndroidModel == null) {
        lastApkFolder = VfsUtilCore.virtualToIoFile(myWizard.getProject().getBaseDir());
      } else {
        lastApkFolder = myAndroidModel.getRootDirPath();
      }
    }
    myApkPathField.setText(lastApkFolder.getAbsolutePath());
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myApkPathField.addBrowseFolderListener("Select APK Destination Folder", null, myWizard.getProject(), descriptor);
  }

  @Override
  public String getHelpId() {
    return null;
  }

  @Override
  protected void commitForNext() throws CommitStepException {
    if (myAndroidModel == null) {
      throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.no.model"));
    }

    final String apkFolder = myApkPathField.getText().trim();
    if (apkFolder.isEmpty()) {
      throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.missing.destination"));
    }

    File f = new File(apkFolder);
    if (!f.isDirectory() || !f.canWrite()) {
      throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.invalid.destination"));
    }

    int[] selectedFlavorIndices = myFlavorsList.getSelectedIndices();
    if (!myFlavorsListModel.isEmpty() && selectedFlavorIndices.length == 0) {
      throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.missing.flavors"));
    }

    Object[] selectedFlavors = myFlavorsList.getSelectedValues();
    List<String> flavors = new ArrayList<String>(selectedFlavors.length);
    for (int i = 0; i < selectedFlavors.length; i++) {
      flavors.add((String)selectedFlavors[i]);
    }

    myWizard.setApkPath(apkFolder);
    myWizard.setGradleOptions((String)myBuildTypeCombo.getSelectedItem(), flavors);

    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());
    properties.setValue(PROPERTY_APK_PATH, apkFolder);
    properties.setValues(PROPERTY_FLAVORS, ArrayUtil.toStringArray(flavors));
    properties.setValue(PROPERTY_BUILD_TYPE, (String)myBuildTypeCombo.getSelectedItem());
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }
}
