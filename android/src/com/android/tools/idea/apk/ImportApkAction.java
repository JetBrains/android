/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.apk;

import com.android.tools.idea.apk.debugging.ApkDebugging;
import com.android.tools.idea.project.CustomProjectTypeImporter;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getManager;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class ImportApkAction extends DumbAwareAction {
  @VisibleForTesting
  @NonNls static final String LAST_IMPORTED_LOCATION = "last.apk.imported.location";

  @NotNull private final PropertiesComponent myPropertiesComponent;
  @NotNull private final CustomProjectTypeImporter.MainImporter myProjectTypeImporter;
  @NotNull private final RecentProjectsManager myRecentProjectsManager;
  @NotNull private final FileChooserDialogFactory myFileChooserDialogFactory;
  @Nullable private final ExternalSystemManager<?, ?, ?, ?, ?> myExternalSystemManager;

  public ImportApkAction() {
    this(PropertiesComponent.getInstance(), CustomProjectTypeImporter.getMain(), new FileChooserDialogFactory(),
         RecentProjectsManager.getInstance(), getManager(ApkDebugging.SYSTEM_ID));
  }

  @VisibleForTesting
  ImportApkAction(@NotNull PropertiesComponent propertiesComponent,
                  @NotNull CustomProjectTypeImporter.MainImporter projectTypeImporter,
                  @NotNull FileChooserDialogFactory fileChooserDialogFactory,
                  @NotNull RecentProjectsManager recentProjectsManager,
                  @Nullable ExternalSystemManager<?, ?, ?, ?, ?> externalSystemManager) {
    super("Profile or debug APK", null, AllIcons.Css.Import);
    myPropertiesComponent = propertiesComponent;
    myProjectTypeImporter = projectTypeImporter;
    myRecentProjectsManager = recentProjectsManager;
    myFileChooserDialogFactory = fileChooserDialogFactory;
    myExternalSystemManager = externalSystemManager;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (myExternalSystemManager == null) {
      return;
    }
    FileChooserDialog chooser = myFileChooserDialogFactory.create(myExternalSystemManager);
    VirtualFile toSelect = null;
    String lastLocation = myPropertiesComponent.getValue(LAST_IMPORTED_LOCATION);
    if (lastLocation != null) {
      toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation);
    }
    VirtualFile[] files = chooser.choose(null, toSelect);
    if (files.length == 0) {
      // User canceled.
      return;
    }
    VirtualFile file = files[0];
    myPropertiesComponent.setValue(LAST_IMPORTED_LOCATION, toSystemDependentName(file.getPath()));
    String lastProjectCreation = myRecentProjectsManager.getLastProjectCreationLocation();

    myProjectTypeImporter.importFileAsProject(file);

    // Importing a project changes the project creation location. Set the original value back.
    myRecentProjectsManager.setLastProjectCreationLocation(lastProjectCreation);
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = myExternalSystemManager != null && ApkDebugging.isEnabled();
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @VisibleForTesting
  static class FileChooserDialogFactory {
    @NotNull
    FileChooserDialog create(@NotNull ExternalSystemManager<?, ?, ?, ?, ?> externalSystemManager) {
      return new FileChooserDialogImpl(externalSystemManager.getExternalProjectDescriptor(), (Project)null);
    }
  }
}
