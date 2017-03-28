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
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class GradleSignStep extends ExportSignedPackageWizardStep {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.exportSignedPackage.GradleSignStep");

  @NonNls private static final String PROPERTY_APK_PATH = "ExportApk.ApkPath";
  @NonNls private static final String PROPERTY_BUILD_TYPE = "ExportApk.BuildType";
  @NonNls private static final String PROPERTY_FLAVORS = "ExportApk.Flavors";
  @NonNls private static final String PROPERTY_V1_SIGN = "ExportApk.SignV1";
  @NonNls private static final String PROPERTY_V2_SIGN = "ExportApk.SignV2";

  public static final GradleVersion MIN_SIGNATURE_SELECTION_VERSION = new GradleVersion(2, 3, 0);

  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myApkPathField;
  private JComboBox myBuildTypeCombo;
  private JBList myFlavorsList;
  private JCheckBox myV1JarSignatureCheckBox;
  private JCheckBox myV2FullAPKSignatureCheckBox;
  private JBLabel mySignatureHelpLabel;

  private final ExportSignedPackageWizard myWizard;
  private final DefaultListModel myFlavorsListModel = new DefaultListModel();
  private final DefaultComboBoxModel myBuildTypeComboModel = new DefaultComboBoxModel();

  private AndroidModuleModel myAndroidModel;

  public GradleSignStep(@NotNull ExportSignedPackageWizard exportSignedPackageWizard) {
    myWizard = exportSignedPackageWizard;

    myFlavorsList.setModel(myFlavorsListModel);
    myFlavorsList.setEmptyText(AndroidBundle.message("android.apk.sign.gradle.no.flavors"));
    new ListSpeedSearch(myFlavorsList);

    myBuildTypeCombo.setModel(myBuildTypeComboModel);
  }

  @Override
  public void _init() {
    myAndroidModel = AndroidModuleModel.get(myWizard.getFacet());

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

    GradleVersion modelVersion = null;
    if (myAndroidModel != null) {
      modelVersion = myAndroidModel.getModelVersion();
    }

    boolean enabled = modelVersion != null && modelVersion.compareIgnoringQualifiers(MIN_SIGNATURE_SELECTION_VERSION) >= 0;
    myV1JarSignatureCheckBox.setEnabled(enabled);
    myV1JarSignatureCheckBox.setSelected(properties.getBoolean(PROPERTY_V1_SIGN));
    myV2FullAPKSignatureCheckBox.setEnabled(enabled);
    myV2FullAPKSignatureCheckBox.setSelected(properties.getBoolean(PROPERTY_V2_SIGN));

    // Set HTML label here; the visual editor does not like the "&nbsp;" part so set the text here.
    mySignatureHelpLabel.setText("<html><a href=\"\">Signature&nbsp;Help</a></html>");
    mySignatureHelpLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    mySignatureHelpLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (Desktop.isDesktopSupported()) {
          Desktop desktop = Desktop.getDesktop();
          if (desktop.isSupported(Desktop.Action.BROWSE)) {
            URI uri;
            try {
              uri = new URI("http://developer.android.com/about/versions/nougat/android-7.0.html#apk_signature_v2");
            } catch (URISyntaxException ex) {
              throw new AssertionError(ex);
            }

            try {
              desktop.browse(uri);
            } catch (IOException ex) {
              LOG.error("Failed to open URI '" + uri + "'", ex);
            }
          }
        }
      }
    });
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

    boolean isV1 = myV1JarSignatureCheckBox.isSelected();
    boolean isV2 = myV2FullAPKSignatureCheckBox.isSelected();
    if (myV1JarSignatureCheckBox.isEnabled() && !isV1 && !isV2) {
      throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.missing.signature-version"));
    }

    myWizard.setApkPath(apkFolder);
    myWizard.setGradleOptions((String)myBuildTypeCombo.getSelectedItem(), flavors);
    myWizard.setV1Signature(isV1);
    myWizard.setV2Signature(isV2);

    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());
    properties.setValue(PROPERTY_APK_PATH, apkFolder);
    properties.setValues(PROPERTY_FLAVORS, ArrayUtil.toStringArray(flavors));
    properties.setValue(PROPERTY_BUILD_TYPE, (String)myBuildTypeCombo.getSelectedItem());
    properties.setValue(PROPERTY_V1_SIGN, myV1JarSignatureCheckBox.isSelected());
    properties.setValue(PROPERTY_V2_SIGN, myV2FullAPKSignatureCheckBox.isSelected());
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }
}
