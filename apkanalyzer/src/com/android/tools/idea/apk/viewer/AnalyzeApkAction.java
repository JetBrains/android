/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.util.CommonAndroidUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class AnalyzeApkAction extends DumbAwareAction {
  private static final String LAST_APK_PATH = "AnalyzeApkAction.lastApkPath";

  public AnalyzeApkAction() {
    super("Analyze APK...");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      Logger.getInstance(AnalyzeApkAction.class).warn("Unable to obtain project from event");
      return;
    }

    e.getPresentation().setEnabledAndVisible(
      IdeInfo.getInstance().isAndroidStudio() || CommonAndroidUtil.getInstance().isAndroidProject(project));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      Logger.getInstance(AnalyzeApkAction.class).warn("Unable to obtain project from event");
      return;
    }

    // find the apk to open
    VirtualFile vf = promptUserForApk(project);
    if (vf == null) {
      return;
    }

    OpenFileDescriptor fd = new OpenFileDescriptor(project, vf);
    FileEditorManager.getInstance(project).openEditor(fd, true);
  }

  @Nullable
  private static VirtualFile promptUserForApk(Project project) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
      .withDescription("Select APK to analyze")
      .withFileFilter(file -> ApkFileSystem.EXTENSIONS.contains(file.getExtension()));

    VirtualFile apk = FileChooser.chooseFile(descriptor, project, getLastSelectedApk(project));
    if (apk != null) {
      saveLastSelectedApk(project, apk);
    }
    return apk;
  }

  @Nullable
  private static VirtualFile getLastSelectedApk(Project project) {
    String lastApkPath = PropertiesComponent.getInstance(project).getValue(LAST_APK_PATH);
    if (lastApkPath != null) {
      File f = new File(lastApkPath);
      if (f.exists()) {
        return VfsUtil.findFileByIoFile(f, true);
      }
    }

    return ProjectSystemUtil.getProjectSystem(project).getDefaultApkFile();
  }

  private static void saveLastSelectedApk(Project project, VirtualFile apk) {
    PropertiesComponent.getInstance(project).setValue(LAST_APK_PATH, apk.getPath());
  }
}
