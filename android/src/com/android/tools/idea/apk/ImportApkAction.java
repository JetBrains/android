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
import com.intellij.icons.AllIcons;
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

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getManager;

public class ImportApkAction extends DumbAwareAction {
  @NonNls private static final String LAST_IMPORTED_LOCATION = "last.apk.imported.location";

  public ImportApkAction() {
    super("Profile or debug APK", null, AllIcons.Css.Import);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = getManager(ApkDebugging.SYSTEM_ID);
    assert manager != null;
    FileChooserDialog chooser = new FileChooserDialogImpl(manager.getExternalProjectDescriptor(), (Project)null);
    VirtualFile toSelect = null;
    String lastLocation = PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION);
    if (lastLocation != null) {
      toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation);
    }
    VirtualFile[] files = chooser.choose(null, toSelect);
    if (files.length == 0) {
      // User canceled.
      return;
    }
    VirtualFile file = files[0];
    PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, file.getPath());
    CustomProjectTypeImporter.getMain().importFileAsProject(file);
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = ApkDebugging.isEnabled();
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
