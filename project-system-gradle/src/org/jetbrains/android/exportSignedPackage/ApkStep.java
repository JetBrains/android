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

package org.jetbrains.android.exportSignedPackage;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.artifact.ProGuardConfigFilesPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidFacetProperties;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.SaveFileListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

class ApkStep extends ExportSignedPackageWizardStep {
  public static final String APK_PATH_PROPERTY = "ExportedApkPath";
  public static final String APK_PATH_PROPERTY_UNSIGNED = "ExportedUnsignedApkPath";
  public static final String RUN_PROGUARD_PROPERTY = "AndroidRunProguardForReleaseBuild";
  public static final String PROGUARD_CFG_PATHS_PROPERTY = "AndroidProguardConfigPaths";

  private TextFieldWithBrowseButton myApkPathField;
  private JPanel myContentPanel;
  private JLabel myApkPathLabel;
  private JCheckBox myProguardCheckBox;
  private ProGuardConfigFilesPanel myProGuardConfigFilesPanel;

  private final ExportSignedPackageWizard myWizard;
  private boolean myInited;
  private boolean myIsBundle;

  @Nullable
  private static String getContentRootPath(Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length != 0) {
      VirtualFile contentRoot = contentRoots[0];
      if (contentRoot != null) return contentRoot.getPath();
    }
    return null;
  }

  public ApkStep(ExportSignedPackageWizard wizard) {
    myWizard = wizard;
    myIsBundle = myWizard.getTargetType().equals(ExportSignedPackageWizard.BUNDLE);
    myApkPathLabel.setLabelFor(myApkPathField);

    myApkPathField.getButton().addActionListener(
      new SaveFileListener(myContentPanel, myApkPathField, AndroidBundle.message("android.extract.package.choose.dest.apk"), "apk") {
        @Override
        protected String getDefaultLocation() {
          Module module = myWizard.getFacet().getModule();
          return getContentRootPath(module);
        }
      });

    myProguardCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myProGuardConfigFilesPanel.setEnabled(myProguardCheckBox.isSelected());
      }
    });

    myContentPanel.setPreferredSize(new Dimension(myContentPanel.getPreferredSize().width, JBUI.scale(250)));
  }

  @Override
  public void _init() {
    if (myInited) return;
    final AndroidFacet facet = myWizard.getFacet();
    Module module = facet.getModule();

    PropertiesComponent properties = PropertiesComponent.getInstance(module.getProject());
    String initialApkPath = getInitialPath(properties, module);
    if (!isNullOrEmpty(initialApkPath)) {
      myApkPathField.setText(FileUtil.toSystemDependentName(initialApkPath));
    }
    final String runProguardPropValue = properties.getValue(RUN_PROGUARD_PROPERTY);
    boolean selected;

    if (runProguardPropValue != null) {
      selected = Boolean.parseBoolean(runProguardPropValue);
    }
    else {
      selected = facet.getProperties().RUN_PROGUARD;
    }
    myProguardCheckBox.setSelected(selected);
    myProGuardConfigFilesPanel.setEnabled(selected);

    final String proguardCfgPathsStr = properties.getValue(PROGUARD_CFG_PATHS_PROPERTY);
    final String[] proguardCfgPaths = proguardCfgPathsStr != null
                                      ? parseAndCheckProguardCfgPaths(proguardCfgPathsStr)
                                      : null;
    if (proguardCfgPaths != null && proguardCfgPaths.length > 0) {
      myProGuardConfigFilesPanel.setOsPaths(Arrays.asList(proguardCfgPaths));
    }
    else {
      final AndroidFacetConfiguration configuration = facet.getConfiguration();
      if (configuration.getState().RUN_PROGUARD) {
        myProGuardConfigFilesPanel.setUrls(facet.getProperties().myProGuardCfgFiles);
      }
      else {
        final List<String> urls = new ArrayList<String>();
        urls.add(AndroidFacetProperties.PROGUARD_SYSTEM_CFG_FILE_URL);
        final Pair<VirtualFile, Boolean> pair = AndroidCompileUtil.getDefaultProguardConfigFile(facet);

        if (pair != null) {
          urls.add(pair.getFirst().getUrl());
        }
        myProGuardConfigFilesPanel.setUrls(urls);
      }
    }

    myInited = true;
  }

  @NotNull
  private static String[] parseAndCheckProguardCfgPaths(@NotNull String pathsStr) {
    if (pathsStr.isEmpty()) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    final String[] paths = pathsStr.split(File.pathSeparator);

    if (paths.length == 0) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    for (String path : paths) {
      if (LocalFileSystem.getInstance().refreshAndFindFileByPath(path) == null) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
    }
    return paths;
  }

  @NotNull
  private static String mergeProguardCfgPathsToOneString(@NotNull Collection<String> paths) {
    final StringBuilder builder = new StringBuilder();

    for (Iterator<String> it = paths.iterator(); it.hasNext(); ) {
      builder.append(it.next());

      if (it.hasNext()) {
        builder.append(File.pathSeparator);
      }
    }
    return builder.toString();
  }

  @VisibleForTesting
  String getApkPathPropertyName(String moduleName) {
    return (myWizard.isSigned() ? APK_PATH_PROPERTY : APK_PATH_PROPERTY_UNSIGNED) + (isNullOrEmpty(moduleName) ? "" : "For" + moduleName);
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  @Override
  protected boolean canFinish() {
    return true;
  }

  @Override
  public String getHelpId() {
    return AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing";
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    final String apkPath = myApkPathField.getText().trim();
    if (apkPath.isEmpty()) {
      throw new CommitStepException(AndroidBundle.message("android.extract.package.specify.apk.path.error"));
    }

    AndroidFacet facet = myWizard.getFacet();
    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());
    @NotNull String moduleName = facet != null ? facet.getModule().getName() : "";
    properties.setValue(KeystoreStep.getModuleProperty(myIsBundle), moduleName);
    properties.setValue(getApkPathPropertyName(moduleName), apkPath);

    File folder = new File(apkPath).getParentFile();
    if (folder == null) {
      throw new CommitStepException(AndroidBundle.message("android.cannot.create.file.error", apkPath));
    }
    try {
      if (!folder.exists()) {
        folder.mkdirs();
      }
    }
    catch (Exception e) {
      throw new CommitStepException(e.getMessage());
    }

    final CompileScope compileScope = CompilerManager.getInstance(myWizard.getProject()).
      createModuleCompileScope(facet.getModule(), true);
    AndroidCompileUtil.setReleaseBuild(compileScope);

    properties.setValue(RUN_PROGUARD_PROPERTY, Boolean.toString(myProguardCheckBox.isSelected()));

    if (myProguardCheckBox.isSelected()) {
      final List<String> proguardOsCfgPaths = myProGuardConfigFilesPanel.getOsPaths();

      if (proguardOsCfgPaths.isEmpty()) {
        throw new CommitStepException(AndroidBundle.message("android.extract.package.specify.proguard.cfg.path.error"));
      }
      final String proguardPathsStr = mergeProguardCfgPathsToOneString(proguardOsCfgPaths);
      properties.setValue(PROGUARD_CFG_PATHS_PROPERTY, proguardPathsStr);

      for (String path : proguardOsCfgPaths) {
        if (!new File(path).isFile()) {
          throw new CommitStepException(AndroidBundle.message("android.cannot.find.file.error", path));
        }
      }
      compileScope.putUserData(AndroidCompileUtil.PROGUARD_CFG_PATHS_KEY, proguardPathsStr);
    }
    myWizard.setCompileScope(compileScope);
    myWizard.setApkPath(apkPath);
  }

  @Override
  protected void commitForNext() throws CommitStepException {
  }

  private void createUIComponents() {
    myProGuardConfigFilesPanel = new ProGuardConfigFilesPanel() {
      @Nullable
      @Override
      protected AndroidFacet getFacet() {
        return myWizard.getFacet();
      }
    };
  }

  @VisibleForTesting
  String getInitialPath(PropertiesComponent properties, @NotNull Module module) {
    String lastApkFolderPath = properties.getValue(getApkPathPropertyName(module.getName()));
    if (!isNullOrEmpty(lastApkFolderPath)) {
      return lastApkFolderPath;
    }
    String contentRootPath = getContentRootPath(module);
    if (contentRootPath != null) {
      return contentRootPath + File.separator + module.getName() + ".apk";
    }
    return null;
  }
}
