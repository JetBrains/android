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

import static com.android.tools.adtui.stdui.StandardColors.DISABLED_TEXT_COLOR;
import static com.android.tools.adtui.stdui.StandardColors.TEXT_COLOR;
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.TargetType.APK;
import static org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.TargetType.BUNDLE;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.model.IdeBasicVariant;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gservices.DevServicesDeprecationData;
import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.io.File;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import kotlin.Pair;
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.TargetType;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class GradleSignStep extends ExportSignedPackageWizardStep {
  @NonNls private static final String PROPERTY_APK_PATH = "ExportApk.ApkPath";
  @NonNls private static final String PROPERTY_BUNDLE_PATH = "ExportBundle.BundlePath";
  @VisibleForTesting
  public @NonNls static final String PROPERTY_BUILD_VARIANTS = "ExportApk.BuildVariants";

  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myApkPathField;
  @VisibleForTesting
  JBList<VariantItem> myBuildVariantsList;

  private final ExportSignedPackageWizard myWizard;
  private final DefaultListModel<VariantItem> myBuildVariantsListModel = new DefaultListModel<>();
  private final Set<String> disabledItems = new HashSet<>();

  private GradleAndroidModel myAndroidModel;

  private final JBLabel mySelectedKeyLabel = new JBLabel("Selected Key");
  private final JBLabel mySelectedKey = new JBLabel();
  private final JBLabel myAdiSelectedAppId = new JBLabel();
  @VisibleForTesting
  final JBLabel myAdiStatus = new JBLabel();
  private final JBLabel myAdiStatusIcon = new JBLabel();
  @VisibleForTesting
  final JBLabel myAdiStatusDescription = new JBLabel();
  private final BrowserLink myLearnMoreLink = new BrowserLink("Learn more", "https://d.android.com/r/studio-ui/developer-verification/learn-more");
  private final JBLabel myWarningText = new JBLabel();

  private final AdiClient myAdiClient;

  private CompletableFuture<Pair<Map<String, RegistrationState>, DevServicesDeprecationData>> myCurrentAdiCheck;

  @VisibleForTesting
  static class VariantItem {
    VariantItem(@NotNull String name) {
      this.name = name;
    }

    @NotNull
    public final String name;
    @Nullable
    public Icon icon = EmptyIcon.ICON_16;
  }

  public GradleSignStep(@NotNull ExportSignedPackageWizard exportSignedPackageWizard) {
    this(exportSignedPackageWizard, new AdiClient(exportSignedPackageWizard.getDisposable()));
  }

  @VisibleForTesting
  GradleSignStep(@NotNull ExportSignedPackageWizard exportSignedPackageWizard, @NotNull AdiClient adiClient) {
    myAdiClient = adiClient;
    setupUI();
    myWizard = exportSignedPackageWizard;

    myBuildVariantsList.setModel(myBuildVariantsListModel);
    myBuildVariantsList.setSelectionModel(new DisabledItemSelectionModel());
    myBuildVariantsList.setCellRenderer(new DisabledItemListCellRenderer());
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
  public void _init(GradleAndroidModel androidModel) {
    myAndroidModel = androidModel;
    myAdiClient.reset();

    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());

    myBuildVariantsListModel.clear();
    List<String> buildVariants = new ArrayList<>();
    if (myAndroidModel != null) {
      buildVariants.addAll(myAndroidModel.getFilteredVariantNames());
      Collections.sort(buildVariants);

      disabledItems.addAll(myAndroidModel.getFilteredDebuggableVariants());
    }

    IntList lastSelectedIndices = new IntArrayList(buildVariants.size());
    List<String> cachedVariants = properties.getList(PROPERTY_BUILD_VARIANTS);
    Set<String> lastSelectedVariants = cachedVariants == null ? Collections.emptySet() : Sets.newHashSet(cachedVariants);
    TargetType targetType = myWizard.getTargetType();

    Multimap<String, VariantItem> idsToCheck = ArrayListMultimap.create();
    for (int i = 0; i < buildVariants.size(); i++) {
      String variant = buildVariants.get(i);
      VariantItem item = new VariantItem(variant);
      myBuildVariantsListModel.addElement(item);
      idsToCheck.put(getAppId(variant), item);
      if (lastSelectedVariants.contains(variant)) {
        lastSelectedIndices.add(i);
      }
    }
    if (StudioFlags.SIGNED_BUILD_ADV_FEATURE.get()) {
      byte[] cert;
      try {
        cert = myWizard.getTargetType() == BUNDLE ? null : myWizard.getCertificate().getEncoded();
      }
      catch (CertificateEncodingException e) {
        throw new RuntimeException(e);
      }
      myAdiClient.checkPackageRegistrationStatusAsync(idsToCheck.keySet(), cert).thenAccept(result -> {
        result.getFirst().forEach(
          (id, state) -> idsToCheck.get(id).forEach(item -> {
            if (state == RegistrationState.REGISTERED) {
              item.icon = StudioIcons.Common.SUCCESS_INLINE;
            }
            else if (state == RegistrationState.STUDIO_VERSION_UNSUPPORTED) {
              item.icon = EmptyIcon.ICON_16;
            }
            else {
              item.icon = AllIcons.General.Note;
            }
          }));
        myBuildVariantsList.repaint();
      });
    }

    myBuildVariantsList.setSelectedIndices(lastSelectedIndices.toIntArray());

    if (StudioFlags.SIGNED_BUILD_ADV_FEATURE.get()) {
      myBuildVariantsList.addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting()) {
          updateAdiStatus();
        }
      });
    }

    String moduleName = myAndroidModel.getModuleName();
    myApkPathField.setText(FileUtil.toSystemDependentName(getInitialPath(properties, moduleName, targetType)));
    mySelectedKey.setVisible(targetType == APK);
    var gradleSigningInfo = myWizard.getGradleSigningInfo();
    var keyAlias = "No key selected";
    if (gradleSigningInfo != null) {
      keyAlias = gradleSigningInfo.keyAlias;
    }
    mySelectedKey.setText(keyAlias);
    mySelectedKeyLabel.setVisible(targetType == APK);
    myWarningText.setText("<html>Soon you will be required to verify your identity and register your app's package name in order for it to be installed by users on certified Android devices.</html>");

    if (StudioFlags.SIGNED_BUILD_ADV_FEATURE.get()) {
      updateAdiStatus();
    }
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

    List<String> buildVariants = myBuildVariantsList.getSelectedValuesList().stream().map(item -> item.name).toList();

    myWizard.setApkPath(apkFolder);
    myWizard.setGradleOptions(myBuildVariantsList.getSelectedValuesList().stream().map(item -> item.name).toList());

    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());
    properties.setValue(getApkPathPropertyName(myAndroidModel.getModuleName(), myWizard.getTargetType()), apkFolder);
    properties.setList(PROPERTY_BUILD_VARIANTS, buildVariants);
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  @VisibleForTesting
  public String getInitialPath(@NotNull PropertiesComponent properties, @NotNull String moduleName, @NotNull TargetType targetType) {
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
  public String getApkPathPropertyName(String moduleName, TargetType targetType) {
    return (targetType.equals(ExportSignedPackageWizard.APK) ? PROPERTY_APK_PATH : PROPERTY_BUNDLE_PATH) +
           (isNullOrEmpty(moduleName) ? "" : "For" + moduleName);
  }

  private void setupUI() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(8, 3, JBUI.emptyInsets(), -1, 7));
    final JBLabel destinationFolderLabel = new JBLabel();
    destinationFolderLabel.setText("Destination Folder");
    destinationFolderLabel.setDisplayedMnemonic('D');
    destinationFolderLabel.setDisplayedMnemonicIndex(0);
    myContentPanel.add(destinationFolderLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    myApkPathField = new TextFieldWithBrowseButton();
    myContentPanel.add(myApkPathField, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                           null, null, 0, false));
    destinationFolderLabel.setLabelFor(myApkPathField);

    final JBLabel buildVariantsLabel = new JBLabel();
    buildVariantsLabel.setText("Build Variants");
    buildVariantsLabel.setBorder(JBUI.Borders.emptyTop(15));
    myContentPanel.add(buildVariantsLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final JBScrollPane scrollPane = new JBScrollPane();
    myContentPanel.add(scrollPane, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                          null, null, 0, false));
    myBuildVariantsList = new JBList<>() {
      public String getToolTipText(MouseEvent e) {
        int row = locationToIndex(e.getPoint());
        if (row < 0) return null;
        String name = getModel().getElementAt(row).name;
        if (shouldDisableDebuggable() && disabledItems.contains(name)) {
          return "Variant `" + name + "` is debuggable so cannot be signed";
        }
        else {
          return super.getToolTipText(e);
        }
      }
    };
    scrollPane.setViewportView(myBuildVariantsList);

    if (StudioFlags.SIGNED_BUILD_ADV_FEATURE.get()) {
      final TitledSeparator separator = new TitledSeparator("Registration Status");
      myContentPanel.add(separator, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                        new Dimension(-1, 20), null, 0, false));
      myContentPanel.add(mySelectedKeyLabel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                 null,
                                                                 null, 0, false));
      myContentPanel.add(mySelectedKey,
                         new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                             null, null, null, 0, false));
      mySelectedKey.setBorder(JBUI.Borders.empty(3, 0, 5, 0));
      mySelectedKeyLabel.setBorder(JBUI.Borders.empty(3, 0, 5, 0));

      final JBLabel pakageNameLabel = new JBLabel("Selected App ID");
      myContentPanel.add(pakageNameLabel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null,
                                                              null, 0, false));
      myContentPanel.add(myAdiSelectedAppId,
                         new GridConstraints(4, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                             null, null, null, 0, false));

      myContentPanel.add(myAdiStatusIcon, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_FIXED,
                                                              null,
                                                              null,
                                                              null, 0, false));
      myContentPanel.add(myAdiStatus, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                                          GridConstraints.SIZEPOLICY_FIXED,
                                                          null, null, null));
      myAdiStatusDescription.setForeground(DISABLED_TEXT_COLOR);
      myContentPanel.add(myAdiStatusDescription,
                         new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                             GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                             GridConstraints.SIZEPOLICY_FIXED,
                                             null, null, null));
      myContentPanel.add(myLearnMoreLink, new GridConstraints(7, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                                              GridConstraints.SIZEPOLICY_FIXED,
                                                              null, null, null));
    }
  }

  private boolean shouldDisableDebuggable() {
    return myWizard.getTargetType() == BUNDLE;
  }

  private class DisabledItemSelectionModel extends DefaultListSelectionModel {
    private boolean isEnabled(int index) {
      return !disabledItems.contains(myBuildVariantsListModel.get(index).name);
    }

    @Override
    public void setSelectionInterval(int index0, int index1) {
      if (shouldDisableDebuggable()) {
        for (int i = index0; i <= index1; i++) {
          if (isEnabled(i)) {
            // call default selection behavior to actually enable row i for selection
            super.setSelectionInterval(i, i);
          }
        }
      }
      else {
        super.setSelectionInterval(index0, index1);
      }
    }
  }

  private void updateAdiStatus() {
    String appId = null;
    List<VariantItem> selectedValues = myBuildVariantsList.getSelectedValuesList();
    if (selectedValues.size() == 1) {
      VariantItem selectedVariant = selectedValues.getFirst();
      if (myAndroidModel != null) {
        if (selectedVariant != null) {
          appId = getAppId(selectedVariant.name);
        }
      }

      myAdiStatusIcon.setIcon(EmptyIcon.ICON_16);
      myAdiSelectedAppId.setText(appId + " (" + selectedVariant.name + ")");

      myAdiStatusIcon.setIcon(selectedVariant.icon);
      myAdiStatus.setText(AndroidBundle.message("android.apk.sign.gradle.adi.checking.status"));
      myAdiStatusDescription.setText(" ");
      myLearnMoreLink.setVisible(false);
      if (appId != null) {
        byte[] cert;
        try {
          cert =  myWizard.getTargetType() == BUNDLE ? null : myWizard.getCertificate().getEncoded();
        }
        catch (CertificateEncodingException e) {
          throw new RuntimeException(e);
        }
        
        final String currentAppId = appId;
        myCurrentAdiCheck = myAdiClient.checkPackageRegistrationStatusAsync(Collections.singleton(currentAppId), cert);
        myCurrentAdiCheck.thenAccept(result -> ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), () -> {
          var state = result.getFirst().get(currentAppId);
            if (myCurrentAdiCheck != null && !myCurrentAdiCheck.isCancelled() && currentAppId.equals(getAppId(selectedVariant.name))) {
              if (state == RegistrationState.UNKNOWN) {
                myAdiStatusIcon.setIcon(AllIcons.General.Note);
                myAdiStatus.setText(AndroidBundle.message("android.apk.sign.gradle.adi.check.failed"));
                myAdiStatusDescription.setText(" ");
                myLearnMoreLink.setVisible(false);
              }
              else if (state == RegistrationState.REGISTERED) {
                String text;
                String description;
                if (myWizard.getTargetType() == BUNDLE) {
                  text = AndroidBundle.message("android.apk.sign.gradle.adi.bundle.registered.title");
                  description = AndroidBundle.message("android.apk.sign.gradle.adi.bundle.registered.description");
                } else {
                  text = AndroidBundle.message("android.apk.sign.gradle.adi.apk.registered.title");
                  description = AndroidBundle.message("android.apk.sign.gradle.adi.apk.registered.description");
                }
                myAdiStatusIcon.setIcon(StudioIcons.Common.SUCCESS_INLINE);
                myAdiStatus.setText("<html>" + text + "</html>");
                myAdiStatusDescription.setText("<html>" + description + "</html>");
                myLearnMoreLink.setVisible(true);
              }
              else if (state == RegistrationState.BAD_KEY) {
                myAdiStatusIcon.setIcon(AllIcons.General.Note);
                myAdiStatus.setText("<html>" + AndroidBundle.message("android.apk.sign.gradle.adi.bad.key.title") + "</html>");
                myAdiStatusDescription.setText("<html>" + AndroidBundle.message("android.apk.sign.gradle.adi.bad.key.description") + "</html>");
                myLearnMoreLink.setVisible(true);
              }
              else if (state == RegistrationState.STUDIO_VERSION_UNSUPPORTED) {
                myAdiStatusIcon.setIcon(AllIcons.General.Note);
                myAdiStatus.setText("<html>" + result.getSecond().getHeader() + "</html>");
                myAdiStatusDescription.setText("<html>" + result.getSecond().getDescription() + "</html>");
                myLearnMoreLink.setVisible(false);
              }
              else {
                myAdiStatusIcon.setIcon(AllIcons.General.Note);
                myAdiStatus.setText("<html>" + AndroidBundle.message("android.apk.sign.gradle.adi.not.registered.title") + "</html>");
                String text;
                if (StudioFlags.SIGNED_BUILD_ADV_ENFORCEMENT_STARTED.get()) {
                  text = AndroidBundle.message("android.apk.sign.gradle.adi.not.eligible");
                } else {
                  text = AndroidBundle.message("android.apk.sign.gradle.adi.soon.not.eligible");
                }
                myAdiStatusDescription.setText("<html>" + text + "</html>");
                myLearnMoreLink.setVisible(true);
              }
            }
          }));
      }
    }
    else if (selectedValues.isEmpty()) {
      myAdiSelectedAppId.setText(AndroidBundle.message("android.apk.sign.gradle.adi.none.selected"));
      myAdiStatusIcon.setIcon(AllIcons.General.Note);
      myAdiStatus.setText("<html>" + AndroidBundle.message("android.apk.sign.gradle.adi.select.variant") + "</html>");
      myAdiStatusDescription.setText(" ");
      myLearnMoreLink.setVisible(false);
    }
    else {
      myAdiSelectedAppId.setText(AndroidBundle.message("android.apk.sign.gradle.adi.multiple.selected"));
      myAdiStatusIcon.setIcon(AllIcons.General.Note);
      myAdiStatus.setText("<html>" + AndroidBundle.message("android.apk.sign.gradle.adi.select.one") + "</html>");
      myAdiStatusDescription.setText(" ");
      myLearnMoreLink.setVisible(false);
    }
  }

  @Nullable
  private String getAppId(@NotNull String selectedVariant) {
    @Nullable IdeBasicVariant variant = myAndroidModel.findBasicVariantByName(selectedVariant);
    if (variant != null) {
      return variant.getApplicationId();
    }
    return null;
  }

  private class DisabledItemListCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      VariantItem item = (VariantItem)value;
      // calling parent getListCellRendererComponent set all proper attributes to item renderer
      DefaultListCellRenderer c = (DefaultListCellRenderer)super.getListCellRendererComponent(list, item.name, index, isSelected, cellHasFocus);
      c.setIconTextGap(JBUI.scale(10));
      c.setBorder(JBUI.Borders.empty(2));
      if (shouldDisableDebuggable() && disabledItems.contains(myBuildVariantsListModel.get(index).name)) {
        c.setForeground(DISABLED_TEXT_COLOR);
        c.setIcon(EmptyIcon.ICON_16);
      }
      else if (item.icon != null) {
        c.setForeground(TEXT_COLOR);
        c.setIcon(item.icon);
      }
      return c;
    }
  }
}
    