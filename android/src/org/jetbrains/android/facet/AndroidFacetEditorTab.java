/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.facet;

import com.android.SdkConstants;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.ModuleSourceAutogenerating;
import org.jetbrains.android.compiler.artifact.ProGuardConfigFilesPanel;
import org.jetbrains.android.maven.AndroidMavenProvider;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.AndroidImportableProperty;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;

/**
 * @author yole
 */
public class AndroidFacetEditorTab extends FacetEditorTab {
  private final AndroidFacetConfiguration myConfiguration;
  private final FacetEditorContext myContext;
  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myRGenPathField;
  private TextFieldWithBrowseButton myAidlGenPathField;
  private JButton myResetPathsButton;
  private TextFieldWithBrowseButton myResFolderField;
  private TextFieldWithBrowseButton myAssetsFolderField;
  private TextFieldWithBrowseButton myNativeLibsFolder;
  private TextFieldWithBrowseButton myManifestFileField;
  private JRadioButton myUseAptResDirectoryFromPathRadio;
  private JRadioButton myUseCustomSourceDirectoryRadio;
  private TextFieldWithBrowseButton myCustomAptSourceDirField;
  private JCheckBox myIsLibraryProjectCheckbox;
  private JPanel myAaptCompilerPanel;
  private ComboboxWithBrowseButton myApkPathCombo;
  private JLabel myApkPathLabel;
  private JRadioButton myRunProcessResourcesRadio;
  private JRadioButton myCompileResourcesByIdeRadio;
  private JLabel myManifestFileLabel;
  private JLabel myResFolderLabel;
  private JLabel myAssetsFolderLabel;
  private JLabel myNativeLibsFolderLabel;
  private JLabel myAidlGenPathLabel;
  private JLabel myRGenPathLabel;
  private TextFieldWithBrowseButton myCustomDebugKeystoreField;
  private JBLabel myCustomKeystoreLabel;
  private JCheckBox myIncludeTestCodeAndCheckBox;
  private JBCheckBox myRunProguardCheckBox;
  private JBCheckBox myIncludeAssetsFromLibraries;
  private JBCheckBox myUseCustomManifestPackage;
  private JTextField myCustomManifestPackageField;
  private ComboBox myUpdateProjectPropertiesCombo;
  private CheckBoxList<AndroidImportableProperty> myImportedOptionsList;
  private JBTabbedPane myTabbedPane;
  private JBCheckBox myEnableManifestMerging;
  private JBCheckBox myPreDexEnabledCheckBox;
  private ProGuardConfigFilesPanel myProGuardConfigFilesPanel;
  private JBCheckBox myEnableSourcesAutogenerationCheckBox;
  private JPanel myAptAutogenerationOptionsPanel;
  private JPanel myAidlAutogenerationOptionsPanel;
  private RawCommandLineEditor myAdditionalPackagingCommandLineParametersField;
  private TextFieldWithBrowseButton myProguardLogsDirectoryField;
  private JBLabel myProGuardLogsDirectoryLabel;
  private JBCheckBox myEnableMultiDexCheckBox;
  private JBTextField myMainDexList;
  private JCheckBox myMinimalMainDexCheckBox;

  private static final String MAVEN_TAB_TITLE = "Maven";
  private final Component myMavenTabComponent;

  public AndroidFacetEditorTab(FacetEditorContext context, AndroidFacetConfiguration androidFacetConfiguration) {
    final Project project = context.getProject();
    myConfiguration = androidFacetConfiguration;
    myContext = context;

    myManifestFileLabel.setLabelFor(myManifestFileField);
    myResFolderLabel.setLabelFor(myResFolderField);
    myAssetsFolderLabel.setLabelFor(myAssetsFolderField);
    myNativeLibsFolderLabel.setLabelFor(myNativeLibsFolder);
    myAidlGenPathLabel.setLabelFor(myAidlGenPathField);
    myRGenPathLabel.setLabelFor(myRGenPathField);
    myCustomKeystoreLabel.setLabelFor(myCustomDebugKeystoreField);

    final AndroidFacet facet = (AndroidFacet)myContext.getFacet();

    myRGenPathField.getButton()
      .addActionListener(new MyGenSourceFieldListener(myRGenPathField, AndroidRootUtil.getAptGenSourceRootPath(facet)));
    myAidlGenPathField.getButton()
      .addActionListener(new MyGenSourceFieldListener(myAidlGenPathField, AndroidRootUtil.getAidlGenSourceRootPath(facet)));

    Module module = myContext.getModule();
    
    myManifestFileField.getButton().addActionListener(
      new MyFolderFieldListener(myManifestFileField, AndroidRootUtil.getManifestFile(facet), true, new MyManifestFilter()));
    
    myResFolderField.getButton().addActionListener(new MyFolderFieldListener(myResFolderField,
                                                                             AndroidRootUtil.getResourceDir(facet), false, null));
    
    myAssetsFolderField.getButton().addActionListener(new MyFolderFieldListener(myAssetsFolderField,
                                                                                AndroidRootUtil.getAssetsDir(facet), false, null));
    
    myNativeLibsFolder.getButton().addActionListener(new MyFolderFieldListener(myNativeLibsFolder,
                                                                               AndroidRootUtil.getLibsDir(facet), false, null));

    myCustomAptSourceDirField.getButton().addActionListener(new MyFolderFieldListener(myCustomAptSourceDirField, getCustomResourceDirForApt(facet), false, null));

    myRunProguardCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myProGuardConfigFilesPanel.setEnabled(myRunProguardCheckBox.isSelected());
      }
    });
    
    myCustomDebugKeystoreField.getButton().addActionListener(new MyFolderFieldListener(myCustomDebugKeystoreField, null, true, null));

    myResetPathsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        AndroidFacetConfiguration configuration = new AndroidFacetConfiguration();
        configuration.setFacet((AndroidFacet)myContext.getFacet());
        Module module = myContext.getModule();
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
        if (contentRoots.length == 1) {
          configuration.init(module, contentRoots[0]);
        }
        if (AndroidMavenUtil.isMavenizedModule(module)) {
          AndroidMavenProvider mavenProvider = AndroidMavenUtil.getMavenProvider();
          if (mavenProvider != null) {
            mavenProvider.setPathsToDefault(module, configuration);
          }
        }
        resetOptions(configuration);
      }
    });

    myEnableSourcesAutogenerationCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateAutogenerationPanels();
      }
    });
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCustomAptSourceDirField.setEnabled(myUseCustomSourceDirectoryRadio.isSelected());
      }
    };
    myUseCustomSourceDirectoryRadio.addActionListener(listener);
    myUseAptResDirectoryFromPathRadio.addActionListener(listener);

    myIsLibraryProjectCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateLibAndAppSpecificFields();
      }
    });

    listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateAptPanel();
      }
    };
    myRunProcessResourcesRadio.addActionListener(listener);
    myCompileResourcesByIdeRadio.addActionListener(listener);

    myApkPathLabel.setLabelFor(myApkPathCombo);

    final JComboBox apkPathComboBoxComponent = myApkPathCombo.getComboBox();
    apkPathComboBoxComponent.setEditable(true);
    apkPathComboBoxComponent.setModel(new DefaultComboBoxModel(getDefaultApks(module)));
    apkPathComboBoxComponent.setMinimumSize(new Dimension(JBUI.scale(10), apkPathComboBoxComponent.getMinimumSize().height));
    apkPathComboBoxComponent.setPreferredSize(new Dimension(JBUI.scale(10), apkPathComboBoxComponent.getPreferredSize().height));

    myApkPathCombo.addBrowseFolderListener(project, new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) {
          return false;
        }
        return file.isDirectory() || "apk".equals(file.getExtension());
      }
    });

    myUseCustomManifestPackage.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCustomManifestPackageField.setEnabled(myUseCustomManifestPackage.isSelected());
      }
    });

    myUpdateProjectPropertiesCombo.setModel(new DefaultComboBoxModel(new Object[]{"", Boolean.TRUE.toString(), Boolean.FALSE.toString()}));
    myUpdateProjectPropertiesCombo.setRenderer(new ListCellRendererWrapper<String>() {
      @Override
      public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        if (value != null && value.isEmpty()) {
          setText("Ask");
        }
        else if (Boolean.parseBoolean(value)) {
          setText("Yes");
        }
        else {
          setText("No");
        }
      }
    });
    buildImportedOptionsList();

    final int mavenTabIndex = myTabbedPane.indexOfTab(MAVEN_TAB_TITLE);
    assert mavenTabIndex >= 0;
    myMavenTabComponent = myTabbedPane.getComponentAt(mavenTabIndex);

    myProguardLogsDirectoryField.getButton().addActionListener(new MyFolderFieldListener(myProguardLogsDirectoryField, null, false, null));
  }

  @Nullable
  public static VirtualFile getCustomResourceDirForApt(@NotNull AndroidFacet facet) {
    return AndroidRootUtil.getFileByRelativeModulePath(facet.getModule(), facet.getProperties().CUSTOM_APK_RESOURCE_FOLDER, false);
  }

  private void updateLibAndAppSpecificFields() {
    boolean lib = myIsLibraryProjectCheckbox.isSelected();
    myAssetsFolderField.setEnabled(!lib);
    myEnableManifestMerging.setEnabled(!lib);
    myIncludeAssetsFromLibraries.setEnabled(!lib);
    myUseCustomManifestPackage.setEnabled(!lib);
    myCustomManifestPackageField.setEnabled(!lib && myUseCustomManifestPackage.isSelected());
    myAdditionalPackagingCommandLineParametersField.setEnabled(!lib);
    myRunProguardCheckBox.setEnabled(!lib);
    myProGuardConfigFilesPanel.setEnabled(!lib && myRunProguardCheckBox.isSelected());
    myApkPathLabel.setEnabled(!lib);
    myApkPathCombo.setEnabled(!lib);
    myCustomKeystoreLabel.setEnabled(!lib);
    myCustomDebugKeystoreField.setEnabled(!lib);
    myPreDexEnabledCheckBox.setEnabled(!lib);
    myProGuardLogsDirectoryLabel.setEnabled(!lib);
    myProguardLogsDirectoryField.setEnabled(!lib);
  }

  private void updateAutogenerationPanels() {
    UIUtil.setEnabled(myAidlAutogenerationOptionsPanel, myEnableSourcesAutogenerationCheckBox.isSelected(), true);
    updateAptPanel();
  }

  private void buildImportedOptionsList() {
    myImportedOptionsList.setItems(Arrays.asList(AndroidImportableProperty.values()), new Function<AndroidImportableProperty, String>() {
      @Override
      public String fun(AndroidImportableProperty property) {
        return property.getDisplayName();
      }
    });
  }

  private void updateAptPanel() {
    if (!myEnableSourcesAutogenerationCheckBox.isSelected()) {
      UIUtil.setEnabled(myAptAutogenerationOptionsPanel, false, true);
    }
    else {
      UIUtil.setEnabled(myAptAutogenerationOptionsPanel, true, true);
      boolean enabled = !myRunProcessResourcesRadio.isVisible() || !myRunProcessResourcesRadio.isSelected();
      UIUtil.setEnabled(myAaptCompilerPanel, enabled, true);
    }
  }

  private static String[] getDefaultApks(@NotNull Module module) {
    List<String> result = new ArrayList<String>();
    String path = AndroidCompileUtil.getOutputPackage(module);
    if (path != null) {
      result.add(path);
    }
    AndroidMavenProvider mavenProvider = AndroidMavenUtil.getMavenProvider();
    if (mavenProvider != null && mavenProvider.isMavenizedModule(module)) {
      String buildDirectory = mavenProvider.getBuildDirectory(module);
      if (buildDirectory != null) {
        result.add(FileUtil.toSystemDependentName(buildDirectory + '/' + AndroidCompileUtil.getApkName(module)));
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Android SDK Settings";
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    //if (myAddAndroidLibrary.isSelected() != myConfiguration.ADD_ANDROID_LIBRARY) return true;
    if (myIsLibraryProjectCheckbox.isSelected() != (myConfiguration.getState().PROJECT_TYPE == PROJECT_TYPE_LIBRARY)) return true;

    if (checkRelativePath(myConfiguration.getState().GEN_FOLDER_RELATIVE_PATH_APT, myRGenPathField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.getState().GEN_FOLDER_RELATIVE_PATH_AIDL, myAidlGenPathField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.getState().MANIFEST_FILE_RELATIVE_PATH, myManifestFileField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.getState().RES_FOLDER_RELATIVE_PATH, myResFolderField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.getState().ASSETS_FOLDER_RELATIVE_PATH, myAssetsFolderField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.getState().LIBS_FOLDER_RELATIVE_PATH, myNativeLibsFolder.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.getState().APK_PATH, (String)myApkPathCombo.getComboBox().getEditor().getItem())) {
      return true;
    }

    if (myUseCustomSourceDirectoryRadio.isSelected() != myConfiguration.getState().USE_CUSTOM_APK_RESOURCE_FOLDER) {
      return true;
    }
    if (checkRelativePath(myConfiguration.getState().CUSTOM_APK_RESOURCE_FOLDER, myCustomAptSourceDirField.getText())) {
      return true;
    }

    if (myRunProcessResourcesRadio.isSelected() != myConfiguration.getState().RUN_PROCESS_RESOURCES_MAVEN_TASK) {
      return true;
    }
    if (!myConfiguration.getState().CUSTOM_DEBUG_KEYSTORE_PATH.equals(getSelectedCustomKeystorePath())) {
      return true;
    }
    if (myConfiguration.getState().ENABLE_MANIFEST_MERGING != myEnableManifestMerging.isSelected()) {
      return true;
    }
    if (myConfiguration.getState().ENABLE_PRE_DEXING != myPreDexEnabledCheckBox.isSelected()) {
      return true;
    }
    if (myConfiguration.getState().PACK_TEST_CODE != myIncludeTestCodeAndCheckBox.isSelected()) {
      return true;
    }
    if (myConfiguration.getState().ENABLE_SOURCES_AUTOGENERATION != myEnableSourcesAutogenerationCheckBox.isSelected()) {
      return true;
    }
    if (myConfiguration.isIncludeAssetsFromLibraries() != myIncludeAssetsFromLibraries.isSelected()) {
      return true;
    }
    if (myConfiguration.getState().RUN_PROGUARD != myRunProguardCheckBox.isSelected()) {
      return true;
    }
    if (!myProGuardConfigFilesPanel.getUrls().equals(myConfiguration.getState().myProGuardCfgFiles)) {
      return true;
    }
    if (myConfiguration.getState().ENABLE_MULTI_DEX != myEnableMultiDexCheckBox.isSelected()) {
      return true;
    }
    if (myConfiguration.getState().MINIMAL_MAIN_DEX != myMinimalMainDexCheckBox.isSelected()) {
      return true;
    }
    if (!myMainDexList.getText().trim().equals(myConfiguration.getState().MAIN_DEX_LIST)) {
      return true;
    }
    if (myConfiguration.getState().USE_CUSTOM_MANIFEST_PACKAGE != myUseCustomManifestPackage.isSelected()) {
      return true;
    }
    if (!myCustomManifestPackageField.getText().trim().equals(myConfiguration.getState().CUSTOM_MANIFEST_PACKAGE)) {
      return true;
    }
    if (!myAdditionalPackagingCommandLineParametersField.getText().trim().equals(
      myConfiguration.getState().ADDITIONAL_PACKAGING_COMMAND_LINE_PARAMETERS)) {
      return true;
    }
    if (!myUpdateProjectPropertiesCombo.getSelectedItem().equals(myConfiguration.getState().UPDATE_PROPERTY_FILES)) {
      return true;
    }
    if (checkRelativePath(myConfiguration.getState().PROGUARD_LOGS_FOLDER_RELATIVE_PATH, myProguardLogsDirectoryField.getText())) {
      return true;
    }
    if (AndroidMavenUtil.isMavenizedModule(myContext.getModule())) {
      final Set<AndroidImportableProperty> newNotImportedProperties = EnumSet.noneOf(AndroidImportableProperty.class);

      for (int i = 0; i < myImportedOptionsList.getItemsCount(); i++) {
        final AndroidImportableProperty property = (AndroidImportableProperty)myImportedOptionsList.getItemAt(i);

        if (!myImportedOptionsList.isItemSelected(i)) {
          newNotImportedProperties.add(property);
        }
      }

      if (!myConfiguration.getState().myNotImportedProperties.equals(newNotImportedProperties)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private String getSelectedCustomKeystorePath() {
    final String path = myCustomDebugKeystoreField.getText().trim();
    return !path.isEmpty() ? VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(path)) : "";
  }

  private boolean checkRelativePath(String relativePathFromConfig, String absPathFromTextField) {
    String pathFromConfig = relativePathFromConfig;
    if (pathFromConfig != null && !pathFromConfig.isEmpty()) {
      pathFromConfig = toAbsolutePath(pathFromConfig);
    }
    String pathFromTextField = absPathFromTextField.trim();
    return !FileUtil.pathsEqual(pathFromConfig, pathFromTextField);
  }

  @Nullable
  private String toRelativePath(String absPath) {
    absPath = FileUtil.toSystemIndependentName(absPath);
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(myContext.getModule());
    if (moduleDirPath != null) {
      moduleDirPath = FileUtil.toSystemIndependentName(moduleDirPath);
      return FileUtil.getRelativePath(moduleDirPath, absPath, '/');
    }
    return null;
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.project.modules.android.facet";
  }

  @Override
  public void apply() throws ConfigurationException {
    if (!isModified()) return;
    String absGenPathR = myRGenPathField.getText().trim();
    String absGenPathAidl = myAidlGenPathField.getText().trim();

    boolean runApt = false;
    boolean runIdl = false;

    if (absGenPathR == null || absGenPathR.isEmpty() || absGenPathAidl == null || absGenPathAidl.isEmpty()) {
      throw new ConfigurationException("Please specify source root for autogenerated files");
    }
    else {
      String relativeGenPathR = getAndCheckRelativePath(absGenPathR, false);
      String newAptDestDir = '/' + relativeGenPathR;
      if (!newAptDestDir.equals(myConfiguration.getState().GEN_FOLDER_RELATIVE_PATH_APT)) {
        runApt = true;
      }
      myConfiguration.getState().GEN_FOLDER_RELATIVE_PATH_APT = newAptDestDir;

      String relativeGenPathAidl = getAndCheckRelativePath(absGenPathAidl, false);
      String newIdlDestDir = '/' + relativeGenPathAidl;
      if (!newIdlDestDir.equals(myConfiguration.getState().GEN_FOLDER_RELATIVE_PATH_AIDL)) {
        runIdl = true;
      }
      myConfiguration.getState().GEN_FOLDER_RELATIVE_PATH_AIDL = newIdlDestDir;
    }

    String absManifestPath = myManifestFileField.getText().trim();
    if (absManifestPath.isEmpty()) {
      throw new ConfigurationException("Manifest file not specified");
    }
    String manifestRelPath = getAndCheckRelativePath(absManifestPath, true);
    if (!SdkConstants.FN_ANDROID_MANIFEST_XML.equals(AndroidUtils.getSimpleNameByRelativePath(manifestRelPath))) {
      throw new ConfigurationException("Manifest file must have name AndroidManifest.xml");
    }
    myConfiguration.getState().MANIFEST_FILE_RELATIVE_PATH = '/' + manifestRelPath;

    String absResPath = myResFolderField.getText().trim();
    if (absResPath.isEmpty()) {
      throw new ConfigurationException("Resources folder not specified");
    }
    myConfiguration.getState().RES_FOLDER_RELATIVE_PATH = '/' + getAndCheckRelativePath(absResPath, false);

    String absAssetsPath = myAssetsFolderField.getText().trim();
    myConfiguration.getState().ASSETS_FOLDER_RELATIVE_PATH =
      !absAssetsPath.isEmpty() ? '/' + getAndCheckRelativePath(absAssetsPath, false) : "";

    String absApkPath = (String)myApkPathCombo.getComboBox().getEditor().getItem();
    if (absApkPath.isEmpty()) {
      myConfiguration.getState().APK_PATH = "";
    }
    else {
      myConfiguration.getState().APK_PATH = '/' + getAndCheckRelativePath(absApkPath, false);
    }

    String absLibsPath = myNativeLibsFolder.getText().trim();
    myConfiguration.getState().LIBS_FOLDER_RELATIVE_PATH = !absLibsPath.isEmpty() ? '/' + getAndCheckRelativePath(absLibsPath, false) : "";

    myConfiguration.getState().CUSTOM_DEBUG_KEYSTORE_PATH = getSelectedCustomKeystorePath();

    myConfiguration.getState().PROJECT_TYPE = myIsLibraryProjectCheckbox.isSelected() ? PROJECT_TYPE_LIBRARY : PROJECT_TYPE_APP;

    myConfiguration.getState().RUN_PROCESS_RESOURCES_MAVEN_TASK = myRunProcessResourcesRadio.isSelected();

    myConfiguration.getState().ENABLE_MANIFEST_MERGING = myEnableManifestMerging.isSelected();

    myConfiguration.getState().ENABLE_PRE_DEXING = myPreDexEnabledCheckBox.isSelected();

    myConfiguration.getState().ENABLE_MULTI_DEX = myEnableMultiDexCheckBox.isSelected();
    myConfiguration.getState().MAIN_DEX_LIST = myMainDexList.getText().trim();
    myConfiguration.getState().MINIMAL_MAIN_DEX = myMinimalMainDexCheckBox.isSelected();

    myConfiguration.getState().PACK_TEST_CODE = myIncludeTestCodeAndCheckBox.isSelected();

    myConfiguration.getState().ENABLE_SOURCES_AUTOGENERATION = myEnableSourcesAutogenerationCheckBox.isSelected();

    myConfiguration.setIncludeAssetsFromLibraries(myIncludeAssetsFromLibraries.isSelected());

    if (AndroidMavenUtil.isMavenizedModule(myContext.getModule())) {
      final Set<AndroidImportableProperty> notImportedProperties = myConfiguration.getState().myNotImportedProperties;
      notImportedProperties.clear();

      for (int i = 0; i < myImportedOptionsList.getItemsCount(); i++) {
        final AndroidImportableProperty property = (AndroidImportableProperty)myImportedOptionsList.getItemAt(i);

        if (!myImportedOptionsList.isItemSelected(i)) {
          notImportedProperties.add(property);
        }
      }
    }
    myConfiguration.getState().RUN_PROGUARD = myRunProguardCheckBox.isSelected();
    myConfiguration.getState().myProGuardCfgFiles = myProGuardConfigFilesPanel.getUrls();

    boolean useCustomAptSrc = myUseCustomSourceDirectoryRadio.isSelected();

    myConfiguration.getState().USE_CUSTOM_APK_RESOURCE_FOLDER = useCustomAptSrc;

    myConfiguration.getState().USE_CUSTOM_MANIFEST_PACKAGE = myUseCustomManifestPackage.isSelected();
    myConfiguration.getState().CUSTOM_MANIFEST_PACKAGE = myCustomManifestPackageField.getText().trim();
    myConfiguration.getState().ADDITIONAL_PACKAGING_COMMAND_LINE_PARAMETERS = myAdditionalPackagingCommandLineParametersField.getText().trim();

    String absAptSourcePath = myCustomAptSourceDirField.getText().trim();
    if (useCustomAptSrc) {
      if (absAptSourcePath.isEmpty()) {
        throw new ConfigurationException("Resources folder not specified");
      }
      myConfiguration.getState().CUSTOM_APK_RESOURCE_FOLDER = '/' + getAndCheckRelativePath(absAptSourcePath, false);
    }
    else {
      String relPath = toRelativePath(absAptSourcePath);
      myConfiguration.getState().CUSTOM_APK_RESOURCE_FOLDER = relPath != null ? '/' + relPath : "";
    }
    myConfiguration.getState().UPDATE_PROPERTY_FILES = (String)myUpdateProjectPropertiesCombo.getSelectedItem();

    String absProguardLogsPath = myProguardLogsDirectoryField.getText().trim();
    myConfiguration.getState().PROGUARD_LOGS_FOLDER_RELATIVE_PATH =
      !absProguardLogsPath.isEmpty() ? '/' + getAndCheckRelativePath(absProguardLogsPath, false) : "";

    if (runApt || runIdl) {
      AndroidFacet facet = (AndroidFacet)myContext.getFacet();
      ModuleSourceAutogenerating sourceAutoGenerator = ModuleSourceAutogenerating.getInstance(facet);

      if (sourceAutoGenerator != null) {
        if (runApt) {
          sourceAutoGenerator.scheduleSourceRegenerating(AndroidAutogeneratorMode.AAPT);
        }
        if (runIdl) {
          sourceAutoGenerator.scheduleSourceRegenerating(AndroidAutogeneratorMode.AIDL);
        }
      }
    }
  }

  private String getAndCheckRelativePath(String absPath, boolean checkExists) throws ConfigurationException {
    if (absPath.indexOf('/') < 0 && absPath.indexOf(File.separatorChar) < 0) {
      throw new ConfigurationException(AndroidBundle.message("file.must.be.under.module.error", FileUtil.toSystemDependentName(absPath)));
    }
    String relativeGenPathR = toRelativePath(absPath);
    if (relativeGenPathR == null || relativeGenPathR.isEmpty()) {
      throw new ConfigurationException(AndroidBundle.message("file.must.be.under.module.error", FileUtil.toSystemDependentName(absPath)));
    }
    if (checkExists && LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(absPath)) == null) {
      throw new ConfigurationException(AndroidBundle.message("android.file.not.exist.error", FileUtil.toSystemDependentName(absPath)));
    }
    return relativeGenPathR;
  }

  @Override
  public void reset() {
    myIsLibraryProjectCheckbox.setSelected(myConfiguration.getState().PROJECT_TYPE == PROJECT_TYPE_LIBRARY);
    resetOptions(myConfiguration);
  }

  private void resetOptions(AndroidFacetConfiguration configuration) {
    String aptGenPath = configuration.getState().GEN_FOLDER_RELATIVE_PATH_APT;
    String aptAbspath = !aptGenPath.isEmpty() ? toAbsolutePath(aptGenPath) : "";
    myRGenPathField.setText(aptAbspath != null ? aptAbspath : "");

    String aidlGenPath = configuration.getState().GEN_FOLDER_RELATIVE_PATH_AIDL;
    String aidlAbsPath = !aidlGenPath.isEmpty() ? toAbsolutePath(aidlGenPath) : "";
    myAidlGenPathField.setText(aidlAbsPath != null ? aidlAbsPath : "");

    String manifestPath = configuration.getState().MANIFEST_FILE_RELATIVE_PATH;
    String manifestAbsPath = !manifestPath.isEmpty() ? toAbsolutePath(manifestPath) : "";
    myManifestFileField.setText(manifestAbsPath != null ? manifestAbsPath : "");

    String resPath = configuration.getState().RES_FOLDER_RELATIVE_PATH;
    String resAbsPath = !resPath.isEmpty() ? toAbsolutePath(resPath) : "";
    myResFolderField.setText(resAbsPath != null ? resAbsPath : "");

    String assetsPath = configuration.getState().ASSETS_FOLDER_RELATIVE_PATH;
    String assetsAbsPath = !assetsPath.isEmpty() ? toAbsolutePath(assetsPath) : "";
    myAssetsFolderField.setText(assetsAbsPath != null ? assetsAbsPath : "");

    String libsPath = configuration.getState().LIBS_FOLDER_RELATIVE_PATH;
    String libsAbsPath = !libsPath.isEmpty() ? toAbsolutePath(libsPath) : "";
    myNativeLibsFolder.setText(libsAbsPath != null ? libsAbsPath : "");

    myCustomDebugKeystoreField.setText(FileUtil.toSystemDependentName(
      VfsUtil.urlToPath(configuration.getState().CUSTOM_DEBUG_KEYSTORE_PATH)));

    final boolean runProguard = configuration.getState().RUN_PROGUARD;
    myRunProguardCheckBox.setSelected(runProguard);
    myProGuardConfigFilesPanel.setUrls(configuration.getState().myProGuardCfgFiles);

    myEnableMultiDexCheckBox.setSelected(configuration.getState().ENABLE_MULTI_DEX);
    myMainDexList.setText(configuration.getState().MAIN_DEX_LIST);
    myMinimalMainDexCheckBox.setSelected(configuration.getState().MINIMAL_MAIN_DEX);

    myUseCustomSourceDirectoryRadio.setSelected(configuration.getState().USE_CUSTOM_APK_RESOURCE_FOLDER);
    myUseAptResDirectoryFromPathRadio.setSelected(!configuration.getState().USE_CUSTOM_APK_RESOURCE_FOLDER);

    String aptSourcePath = configuration.getState().CUSTOM_APK_RESOURCE_FOLDER;
    String aptSourceAbsPath = !aptSourcePath.isEmpty() ? toAbsolutePath(aptSourcePath) : "";
    myCustomAptSourceDirField.setText(aptSourceAbsPath != null ? aptSourceAbsPath : "");
    myCustomAptSourceDirField.setEnabled(configuration.getState().USE_CUSTOM_APK_RESOURCE_FOLDER);

    String apkPath = configuration.getState().APK_PATH;
    String apkAbsPath = !apkPath.isEmpty() ? toAbsolutePath(apkPath) : "";
    myApkPathCombo.getComboBox().getEditor().setItem(apkAbsPath != null ? apkAbsPath : "");

    boolean mavenizedModule = AndroidMavenUtil.isMavenizedModule(myContext.getModule());
    myRunProcessResourcesRadio.setVisible(mavenizedModule);
    myRunProcessResourcesRadio.setSelected(myConfiguration.getState().RUN_PROCESS_RESOURCES_MAVEN_TASK);
    myCompileResourcesByIdeRadio.setVisible(mavenizedModule);
    myCompileResourcesByIdeRadio.setSelected(!myConfiguration.getState().RUN_PROCESS_RESOURCES_MAVEN_TASK);

    myEnableManifestMerging.setSelected(myConfiguration.getState().ENABLE_MANIFEST_MERGING);
    myPreDexEnabledCheckBox.setSelected(myConfiguration.getState().ENABLE_PRE_DEXING);
    myIncludeTestCodeAndCheckBox.setSelected(myConfiguration.getState().PACK_TEST_CODE);
    myIncludeAssetsFromLibraries.setSelected(myConfiguration.isIncludeAssetsFromLibraries());

    myUseCustomManifestPackage.setSelected(myConfiguration.getState().USE_CUSTOM_MANIFEST_PACKAGE);
    myCustomManifestPackageField.setEnabled(myConfiguration.getState().USE_CUSTOM_MANIFEST_PACKAGE);
    myCustomManifestPackageField.setText(myConfiguration.getState().CUSTOM_MANIFEST_PACKAGE);
    myAdditionalPackagingCommandLineParametersField.setText(myConfiguration.getState().ADDITIONAL_PACKAGING_COMMAND_LINE_PARAMETERS);

    String proguardLogsPath = configuration.getState().PROGUARD_LOGS_FOLDER_RELATIVE_PATH;
    String proguardLogsAbsPath = !proguardLogsPath.isEmpty() ? toAbsolutePath(proguardLogsPath) : "";
    myProguardLogsDirectoryField.setText(proguardLogsAbsPath != null ? proguardLogsAbsPath : "");

    myUpdateProjectPropertiesCombo.setSelectedItem(myConfiguration.getState().UPDATE_PROPERTY_FILES);
    myEnableSourcesAutogenerationCheckBox.setSelected(myConfiguration.getState().ENABLE_SOURCES_AUTOGENERATION);
    updateAutogenerationPanels();

    final int mavenTabIndex = myTabbedPane.indexOfTab(MAVEN_TAB_TITLE);

    if (mavenTabIndex >= 0) {
      myTabbedPane.removeTabAt(mavenTabIndex);
    }

    if (mavenizedModule) {
      myTabbedPane.insertTab(MAVEN_TAB_TITLE, null, myMavenTabComponent, null, 2);

      for (int i = 0; i < myImportedOptionsList.getItemsCount(); i++) {
        final AndroidImportableProperty property = (AndroidImportableProperty)myImportedOptionsList.getItemAt(i);
        myImportedOptionsList.setItemSelected(property, configuration.isImportedProperty(property));
      }
    }
    updateLibAndAppSpecificFields();
  }

  @Nullable
  private String toAbsolutePath(String genRelativePath) {
    if (genRelativePath == null) {
      return null;
    }
    if (genRelativePath.isEmpty()) {
      return "";
    }
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(myContext.getModule());
    if (moduleDirPath == null) return null;
    final String path = PathUtil.getCanonicalPath(new File(moduleDirPath, genRelativePath).getPath());
    return path != null ? PathUtil.getLocalPath(path) : null;
  }

  private void createUIComponents() {
    myProGuardConfigFilesPanel = new ProGuardConfigFilesPanel() {
      @Nullable
      @Override
      protected AndroidFacet getFacet() {
        return (AndroidFacet)myContext.getFacet();
      }
    };
  }

  private class MyGenSourceFieldListener implements ActionListener {
    private final TextFieldWithBrowseButton myTextField;
    private final String myDefaultPath;

    private MyGenSourceFieldListener(TextFieldWithBrowseButton textField, String defaultPath) {
      myTextField = textField;
      myDefaultPath = defaultPath;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      Module module = myContext.getModule();
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();

      if (contentRoots.length == 0) {
        return;
      }
      VirtualFile initialFile = null;
      String path = myTextField.getText().trim();
      if (path.isEmpty()) {
        path = myDefaultPath;
      }
      if (path != null) {
        initialFile = LocalFileSystem.getInstance().findFileByPath(path);
      }
      if (initialFile == null) {
        ModuleRootManager manager = ModuleRootManager.getInstance(module);
        VirtualFile[] sourceRoots = manager.getSourceRoots();
        if (sourceRoots.length > 0) {
          initialFile = sourceRoots[0];
        }
        else {
          initialFile = module.getModuleFile();
          if (initialFile == null) {
            String p = AndroidRootUtil.getModuleDirPath(myContext.getModule());
            if (p != null) {
              initialFile = LocalFileSystem.getInstance().findFileByPath(p);
            }
          }
        }
      }
      final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      descriptor.setRoots(contentRoots);
      VirtualFile file = FileChooser.chooseFile(descriptor, myContentPanel, myContext.getProject(), initialFile);
      if (file != null) {
        myTextField.setText(FileUtil.toSystemDependentName(file.getPath()));
      }
    }
  }

  private class MyFolderFieldListener implements ActionListener {
    private final TextFieldWithBrowseButton myTextField;
    private final VirtualFile myDefaultDir;
    private final boolean myChooseFile;
    private final Condition<VirtualFile> myFilter;

    public MyFolderFieldListener(TextFieldWithBrowseButton textField,
                                 VirtualFile defaultDir,
                                 boolean chooseFile,
                                 @Nullable Condition<VirtualFile> filter) {
      myTextField = textField;
      myDefaultDir = defaultDir;
      myChooseFile = chooseFile;
      myFilter = filter;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      VirtualFile initialFile = null;
      String path = myTextField.getText().trim();
      if (path.isEmpty()) {
        VirtualFile dir = myDefaultDir;
        path = dir != null ? dir.getPath() : null;
      }
      if (path != null) {
        initialFile = LocalFileSystem.getInstance().findFileByPath(path);
      }
      VirtualFile[] files = chooserDirsUnderModule(initialFile, myChooseFile, false, myFilter);
      if (files.length > 0) {
        assert files.length == 1;
        myTextField.setText(FileUtil.toSystemDependentName(files[0].getPath()));
      }
    }
  }

  private VirtualFile[] chooserDirsUnderModule(@Nullable VirtualFile initialFile,
                                               final boolean chooseFile,
                                               boolean chooseMultiple,
                                               @Nullable final Condition<VirtualFile> filter) {
    if (initialFile == null) {
      initialFile = myContext.getModule().getModuleFile();
    }
    if (initialFile == null) {
      String p = AndroidRootUtil.getModuleDirPath(myContext.getModule());
      if (p != null) {
        initialFile = LocalFileSystem.getInstance().findFileByPath(p);
      }
    }
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(chooseFile, !chooseFile, false, false, false, chooseMultiple) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) {
          return false;
        }

        if (!file.isDirectory() && !chooseFile) {
          return false;
        }

        return filter == null || filter.value(file);
      }
    };
    return FileChooser.chooseFiles(descriptor, myContentPanel, myContext.getProject(), initialFile);
  }
  
  private static class MyManifestFilter implements Condition<VirtualFile> {

    @Override
    public boolean value(VirtualFile file) {
      return file.isDirectory() || file.getName().equals(SdkConstants.FN_ANDROID_MANIFEST_XML);
    }
  }
}
