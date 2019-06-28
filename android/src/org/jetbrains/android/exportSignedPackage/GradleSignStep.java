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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
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
import com.intellij.util.ArrayUtilRt;
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
  @NonNls private static final String PROPERTY_BUILD_VARIANTS = "ExportApk.BuildVariants";
  @NonNls private static final String PROPERTY_V1_SIGN = "ExportApk.SignV1";
  @NonNls private static final String PROPERTY_V2_SIGN = "ExportApk.SignV2";

  public static final GradleVersion MIN_SIGNATURE_SELECTION_VERSION = new GradleVersion(2, 3, 0);

  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myApkPathField;
  private JBList<String> myBuildVariantsList;
  private JCheckBox myV1JarSignatureCheckBox;
  private JCheckBox myV2FullAPKSignatureCheckBox;
  private JBLabel mySignatureHelpLabel;
  private JBLabel mySignatureLabel;
  private JPanel mySignaturePanel;

  private final ExportSignedPackageWizard myWizard;
  private final DefaultListModel<String> myBuildVariantsListModel = new DefaultListModel<>();

  private AndroidModuleModel myAndroidModel;

  public GradleSignStep(@NotNull ExportSignedPackageWizard exportSignedPackageWizard) {
    myWizard = exportSignedPackageWizard;

    myBuildVariantsList.setModel(myBuildVariantsListModel);
    myBuildVariantsList.setEmptyText(AndroidBundle.message("android.apk.sign.gradle.no.variants"));
    new ListSpeedSearch<>(myBuildVariantsList);
  }

  @Override
  public void _init() {
    myAndroidModel = AndroidModuleModel.get(myWizard.getFacet());

    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());

    myBuildVariantsListModel.clear();
    List<String> buildVariants = new ArrayList<>();
    if (myAndroidModel != null) {
      buildVariants.addAll(myAndroidModel.getVariantNames());
      Collections.sort(buildVariants);
    }

    TIntArrayList lastSelectedIndices = new TIntArrayList(buildVariants.size());
    String[] cachedVariants = properties.getValues(PROPERTY_BUILD_VARIANTS);
    Set<String> lastSelectedVariants = cachedVariants == null ? Collections.emptySet() : Sets.newHashSet(cachedVariants);

    for (int i = 0; i < buildVariants.size(); i++) {
      String variant = buildVariants.get(i);
      myBuildVariantsListModel.addElement(variant);

      if (lastSelectedVariants.contains(variant)) {
        lastSelectedIndices.add(i);
      }
    }

    myBuildVariantsList.setSelectedIndices(lastSelectedIndices.toNativeArray());

    String lastApkFolderPath = properties.getValue(PROPERTY_APK_PATH);
    File lastApkFolder;
    if (lastApkFolderPath != null) {
      lastApkFolder = new File(lastApkFolderPath);
    }
    else {
      if (myAndroidModel == null) {
        lastApkFolder = VfsUtilCore.virtualToIoFile(myWizard.getProject().getBaseDir());
      }
      else {
        lastApkFolder = myAndroidModel.getRootDirPath();
      }
    }
    myApkPathField.setText(lastApkFolder.getAbsolutePath());
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myApkPathField.addBrowseFolderListener("Select APK Destination Folder", null, myWizard.getProject(), descriptor);

    boolean isBundle = myWizard.getTargetType().equals(ExportSignedPackageWizard.BUNDLE);
    mySignaturePanel.setVisible(!isBundle);
    mySignatureLabel.setVisible(!isBundle);
    myV1JarSignatureCheckBox.setEnabled(!isBundle);
    if (!isBundle) {
      GradleVersion modelVersion = null;
      if (myAndroidModel != null) {
        modelVersion = myAndroidModel.getModelVersion();
      }
      initSignaturePanel(properties, modelVersion);
    }
  }

  private void initSignaturePanel(PropertiesComponent properties, GradleVersion modelVersion) {
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
            }
            catch (URISyntaxException ex) {
              throw new AssertionError(ex);
            }

            try {
              desktop.browse(uri);
            }
            catch (IOException ex) {
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

    List buildVariants = myBuildVariantsList.getSelectedValuesList();

    boolean isV1 = myV1JarSignatureCheckBox.isSelected();
    boolean isV2 = myV2FullAPKSignatureCheckBox.isSelected();
    if (myV1JarSignatureCheckBox.isEnabled() && !isV1 && !isV2) {
      throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.missing.signature-version"));
    }

    myWizard.setApkPath(apkFolder);
    //noinspection unchecked
    myWizard.setGradleOptions(myBuildVariantsList.getSelectedValuesList());
    myWizard.setV1Signature(isV1);
    myWizard.setV2Signature(isV2);

    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());
    properties.setValue(PROPERTY_APK_PATH, apkFolder);
    //noinspection unchecked
    properties.setValues(PROPERTY_BUILD_VARIANTS, ArrayUtilRt.toStringArray(buildVariants));
    properties.setValue(PROPERTY_V1_SIGN, myV1JarSignatureCheckBox.isSelected());
    properties.setValue(PROPERTY_V2_SIGN, myV2FullAPKSignatureCheckBox.isSelected());
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }
}
