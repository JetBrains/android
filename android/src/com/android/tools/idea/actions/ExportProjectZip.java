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
package com.android.tools.idea.actions;

import com.android.SdkConstants;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.util.io.Compressor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exports an entire project into a zip file containing everything that is needed to run the project.
 */
public class ExportProjectZip extends AnAction implements DumbAware {

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null && !project.isDefault());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    FileSaverDialog saver = FileChooserFactory.getInstance()
        .createSaveFileDialog(new FileSaverDescriptor("Save Project As Zip", "Save to", SdkConstants.EXT_ZIP), project);

    VirtualFileWrapper target = saver.save(project.getName() + "." + SdkConstants.EXT_ZIP);
    if (target != null) {
      Task.Backgroundable task = new Task.Backgroundable(project, "Saving Project Zip") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          save(target.getFile(), project, indicator);
        }
      };
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }
  }

  @VisibleForTesting
  static void save(@NotNull File zipFile, @NotNull Project project, @Nullable ProgressIndicator indicator) {
    Set<File> allRoots = new HashSet<>();
    Set<File> excludes = new HashSet<>();
    excludes.add(zipFile);

    assert project.getBasePath() != null;
    File basePath = new File(FileUtil.toSystemDependentName(project.getBasePath()));
    allRoots.add(basePath);

    excludes.add(new File(basePath, SdkConstants.FN_LOCAL_PROPERTIES));

    for (ExportProjectZipExcludesContributor contributor : ExportProjectZipExcludesContributor.EP_NAME.getExtensionList()) {
      if (contributor.isApplicable(project)) {
        excludes.addAll(contributor.excludes(project));
      }
    }

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModuleRootManager roots = ModuleRootManager.getInstance(module);

      VirtualFile[] contentRoots = roots.getContentRoots();
      for (VirtualFile root : contentRoots) {
        allRoots.add(VfsUtilCore.virtualToIoFile(root));
      }

      VirtualFile[] exclude = roots.getExcludeRoots();
      for (VirtualFile root : exclude) {
        excludes.add(VfsUtilCore.virtualToIoFile(root));
      }
    }

    File commonRoot = null;
    for (File root : allRoots) {
      commonRoot = commonRoot == null ? root : FileUtil.findAncestor(commonRoot, root);
      if (commonRoot == null) {
        throw new IllegalArgumentException("no common root found");
      }
    }
    assert commonRoot != null;

    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    BiPredicate<String, Path> filter = (entryName, file) -> {
      if (fileTypeManager.isFileIgnored(file.getFileName().toString()) || excludes.stream().anyMatch(root -> file.startsWith(root.toPath()))) {
        return false;
      }

      if (!Files.exists(file)) {
        Logger.getInstance(ExportProjectZip.class).info("Skipping broken symlink: " + file);
        return false;
      }

      // if it's a folder and an ancestor of any of the roots we must allow it (to allow its content) or if a root is an ancestor
      boolean isDir = Files.isDirectory(file);
      if (allRoots.stream().noneMatch(root -> isDir && root.toPath().startsWith(file) || file.startsWith(root.toPath()))) {
        return false;
      }

      if (indicator != null) {
        indicator.setText(entryName);
      }

      return true;
    };

    try (Compressor zip = new Compressor.Zip(zipFile)) {
      zip.filter(filter);

      File[] children = commonRoot.listFiles();
      if (children != null) {
        for (File child : children) {
          String childRelativePath = (FileUtil.filesEqual(commonRoot, basePath) ? commonRoot.getName() + '/' : "") + child.getName();
          if (child.isDirectory()) {
            zip.addDirectory(childRelativePath, child);
          }
          else {
            zip.addFile(childRelativePath, child);
          }
        }
      }
    }
    catch (Exception ex) {
      Logger.getInstance(ExportProjectZip.class).info("error making zip", ex);
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project, "Error: " + ex, "Error!"));
    }
  }
}
