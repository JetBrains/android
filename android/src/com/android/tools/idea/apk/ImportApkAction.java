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
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ImportApkAction extends DumbAwareAction {
  @NonNls private static final String LAST_IMPORTED_LOCATION = "last.apk.imported.location";

  public ImportApkAction() {
    super("Profile or debug APK", null, AllIcons.Css.Import);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(createFileChooserDescriptor(), null, null);
    VirtualFile toSelect = null;
    String lastLocation = PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION);
    if (lastLocation != null) {
      toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation);
    }
    VirtualFile[] files = chooser.choose(null, toSelect);
    if (files.length == 0) {
      return;
    }
    VirtualFile file = files[0];
    PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, file.getPath());
    CustomProjectTypeImporter.getMain().importFileAsProject(file);
  }

  @NotNull
  private static FileChooserDescriptor createFileChooserDescriptor() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true /* choose files */, false /* do not choose folders */,
                                                                 false /* do not choose jars */, false /* do not choose jars as files */,
                                                                 false /* do not choose jar contents */,
                                                                 false /* do not choose multiple */) {
      FileChooserDescriptor myDelegate = new OpenProjectFileChooserDescriptor(true);
      @Override
      public Icon getIcon(VirtualFile file) {
        Icon icon = myDelegate.getIcon(file);
        return icon == null ? super.getIcon(file) : icon;
      }
    };
    descriptor.setHideIgnored(false);
    descriptor.setTitle("Select APK File");
    descriptor.setDescription("Select the APK file to profile or debug");
    descriptor.withFileFilter(file -> "apk".equals(file.getExtension()));
    return descriptor;
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = ApkDebugging.isEnabled();
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
